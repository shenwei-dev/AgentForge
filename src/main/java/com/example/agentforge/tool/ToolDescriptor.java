package com.example.agentforge.tool;

import java.util.Map;
import java.util.Set;

 
public record ToolDescriptor(
        String name,
        String description,
        String category,
        Map<String, Object> jsonSchema,
        Set<String> requiredPermissions,
        long timeoutMs) {
}
