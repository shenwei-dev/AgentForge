package com.example.agentforge.tool;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

 
@Component
public class RegistryToolExecutor implements ToolExecutor {

    private final ToolRegistry registry;

    public RegistryToolExecutor(ToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ToolResult execute(String toolName, Map<String, Object> input) {
        return registry.execute(toolName, input);
    }

    @Override
    public Set<String> availableTools() {
        return registry.names();
    }
}
