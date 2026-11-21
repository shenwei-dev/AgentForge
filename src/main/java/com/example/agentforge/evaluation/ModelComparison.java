package com.example.agentforge.evaluation;

 
public record ModelComparison(
        String modelName,
        int runs,
        double successRate,
        double averageLatencyMs,
        long totalTokens) {
}
