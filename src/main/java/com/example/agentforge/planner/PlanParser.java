package com.example.agentforge.planner;

import com.example.agentforge.planner.dto.PlanTask;
import com.example.agentforge.planner.dto.TaskPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

 

@Component
public class PlanParser {

    private final ObjectMapper mapper;

    public PlanParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public TaskPlan parse(String raw) {
        String json = sanitize(raw);
        if (json.isEmpty()) {
            throw new PlanValidationException("模型输出为空或未包含 JSON 对象", raw);
        }
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new PlanValidationException("非法 JSON: " + e.getOriginalMessage(), raw);
        }
        if (root == null || !root.isObject()) {
            throw new PlanValidationException("计划必须是 JSON 对象", raw);
        }
        JsonNode tasksNode = root.path("tasks");
        if (!tasksNode.isArray()) {
            throw new PlanValidationException("计划必须包含 tasks 数组", raw);
        }

        String goal = root.path("goal").asText("");
        List<PlanTask> tasks = new ArrayList<>();
        for (JsonNode node : tasksNode) {
            String role = node.path("agent_role").asText("");
            List<String> dependsOn = new ArrayList<>();
            JsonNode dependsNode = node.path("depends_on");
            if (dependsNode.isArray()) {
                for (JsonNode dependency : dependsNode) {
                    dependsOn.add(dependency.asText(""));
                }
            }
            tasks.add(new PlanTask(
                    node.path("id").asText(""),
                    node.path("title").asText(""),
                    node.path("description").asText(""),
                    dependsOn,
                    role.isBlank() ? null : role));
        }
        return new TaskPlan(goal, tasks);
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
