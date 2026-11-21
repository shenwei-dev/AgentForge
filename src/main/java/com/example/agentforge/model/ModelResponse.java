package com.example.agentforge.model;

 
public record ModelResponse(
        String content,
        String finishReason,
        TokenUsage usage,
        long latencyMs,
        String provider,
        String model) {
}
