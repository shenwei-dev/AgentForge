package com.example.agentforge.tool;

import java.util.Map;

 
public record ToolCallRecord(
        String id,
        String runId,
        String agentId,
        String toolName,
        Map<String, Object> input,
        String output,
        String status,
        String errorMessage,
        long durationMs) {
}
