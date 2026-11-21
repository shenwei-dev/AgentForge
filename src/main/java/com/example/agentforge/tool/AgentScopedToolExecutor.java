package com.example.agentforge.tool;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

 

public final class AgentScopedToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final Set<String> allowedTools;

    public AgentScopedToolExecutor(ToolExecutor delegate, Set<String> allowedTools) {
        this.delegate = delegate;
        this.allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
    }

    @Override
    public Set<String> availableTools() {
        return delegate.availableTools().stream()
                .filter(allowedTools::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public ToolResult execute(String toolName, Map<String, Object> input) {
        if (!allowedTools.contains(toolName)) {
            return ToolResult.denied(toolName, "Agent 无权使用工具: " + toolName);
        }
        return delegate.execute(toolName, input);
    }
}
