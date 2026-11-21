package com.example.agentforge.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class ToolSchemaTest {

    private final ToolSchema schema = ToolSchema.builder()
            .requiredString("expression", "数学表达式")
            .optionalString("zone", "时区")
            .enumString("mode", "模式", false, List.of("fast", "slow"))
            .build();

    @Test
    void shouldAcceptValidInput() {
        assertTrue(schema.validate(Map.of("expression", "1+1")).isEmpty());
        assertTrue(schema.validate(Map.of("expression", "1+1", "mode", "fast")).isEmpty());
    }

    @Test
    void shouldRejectMissingRequiredParameter() {
        List<String> errors = schema.validate(Map.of());

        assertTrue(errors.stream().anyMatch(e -> e.contains("缺少必填参数: expression")));
    }

    @Test
    void shouldRejectWrongType() {
        List<String> errors = schema.validate(Map.of("expression", 123));

        assertTrue(errors.stream().anyMatch(e -> e.contains("类型应为 string")));
    }

    @Test
    void shouldRejectUnknownParameter() {
        List<String> errors = schema.validate(Map.of("expression", "1+1", "evil", "x"));

        assertTrue(errors.stream().anyMatch(e -> e.contains("未声明的参数: evil")));
    }

    @Test
    void shouldRejectValueOutsideEnum() {
        List<String> errors = schema.validate(Map.of("expression", "1+1", "mode", "medium"));

        assertTrue(errors.stream().anyMatch(e -> e.contains("取值不在允许范围")));
    }

    @Test
    void shouldGenerateJsonSchemaForModelDiscovery() {
        Map<String, Object> json = schema.toJsonSchema();

        assertEquals("object", json.get("type"));
        assertEquals(List.of("expression"), json.get("required"));
        Map<?, ?> properties = (Map<?, ?>) json.get("properties");
        assertTrue(properties.containsKey("expression"));
        assertTrue(properties.containsKey("zone"));
    }
}
