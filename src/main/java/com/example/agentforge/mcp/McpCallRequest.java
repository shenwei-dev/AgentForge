package com.example.agentforge.mcp;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

 
public record McpCallRequest(
        @NotBlank(message = "工具名不能为空") String tool,
        Map<String, Object> arguments) {
}
