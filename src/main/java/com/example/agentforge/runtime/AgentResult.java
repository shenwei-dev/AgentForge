package com.example.agentforge.runtime;

 
public record AgentResult(
        String runId,
        AgentState state,
        TerminationReason terminationReason,
        String finalAnswer,
        int rounds,
        long latencyMs,
        long totalTokens,
        String errorMessage) {

    public boolean isSuccess() {
        return state == AgentState.SUCCEEDED;
    }
}
