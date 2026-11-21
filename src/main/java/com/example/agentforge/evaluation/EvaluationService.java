package com.example.agentforge.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.agentforge.chat.entity.ChatMessageEntity;
import com.example.agentforge.chat.mapper.ChatMessageMapper;
import com.example.agentforge.common.BusinessException;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.config.EvaluationProperties;
import com.example.agentforge.tool.ToolCallEntity;
import com.example.agentforge.tool.mapper.ToolCallMapper;
import com.example.agentforge.trace.ExecutionEventEntity;
import com.example.agentforge.trace.mapper.ExecutionEventMapper;
import com.example.agentforge.workflow.entity.WorkflowRunEntity;
import com.example.agentforge.workflow.mapper.WorkflowRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

 

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    private static final int MAX_SCORE = 100;
    private static final int MAX_ROWS = 2000;
    private static final String SUCCEEDED = "SUCCEEDED";

    private final ChatMessageMapper chatMessageMapper;
    private final ToolCallMapper toolCallMapper;
    private final ExecutionEventMapper eventMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final EvaluationProperties properties;

    public EvaluationService(ChatMessageMapper chatMessageMapper,
                             ToolCallMapper toolCallMapper,
                             ExecutionEventMapper eventMapper,
                             WorkflowRunMapper workflowRunMapper,
                             EvaluationProperties properties) {
        this.chatMessageMapper = chatMessageMapper;
        this.toolCallMapper = toolCallMapper;
        this.eventMapper = eventMapper;
        this.workflowRunMapper = workflowRunMapper;
        this.properties = properties;
    }

    public List<EvaluationTask> tasks() {
        return List.copyOf(properties.getTasks());
    }

     
    public RunMetrics metrics(String runId) {
        List<ChatMessageEntity> messages = messagesOf(runId);
        List<ToolCallEntity> tools = toolsOf(runId);
        List<ExecutionEventEntity> events = eventsOf(runId);

        String modelName = messages.stream()
                .map(ChatMessageEntity::getModelName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst().orElse("unknown");
        int modelCalls = (int) messages.stream()
                .filter(m -> "ASSISTANT".equals(m.getRole()) && m.getModelName() != null)
                .count();
        long promptTokens = messages.stream().mapToLong(this::promptTokens).sum();
        long completionTokens = messages.stream().mapToLong(this::completionTokens).sum();
        long modelLatency = messages.stream().mapToLong(this::latency).sum();
        int toolSuccess = (int) tools.stream()
                .filter(t -> SUCCEEDED.equals(t.getStatus())).count();
        int rounds = (int) events.stream()
                .filter(e -> ExecutionEvent.ROUND_STARTED.equals(e.getEventType())).count();
        int errors = (int) events.stream()
                .filter(this::isFailureEvent).count();
        boolean hasAnswer = messages.stream().anyMatch(m ->
                "ASSISTANT".equals(m.getRole())
                        && m.getContent() != null && !m.getContent().isBlank()
                        && !"SUMMARY".equals(m.getContentType()));

        return new RunMetrics(runId, modelName, modelCalls, promptTokens, completionTokens,
                promptTokens + completionTokens, tools.size(), toolSuccess, rounds,
                events.size(), errors, modelLatency, wallClockMs(events), hasAnswer,
                resolveStatus(runId, events), failureReasons(events, tools));
    }

     
    public EvaluationResult evaluate(String runId, String taskId) {
        EvaluationTask task = properties.getTasks().stream()
                .filter(t -> taskId.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "未配置的评测任务: " + taskId));

        RunMetrics metrics = metrics(runId);
        List<ToolCallEntity> tools = toolsOf(runId);
        String answer = messagesOf(runId).stream()
                .filter(m -> "ASSISTANT".equals(m.getRole()))
                .map(ChatMessageEntity::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (metrics.hasFinalAnswer()) {
            score += 35;
        } else {
            reasons.add("缺少最终答案");
        }
        if (metrics.errorCount() == 0 && !"FAILED".equals(metrics.status())) {
            score += 15;
        } else {
            reasons.add("存在失败事件");
        }
        if (metrics.toolCallCount() == 0
                || metrics.toolSuccessCount() == metrics.toolCallCount()) {
            score += 10;
        } else {
            reasons.add("存在失败的工具调用");
        }
        if (task.getExpectedTool() == null || task.getExpectedTool().isBlank()) {
            score += 10;
        } else {
            boolean used = tools.stream().anyMatch(t ->
                    task.getExpectedTool().equals(t.getToolName())
                            && SUCCEEDED.equals(t.getStatus()));
            if (used) {
                score += 10;
            } else {
                reasons.add("未成功调用期望工具: " + task.getExpectedTool());
            }
        }
        if (metrics.agentRounds() <= properties.getMaxRoundsThreshold()) {
            score += 10;
        } else {
            reasons.add("Agent 轮数超出预算");
        }
        if (metrics.modelLatencyMs() <= properties.getLatencyThresholdMs()) {
            score += 10;
        } else {
            reasons.add("模型耗时超出阈值");
        }
        List<String> keywords = task.getExpectedKeywords();
        if (keywords == null || keywords.isEmpty()) {
            score += 10;
        } else {
            long hit = keywords.stream().filter(answer::contains).count();
            score += (int) Math.round(10.0 * hit / keywords.size());
            if (hit < keywords.size()) {
                reasons.add("期望关键词未完全命中 (" + hit + "/" + keywords.size() + ")");
            }
        }

        score = Math.min(MAX_SCORE, score);
        boolean passed = score >= properties.getPassScore();
        if (passed) {
            reasons.add(0, "评测通过");
        }
        return new EvaluationResult(runId, task.getId(), task.getName(), score,
                MAX_SCORE, passed, List.copyOf(reasons), metrics);
    }

     
    public EvaluationSummary summary() {
        List<ExecutionEventEntity> events = allEvents();
        Map<String, Set<String>> typesByRun = typesByRun(events);
        List<ChatMessageEntity> messages = allMessages();
        List<ToolCallEntity> tools = allTools();

        int totalRuns = typesByRun.size();
        int succeededRuns = (int) typesByRun.values().stream()
                .filter(this::isSuccessfulRun).count();
        double averageLatency = messages.stream()
                .filter(m -> m.getLatencyMs() != null)
                .mapToLong(this::latency).average().orElse(0);
        long totalTokens = messages.stream()
                .mapToLong(m -> promptTokens(m) + completionTokens(m)).sum();
        int toolSuccess = (int) tools.stream()
                .filter(t -> SUCCEEDED.equals(t.getStatus())).count();
        Map<String, Long> failures = events.stream()
                .filter(this::isFailureEvent)
                .collect(Collectors.groupingBy(
                        e -> e.getEventType() == null ? "UNKNOWN" : e.getEventType(),
                        Collectors.counting()));

        return new EvaluationSummary(totalRuns, succeededRuns,
                round(rate(succeededRuns, totalRuns)), round(averageLatency), totalTokens,
                tools.size(), round(rate(toolSuccess, tools.size())), failures);
    }

     
    public List<ModelComparison> compareModels() {
        Map<String, Set<String>> typesByRun = typesByRun(allEvents());
        Map<String, List<ChatMessageEntity>> grouped = allMessages().stream()
                .filter(m -> m.getModelName() != null && !m.getModelName().isBlank()
                        && m.getRunId() != null)
                .collect(Collectors.groupingBy(ChatMessageEntity::getModelName,
                        LinkedHashMap::new, Collectors.toList()));

        List<ModelComparison> comparisons = new ArrayList<>();
        grouped.forEach((model, messages) -> {
            Set<String> runs = messages.stream()
                    .map(ChatMessageEntity::getRunId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            int ok = (int) runs.stream()
                    .filter(run -> isSuccessfulRun(typesByRun.getOrDefault(run, Set.of())))
                    .count();
            double averageLatency = messages.stream()
                    .filter(m -> m.getLatencyMs() != null)
                    .mapToLong(this::latency).average().orElse(0);
            long tokens = messages.stream()
                    .mapToLong(m -> promptTokens(m) + completionTokens(m)).sum();
            comparisons.add(new ModelComparison(model, runs.size(),
                    round(rate(ok, runs.size())), round(averageLatency), tokens));
        });
        comparisons.sort(Comparator.comparingDouble(ModelComparison::successRate).reversed());
        return comparisons;
    }

    private List<ChatMessageEntity> messagesOf(String runId) {
        try {
            return chatMessageMapper.selectList(new QueryWrapper<ChatMessageEntity>()
                    .eq("run_id", runId).orderByAsc("created_at"));
        } catch (Exception e) {
            log.warn("评测读取 chat_message 失败 runId={}", runId, e);
            return List.of();
        }
    }

    private List<ToolCallEntity> toolsOf(String runId) {
        try {
            return toolCallMapper.selectList(new QueryWrapper<ToolCallEntity>()
                    .eq("run_id", runId));
        } catch (Exception e) {
            log.warn("评测读取 tool_call 失败 runId={}", runId, e);
            return List.of();
        }
    }

    private List<ExecutionEventEntity> eventsOf(String runId) {
        try {
            return eventMapper.selectList(new QueryWrapper<ExecutionEventEntity>()
                    .eq("run_id", runId).orderByAsc("id"));
        } catch (Exception e) {
            log.warn("评测读取 execution_event 失败 runId={}", runId, e);
            return List.of();
        }
    }

    private List<ChatMessageEntity> allMessages() {
        try {
            return chatMessageMapper.selectList(new QueryWrapper<ChatMessageEntity>()
                    .orderByAsc("created_at").last("LIMIT " + MAX_ROWS));
        } catch (Exception e) {
            log.warn("评测读取全量 chat_message 失败", e);
            return List.of();
        }
    }

    private List<ToolCallEntity> allTools() {
        try {
            return toolCallMapper.selectList(new QueryWrapper<ToolCallEntity>()
                    .last("LIMIT " + MAX_ROWS));
        } catch (Exception e) {
            log.warn("评测读取全量 tool_call 失败", e);
            return List.of();
        }
    }

    private List<ExecutionEventEntity> allEvents() {
        try {
            return eventMapper.selectList(new QueryWrapper<ExecutionEventEntity>()
                    .orderByAsc("id").last("LIMIT " + MAX_ROWS));
        } catch (Exception e) {
            log.warn("评测读取全量 execution_event 失败", e);
            return List.of();
        }
    }

    private Map<String, Set<String>> typesByRun(List<ExecutionEventEntity> events) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (ExecutionEventEntity event : events) {
            if (event.getRunId() == null) {
                continue;
            }
            result.computeIfAbsent(event.getRunId(), key -> new HashSet<>())
                    .add(event.getEventType());
        }
        return result;
    }

    private boolean isSuccessfulRun(Set<String> types) {
        return types.contains(ExecutionEvent.RUN_COMPLETED)
                && !types.contains(ExecutionEvent.RUN_FAILED);
    }

    private boolean isFailureEvent(ExecutionEventEntity event) {
        return "ERROR".equals(event.getLevel())
                || ExecutionEvent.RUN_FAILED.equals(event.getEventType());
    }

    private String resolveStatus(String runId, List<ExecutionEventEntity> events) {
        boolean failed = events.stream().anyMatch(e ->
                ExecutionEvent.RUN_FAILED.equals(e.getEventType()));
        if (failed) {
            return "FAILED";
        }
        boolean completed = events.stream().anyMatch(e ->
                ExecutionEvent.RUN_COMPLETED.equals(e.getEventType()));
        if (completed) {
            return SUCCEEDED;
        }
        try {
            WorkflowRunEntity workflowRun = workflowRunMapper.selectById(runId);
            if (workflowRun != null && workflowRun.getStatus() != null) {
                return workflowRun.getStatus();
            }
        } catch (Exception e) {
            log.debug("评测读取 workflow_run 失败 runId={}", runId, e);
        }
        return "UNKNOWN";
    }

    private List<String> failureReasons(List<ExecutionEventEntity> events,
                                        List<ToolCallEntity> tools) {
        List<String> reasons = new ArrayList<>();
        events.stream().filter(this::isFailureEvent)
                .map(e -> e.getMessage() == null ? e.getEventType() : e.getMessage())
                .distinct().limit(10).forEach(reasons::add);
        tools.stream().filter(t -> !SUCCEEDED.equals(t.getStatus()))
                .map(t -> "工具失败: " + t.getToolName() + " - "
                        + (t.getErrorMessage() == null ? t.getStatus() : t.getErrorMessage()))
                .distinct().limit(10).forEach(reasons::add);
        return List.copyOf(reasons);
    }

    private long wallClockMs(List<ExecutionEventEntity> events) {
        List<java.time.LocalDateTime> times = events.stream()
                .map(ExecutionEventEntity::getCreatedAt)
                .filter(Objects::nonNull).sorted().toList();
        if (times.size() < 2) {
            return 0;
        }
        return times.get(times.size() - 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                - times.get(0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long promptTokens(ChatMessageEntity message) {
        return message.getPromptTokens() == null ? 0 : message.getPromptTokens();
    }

    private long completionTokens(ChatMessageEntity message) {
        return message.getCompletionTokens() == null ? 0 : message.getCompletionTokens();
    }

    private long latency(ChatMessageEntity message) {
        return message.getLatencyMs() == null ? 0 : message.getLatencyMs();
    }

    private double rate(int numerator, int denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
