package com.example.agentforge.mcp;

import com.example.agentforge.tool.ToolSchema;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

 
public final class McpSchemaConverter {

    private McpSchemaConverter() {
    }

    public static ToolSchema convert(JsonNode schema) {
        ToolSchema.Builder builder = ToolSchema.builder();
        if (schema == null || !schema.isObject()) {
            return builder.build();
        }
        Set<String> required = new LinkedHashSet<>();
        JsonNode requiredNode = schema.path("required");
        if (requiredNode.isArray()) {
            requiredNode.forEach(node -> required.add(node.asText()));
        }
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) {
            return builder.build();
        }
        properties.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            JsonNode definition = entry.getValue();
            String type = definition.path("type").asText("string");
            String description = definition.path("description").asText("");
            boolean isRequired = required.contains(name);

            List<Object> enumValues = new ArrayList<>();
            JsonNode enumNode = definition.path("enum");
            if (enumNode.isArray()) {
                enumNode.forEach(node -> enumValues.add(node.asText()));
            }
            if (!enumValues.isEmpty()) {
                builder.enumString(name, description, isRequired, enumValues);
                return;
            }
            switch (type) {
                case "number", "integer" -> {
                    if (isRequired) {
                        builder.requiredNumber(name, description);
                    } else {
                        builder.optionalNumber(name, description);
                    }
                }
                case "boolean" -> {
                    if (isRequired) {
                        builder.requiredBoolean(name, description);
                    } else {
                        builder.optionalBoolean(name, description);
                    }
                }
                default -> {
                    if (isRequired) {
                        builder.requiredString(name, description);
                    } else {
                        builder.optionalString(name, description);
                    }
                }
            }
        });
        return builder.build();
    }
}
