package com.example.agentforge.agent;

 
public record AgentStatus(String taskId, String role, String state, Long latencyMs) {

    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
}
