package com.example.agentforge.memory;

import com.example.agentforge.config.MemoryProperties;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.model.ModelRequest;
import com.example.agentforge.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

 

@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final String SUMMARY_PROMPT = """
            你是 AgentForge 的上下文压缩器。请把给定对话压缩为简洁的中文要点摘要：
            保留关键事实、数字、已达成的决策与未完成事项；不要臆测；
            不要输出 Markdown 标题、解释文字或多余内容。
            """;

    private final ModelAdapterRegistry registry;
    private final MemoryProperties properties;

    public SummaryService(ModelAdapterRegistry registry, MemoryProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    public String summarize(String previousSummary, List<ChatMessage> dropped) {
        if (dropped == null || dropped.isEmpty()) {
            return previousSummary;
        }
        StringBuilder prompt = new StringBuilder();
        if (previousSummary != null && !previousSummary.isBlank()) {
            prompt.append("已有摘要：\n").append(previousSummary).append("\n\n");
        }
        prompt.append("需要合并的旧消息：\n");
        for (ChatMessage message : dropped) {
            prompt.append("[").append(message.role()).append("] ")
                    .append(abbreviate(message.content(), 800)).append('\n');
        }
        prompt.append("请输出不超过 ").append(properties.getSummaryMaxChars())
                .append(" 字的合并摘要。");

        try {
            ModelResponse response = registry.current().chat(ModelRequest.of(List.of(
                    ChatMessage.system(SUMMARY_PROMPT),
                    ChatMessage.user(prompt.toString()))));
            String text = response.content();
            if (text == null || text.isBlank()) {
                return previousSummary;
            }
            return text.length() > properties.getSummaryMaxChars()
                    ? text.substring(0, properties.getSummaryMaxChars())
                    : text;
        } catch (Exception e) {
            log.warn("历史摘要生成失败，降级为直接丢弃旧消息", e);
            return previousSummary;
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
