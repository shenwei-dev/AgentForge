package com.example.agentforge.tool;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

 

public interface Tool {

    String name();

    String description();

    ToolCategory category();

    ToolSchema schema();

    Set<String> requiredPermissions();

    Duration timeout();

    String execute(Map<String, Object> input);

    default ToolDescriptor descriptor() {
        return new ToolDescriptor(name(), description(), category().name(),
                schema().toJsonSchema(), Set.copyOf(requiredPermissions()), timeout().toMillis());
    }
}
