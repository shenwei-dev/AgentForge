package com.example.agentforge.planner.dto;

import com.example.agentforge.agent.AgentStatus;

import java.util.List;

 
public record PlanExecutionResult(
        String runId,
        String goal,
        List<TaskOutcome> outcomes,
        String summary,
        int totalTasks,
        int succeededTasks,
        long latencyMs,
        List<AgentStatus> agentStatuses) {

     
    public PlanExecutionResult(String runId, String goal, List<TaskOutcome> outcomes,
                               String summary, int totalTasks, int succeededTasks, long latencyMs) {
        this(runId, goal, outcomes, summary, totalTasks, succeededTasks, latencyMs, List.of());
    }

    public record TaskOutcome(
            String taskId,
            String title,
            boolean success,
            String result,
            String errorMessage,
            long latencyMs) {
    }

    public boolean isSuccess() {
        return totalTasks > 0 && succeededTasks == totalTasks;
    }
}
