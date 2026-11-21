package com.example.agentforge.evaluation;

import java.util.List;

 

public record RunMetrics(
        String runId,
        String modelName,
        int modelCallCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        int toolCallCount,
        int toolSuccessCount,
        int agentRounds,
        int eventCount,
        int errorCount,
        long modelLatencyMs,
        long wallClockMs,
        boolean hasFinalAnswer,
        String status,
        List<String> failureReasons) {
}
