package com.example.agentforge.tool;

import java.util.Map;
import java.util.Set;

 

public interface ToolExecutor {

    ToolResult execute(String toolName, Map<String, Object> input);

    Set<String> availableTools();
}
