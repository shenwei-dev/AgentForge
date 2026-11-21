package com.example.agentforge.runtime;

import java.util.Map;

 
public record AgentDecision(
        String thought,
        Decision decision,
        String toolName,
        Map<String, Object> toolInput,
        String answer) {

    public enum Decision {
        TOOL,
        FINAL
    }

    public AgentDecision {
        toolInput = toolInput == null ? Map.of() : Map.copyOf(toolInput);
    }

    public static AgentDecision tool(String thought, String toolName, Map<String, Object> input) {
        return new AgentDecision(thought, Decision.TOOL, toolName, input, null);
    }

    public static AgentDecision finish(String thought, String answer) {
        return new AgentDecision(thought, Decision.FINAL, null, Map.of(), answer);
    }
}
