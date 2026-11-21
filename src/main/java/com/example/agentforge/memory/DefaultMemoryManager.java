package com.example.agentforge.memory;

import com.example.agentforge.chat.service.ConversationService;
import com.example.agentforge.config.MemoryProperties;
import com.example.agentforge.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

 

@Service
public class DefaultMemoryManager implements MemoryManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryManager.class);

    private final ConversationService conversationService;
    private final SummaryService summaryService;
    private final MemoryProperties properties;
    private final Map<String, List<ChatMessage>> workingMemory = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> taskResults = new ConcurrentHashMap<>();
    private final Map<String, MemoryStats> lastStats = new ConcurrentHashMap<>();

    public DefaultMemoryManager(ConversationService conversationService,
                                SummaryService summaryService,
                                MemoryProperties properties) {
        this.conversationService = conversationService;
        this.summaryService = summaryService;
        this.properties = properties;
    }

    @Override
    public List<ChatMessage> buildContext(MemoryScope scope, String systemPrompt,
                                          String currentInput) {
        String conversationId = scope.conversationId();
        List<ChatMessage> history = conversationService.loadAllHistory(conversationId);
        if (history.size() > properties.getMaxMessages()) {
            history = new ArrayList<>(history.subList(
                    history.size() - properties.getMaxMessages(), history.size()));
        }
        String summary = conversationService.findLatestSummary(conversationId);
        List<ChatMessage> working = List.copyOf(
                workingMemory.getOrDefault(scope.workingKey(), List.of()));
        String reusable = reusableText(conversationId);

        int used = length(systemPrompt) + length(summary) + length(reusable)
                + length(currentInput)
                + working.stream().mapToInt(m -> length(m.content())).sum();
        List<ChatMessage> retained = new ArrayList<>();
        List<ChatMessage> dropped = new ArrayList<>();
        boolean overflow = false;
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            int cost = length(message.content()) + 8;
            if (!overflow && used + cost <= properties.getCharBudget()) {
                retained.add(message);
                used += cost;
            } else {
                overflow = true;
                dropped.add(message);
            }
        }
        Collections.reverse(retained);
        Collections.reverse(dropped);

        boolean summaryPresent = summary != null && !summary.isBlank();
        if (!dropped.isEmpty() && properties.isSummaryEnabled()
                && dropped.size() >= properties.getSummaryThreshold()) {
            String newSummary = summaryService.summarize(summary, dropped);
            if (newSummary != null && !newSummary.isBlank() && !newSummary.equals(summary)) {
                conversationService.saveSummaryMessage(conversationId,
                        scope.runId() == null ? "memory" : scope.runId(), newSummary);
                summary = newSummary;
                summaryPresent = true;
                log.debug("上下文已压缩 conversationId={} dropped={}", conversationId, dropped.size());
            }
        }

        List<ChatMessage> context = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            context.add(ChatMessage.system(systemPrompt));
        }
        if (summaryPresent) {
            context.add(ChatMessage.system("以下是较早对话的要点摘要（供参考）：\n" + summary));
        }
        if (!reusable.isBlank()) {
            context.add(ChatMessage.system("可复用的历史任务结果：\n" + reusable));
        }
        context.addAll(working);
        context.addAll(retained);
        if (currentInput != null && !currentInput.isBlank()
                && !lastUserEquals(retained, currentInput)) {
            context.add(ChatMessage.user(currentInput));
        }

        int contextChars = context.stream().mapToInt(m -> length(m.content())).sum();
        MemoryStats stats = new MemoryStats(history.size(), retained.size(), dropped.size(),
                contextChars, summaryPresent, countResults(conversationId));
        lastStats.put(conversationId, stats);
        return context;
    }

    @Override
    public void appendWorkingMemory(MemoryScope scope, String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        List<ChatMessage> list = workingMemory.computeIfAbsent(
                scope.workingKey(), key -> new CopyOnWriteArrayList<>());
        list.add("assistant".equalsIgnoreCase(role)
                ? ChatMessage.assistant(content) : ChatMessage.user(content));
        while (list.size() > Math.max(1, properties.getWorkingMemoryMax())) {
            list.remove(0);
        }
    }

    @Override
    public void putTaskResult(String conversationId, String taskId, String result) {
        if (conversationId == null || taskId == null || result == null) {
            return;
        }
        taskResults.computeIfAbsent(conversationId,
                key -> Collections.synchronizedMap(new LinkedHashMap<>()))
                .put(taskId, result);
    }

    @Override
    public Map<String, String> taskResults(String conversationId) {
        Map<String, String> source = taskResults.get(conversationId);
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }

    @Override
    public void clear(MemoryScope scope) {
        workingMemory.remove(scope.workingKey());
    }

    @Override
    public MemoryStats stats(String conversationId) {
        return lastStats.getOrDefault(conversationId,
                new MemoryStats(0, 0, 0, 0, false, countResults(conversationId)));
    }

    @Override
    public MemoryStats compact(String conversationId) {
        buildContext(MemoryScope.conversation(conversationId), null, null);
        return stats(conversationId);
    }

    private String reusableText(String conversationId) {
        Map<String, String> results = taskResults(conversationId);
        if (results.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> entry : results.entrySet()) {
            if (count++ >= properties.getMaxReusableResults()) {
                break;
            }
            String text = entry.getValue();
            if (text.length() > properties.getReusableResultChars()) {
                text = text.substring(0, properties.getReusableResultChars()) + "…";
            }
            builder.append("- [").append(entry.getKey()).append("] ").append(text).append('\n');
        }
        return builder.toString();
    }

    private int countResults(String conversationId) {
        Map<String, String> results = taskResults.get(conversationId);
        return results == null ? 0 : results.size();
    }

    private boolean lastUserEquals(List<ChatMessage> retained, String currentInput) {
        if (retained.isEmpty()) {
            return false;
        }
        ChatMessage last = retained.get(retained.size() - 1);
        return "user".equals(last.role()) && currentInput.equals(last.content());
    }

    private int length(String text) {
        return text == null ? 0 : text.length();
    }
}
