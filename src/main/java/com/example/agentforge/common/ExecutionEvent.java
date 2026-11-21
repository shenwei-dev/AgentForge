package com.example.agentforge.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

 

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionEvent(
        String eventId,
        String event,
        String runId,
        String conversationId,
        String agentId,
        String nodeId,
        String toolCallId,
        String level,
        String message,
        Map<String, Object> payload,
        long timestamp) {

    public static final String RUN_STARTED = "run_started";
    public static final String PLAN_CREATED = "plan_created";
    public static final String AGENT_STARTED = "agent_started";
    public static final String AGENT_COMPLETED = "agent_completed";
    public static final String TOOL_CALL_STARTED = "tool_call_started";
    public static final String TOOL_CALL_COMPLETED = "tool_call_completed";
    public static final String NODE_STARTED = "node_started";
    public static final String NODE_COMPLETED = "node_completed";
    public static final String MESSAGE_DELTA = "message_delta";
    public static final String ROUND_STARTED = "round_started";
    public static final String PLAN_TASK_STARTED = "plan_task_started";
    public static final String PLAN_TASK_COMPLETED = "plan_task_completed";
    public static final String RUN_COMPLETED = "run_completed";
    public static final String RUN_FAILED = "run_failed";

    public static ExecutionEvent of(String eventType, String runId, String conversationId,
                                    String level, String message, Map<String, Object> payload) {
        return ofWith(eventType, runId, conversationId, null, null, level, message, payload);
    }

    public static ExecutionEvent runStarted(String runId, String conversationId,
                                            String provider, String model) {
        return of(RUN_STARTED, runId, conversationId, "INFO", "任务开始执行",
                payload("provider", provider, "model", model));
    }

    public static ExecutionEvent messageDelta(String runId, String conversationId, String delta) {
        return of(MESSAGE_DELTA, runId, conversationId, "INFO", null,
                payload("delta", delta));
    }

    public static ExecutionEvent runCompleted(String runId, String conversationId,
                                              int answerLength, long latencyMs,
                                              Integer totalTokens, String provider) {
        return of(RUN_COMPLETED, runId, conversationId, "INFO", "任务执行完成",
                payload("answerLength", answerLength, "latencyMs", latencyMs,
                        "totalTokens", totalTokens, "provider", provider));
    }

    public static ExecutionEvent runFailed(String runId, String conversationId, String error) {
        return of(RUN_FAILED, runId, conversationId, "ERROR", "任务执行失败",
                payload("error", error));
    }

    

    public static ExecutionEvent agentStarted(String runId, String conversationId,
                                              String agentId, String provider) {
        return ofWith(AGENT_STARTED, runId, conversationId, agentId, null,
                "INFO", "Agent 开始执行", payload("provider", provider));
    }

    public static ExecutionEvent agentCompleted(String runId, String conversationId) {
        return of(AGENT_COMPLETED, runId, conversationId, "INFO", "Agent 执行结束", Map.of());
    }

    public static ExecutionEvent roundStarted(String runId, String conversationId, int round) {
        return of(ROUND_STARTED, runId, conversationId, "INFO", "第 " + round + " 轮开始",
                payload("round", round));
    }

    public static ExecutionEvent toolCallStarted(String runId, String conversationId, String toolName) {
        return of(TOOL_CALL_STARTED, runId, conversationId, "INFO", "调用工具: " + toolName,
                payload("toolName", toolName));
    }

    public static ExecutionEvent toolCallCompleted(String runId, String conversationId,
                                                   String toolName, boolean success) {
        return of(TOOL_CALL_COMPLETED, runId, conversationId, success ? "INFO" : "WARN",
                "工具调用" + (success ? "完成" : "失败") + ": " + toolName,
                payload("toolName", toolName, "success", success));
    }

    

    public static ExecutionEvent planCreated(String runId, String conversationId,
                                             String goal, List<Map<String, Object>> tasks) {
        return of(PLAN_CREATED, runId, conversationId, "INFO", "任务计划已生成",
                payload("goal", goal, "taskCount", tasks.size(), "tasks", tasks));
    }

    public static ExecutionEvent planTaskStarted(String runId, String conversationId,
                                                 String taskId, String title, int index, int total) {
        return planTaskStarted(runId, conversationId, taskId, title, index, total, null);
    }

     
    public static ExecutionEvent planTaskStarted(String runId, String conversationId,
                                                 String taskId, String title, int index,
                                                 int total, String agentRole) {
        return of(PLAN_TASK_STARTED, runId, conversationId, "INFO",
                "开始执行任务 " + taskId + ": " + title,
                payload("taskId", taskId, "title", title, "index", index,
                        "total", total, "agentRole", agentRole));
    }

    public static ExecutionEvent planTaskCompleted(String runId, String conversationId,
                                                   String taskId, boolean success,
                                                   long latencyMs, String error) {
        return of(PLAN_TASK_COMPLETED, runId, conversationId, success ? "INFO" : "WARN",
                "任务 " + taskId + (success ? " 完成" : " 失败"),
                payload("taskId", taskId, "success", success, "latencyMs", latencyMs, "error", error));
    }

    

    public static ExecutionEvent nodeStarted(String runId, String conversationId,
                                             String nodeKey, int level) {
        return of(NODE_STARTED, runId, conversationId, "INFO",
                "节点开始执行: " + nodeKey,
                payload("nodeKey", nodeKey, "level", level));
    }

    public static ExecutionEvent nodeCompleted(String runId, String conversationId,
                                               String nodeKey, boolean success,
                                               long latencyMs, String state) {
        return of(NODE_COMPLETED, runId, conversationId, success ? "INFO" : "WARN",
                "节点" + (success ? "完成" : "未成功") + ": " + nodeKey,
                payload("nodeKey", nodeKey, "success", success,
                        "latencyMs", latencyMs, "state", state));
    }

     
    private static ExecutionEvent ofWith(String eventType, String runId, String conversationId,
                                         String agentId, String toolCallId, String level,
                                         String message, Map<String, Object> payload) {
        return new ExecutionEvent(UUID.randomUUID().toString(), eventType, runId, conversationId,
                agentId, null, toolCallId, level, message, payload, System.currentTimeMillis());
    }

     
    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                map.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return Map.copyOf(map);
    }
}
