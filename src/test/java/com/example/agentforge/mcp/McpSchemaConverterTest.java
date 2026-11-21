package com.example.agentforge.mcp;

import com.example.agentforge.tool.ToolSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class McpSchemaConverterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldConvertRequiredOptionalAndEnumProperties() throws Exception {
        JsonNode schema = mapper.readTree("""
                {"type":"object","properties":{
                  "text":{"type":"string","description":"要处理的文本"},
                  "a":{"type":"number","description":"第一个数字"},
                  "flag":{"type":"boolean","description":"是否启用"},
                  "mode":{"type":"string","description":"模式","enum":["fast","slow"]}
                },"required":["text","a"]}
                """);

        ToolSchema converted = McpSchemaConverter.convert(schema);

        assertTrue(converted.validate(Map.of("text", "x", "a", 1)).isEmpty());
        assertTrue(converted.validate(Map.of()).stream()
                .anyMatch(e -> e.contains("缺少必填参数: text")));
        assertTrue(converted.validate(Map.of("text", "x", "a", "wrong")).stream()
                .anyMatch(e -> e.contains("类型应为 number")));
        assertTrue(converted.validate(Map.of("text", "x", "a", 1, "mode", "medium")).stream()
                .anyMatch(e -> e.contains("取值不在允许范围")));
        assertEquals(List.of("text", "a"), converted.toJsonSchema().get("required"));
    }

    @Test
    void shouldTolerateEmptyOrMissingSchema() {
        assertTrue(McpSchemaConverter.convert(null).validate(Map.of()).isEmpty());
        assertTrue(McpSchemaConverter.convert(mapper.createObjectNode())
                .validate(Map.of()).isEmpty());
    }
}
