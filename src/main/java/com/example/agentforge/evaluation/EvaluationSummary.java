package com.example.agentforge.evaluation;

import java.util.Map;

 
public record EvaluationSummary(
        int totalRuns,
        int succeededRuns,
        double successRate,
        double averageLatencyMs,
        long totalTokens,
        int totalToolCalls,
        double toolSuccessRate,
        Map<String, Long> failureReasons) {
}
