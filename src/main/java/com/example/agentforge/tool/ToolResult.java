package com.example.agentforge.tool;

 

public record ToolResult(
        boolean success,
        String toolName,
        String content,
        String errorMessage,
        String status,
        long durationMs) {

    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String DENIED = "DENIED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String NOT_FOUND = "NOT_FOUND";

    public static ToolResult ok(String toolName, String content) {
        return ok(toolName, content, 0);
    }

    public static ToolResult ok(String toolName, String content, long durationMs) {
        return new ToolResult(true, toolName, content, null, SUCCEEDED, durationMs);
    }

    public static ToolResult failed(String toolName, String errorMessage) {
        return failed(toolName, errorMessage, 0);
    }

    public static ToolResult failed(String toolName, String errorMessage, long durationMs) {
        return new ToolResult(false, toolName, null, errorMessage, FAILED, durationMs);
    }

    public static ToolResult denied(String toolName, String errorMessage) {
        return new ToolResult(false, toolName, null, errorMessage, DENIED, 0);
    }

    public static ToolResult timeout(String toolName, String errorMessage) {
        return timeout(toolName, errorMessage, 0);
    }

    public static ToolResult timeout(String toolName, String errorMessage, long durationMs) {
        return new ToolResult(false, toolName, null, errorMessage, TIMEOUT, durationMs);
    }

    public static ToolResult notFound(String toolName) {
        return new ToolResult(false, toolName, null, "工具未注册: " + toolName, NOT_FOUND, 0);
    }
}
