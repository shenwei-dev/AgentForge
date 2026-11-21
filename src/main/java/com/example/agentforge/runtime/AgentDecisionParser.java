package com.example.agentforge.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

 

@Component
public class AgentDecisionParser {

    private final ObjectMapper mapper;

    public AgentDecisionParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AgentDecision parse(String raw) {
        String json = sanitize(raw);
        if (json.isEmpty()) {
            throw new DecisionParseException("模型输出为空或未包含 JSON 对象", raw);
        }

        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new DecisionParseException("非法 JSON: " + e.getOriginalMessage(), raw);
        }
        if (root == null || !root.isObject()) {
            throw new DecisionParseException("决策必须是 JSON 对象", raw);
        }

        String thought = root.path("thought").asText("");
        String decision = root.path("decision").asText("").trim().toLowerCase(Locale.ROOT);

        if ("final".equals(decision)) {
            String answer = root.path("answer").asText("");
            if (answer.isBlank()) {
                throw new DecisionParseException("decision=final 时 answer 不能为空", raw);
            }
            return AgentDecision.finish(thought, answer);
        }
        if ("tool".equals(decision)) {
            String toolName = root.path("tool_name").asText("");
            if (toolName.isBlank()) {
                throw new DecisionParseException("decision=tool 时 tool_name 不能为空", raw);
            }
            return AgentDecision.tool(thought, toolName, parseInput(root.path("tool_input")));
        }
        throw new DecisionParseException("decision 只能是 tool 或 final，实际值: " + decision, raw);
    }

    private Map<String, Object> parseInput(JsonNode node) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return input;
        }
        node.fields().forEachRemaining(entry -> {
            Object value = mapper.convertValue(entry.getValue(), Object.class);
            if (value != null) {
                input.put(entry.getKey(), value);
            }
        });
        return input;
    }

     
    private String sanitize(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            text = firstLineEnd >= 0 ? text.substring(firstLineEnd + 1) : "";
            int closingFence = text.lastIndexOf("```");
            if (closingFence >= 0) {
                text = text.substring(0, closingFence);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return text.substring(start, end + 1).trim();
    }
}
