package com.example.agentforge.runtime;

import com.example.agentforge.tool.ToolExecutor;
import com.example.agentforge.tool.ToolResult;

import java.util.Map;
import java.util.Set;

 
public final class TestTools {

    private TestTools() {
    }

    public static ToolExecutor echo() {
        return new ToolExecutor() {
            @Override
            public Set<String> availableTools() {
                return Set.of("echo");
            }

            @Override
            public ToolResult execute(String toolName, Map<String, Object> input) {
                if (!"echo".equals(toolName)) {
                    return ToolResult.failed(toolName, "工具未注册: " + toolName);
                }
                return ToolResult.ok(toolName, String.valueOf(input.get("text")));
            }
        };
    }
}
