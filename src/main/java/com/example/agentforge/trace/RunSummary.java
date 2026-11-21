package com.example.agentforge.trace;

 
public record RunSummary(
        String runId,
        String conversationId,
        long firstEventAt,
        long lastEventAt,
        int eventCount,
        String lastEventType) {
}
