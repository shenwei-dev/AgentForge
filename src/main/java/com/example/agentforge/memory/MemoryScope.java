package com.example.agentforge.memory;

 

public record MemoryScope(String conversationId, String agentId, String runId) {

    public static MemoryScope conversation(String conversationId) {
        return new MemoryScope(conversationId, null, null);
    }

    public static MemoryScope agent(String conversationId, String agentId) {
        return new MemoryScope(conversationId, agentId, null);
    }

    public String workingKey() {
        return conversationId + ":"
                + (agentId == null || agentId.isBlank() ? "shared" : agentId);
    }
}
