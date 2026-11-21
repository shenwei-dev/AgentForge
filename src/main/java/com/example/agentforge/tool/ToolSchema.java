package com.example.agentforge.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

 

public record ToolSchema(Map<String, ParamSpec> properties, List<String> required) {

    public record ParamSpec(String type, String description, List<Object> allowedValues) {
        public ParamSpec {
            allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        }

        public static ParamSpec of(String type, String description) {
            return new ParamSpec(type, description, List.of());
        }
    }

    public ToolSchema {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        required = required == null ? List.of() : List.copyOf(required);
    }

     
    public List<String> validate(Map<String, Object> input) {
        Map<String, Object> actual = input == null ? Map.of() : input;
        List<String> errors = new ArrayList<>();
        for (String name : required) {
            if (!actual.containsKey(name) || actual.get(name) == null) {
                errors.add("缺少必填参数: " + name);
            }
        }
        for (Map.Entry<String, ParamSpec> entry : properties.entrySet()) {
            Object value = actual.get(entry.getKey());
            if (value == null) {
                continue;
            }
            ParamSpec spec = entry.getValue();
            if (!typeMatches(spec.type(), value)) {
                errors.add("参数 " + entry.getKey() + " 类型应为 " + spec.type());
            }
            if (!spec.allowedValues().isEmpty() && !spec.allowedValues().contains(value)) {
                errors.add("参数 " + entry.getKey() + " 取值不在允许范围: " + spec.allowedValues());
            }
        }
        for (String key : actual.keySet()) {
            if (!properties.containsKey(key)) {
                errors.add("未声明的参数: " + key);
            }
        }
        return errors;
    }

    public Map<String, Object> toJsonSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        properties.forEach((name, spec) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", spec.type());
            item.put("description", spec.description());
            if (!spec.allowedValues().isEmpty()) {
                item.put("enum", spec.allowedValues());
            }
            props.put(name, item);
        });
        schema.put("properties", props);
        schema.put("required", required);
        return schema;
    }

    private boolean typeMatches(String type, Object value) {
        return switch (type) {
            case "string" -> value instanceof String;
            case "number", "integer" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            default -> true;
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, ParamSpec> properties = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();

        public Builder requiredString(String name, String description) {
            return add(name, "string", description, true, List.of());
        }

        public Builder optionalString(String name, String description) {
            return add(name, "string", description, false, List.of());
        }

        public Builder requiredNumber(String name, String description) {
            return add(name, "number", description, true, List.of());
        }

        public Builder optionalNumber(String name, String description) {
            return add(name, "number", description, false, List.of());
        }

        public Builder optionalBoolean(String name, String description) {
            return add(name, "boolean", description, false, List.of());
        }

        public Builder requiredBoolean(String name, String description) {
            return add(name, "boolean", description, true, List.of());
        }

        public Builder enumString(String name, String description, boolean required, List<Object> values) {
            return add(name, "string", description, required, values);
        }

        private Builder add(String name, String type, String description,
                            boolean required, List<Object> values) {
            properties.put(name, new ParamSpec(type, description, values));
            if (required) {
                this.required.add(name);
            }
            return this;
        }

        public ToolSchema build() {
            return new ToolSchema(properties, required);
        }
    }
}
