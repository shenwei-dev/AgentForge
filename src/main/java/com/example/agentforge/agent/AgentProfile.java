package com.example.agentforge.agent;

import java.util.Set;

 

public record AgentProfile(
        String id,
        String name,
        String role,
        String systemPrompt,
        Set<String> allowedTools,
        String modelProvider,
        String modelName,
        int maxRounds,
        long timeoutMs,
        boolean enabled) {

    public AgentProfile {
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
    }
}
