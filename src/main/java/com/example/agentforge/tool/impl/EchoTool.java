package com.example.agentforge.tool.impl;

import com.example.agentforge.tool.Tool;
import com.example.agentforge.tool.ToolCategory;
import com.example.agentforge.tool.ToolSchema;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

 
@Component
public class EchoTool implements Tool {

    public static final String NAME = "echo";

    private static final ToolSchema SCHEMA = ToolSchema.builder()
            .requiredString("text", "要回显的文本")
            .build();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "原样返回输入文本，用于验证工具调用链路";
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.UTILITY;
    }

    @Override
    public ToolSchema schema() {
        return SCHEMA;
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("utility");
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(1);
    }

    @Override
    public String execute(Map<String, Object> input) {
        return String.valueOf(input.get("text"));
    }
}
