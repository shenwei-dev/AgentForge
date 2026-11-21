package com.example.agentforge.tool;

 

public final class ToolCallContext {

    public record Context(String runId, String agentId) {
    }

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private ToolCallContext() {
    }

    public static void set(String runId, String agentId) {
        CURRENT.set(new Context(runId, agentId));
    }

    public static Context current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
