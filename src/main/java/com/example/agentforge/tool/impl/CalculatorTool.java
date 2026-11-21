package com.example.agentforge.tool.impl;

import com.example.agentforge.tool.Tool;
import com.example.agentforge.tool.ToolCategory;
import com.example.agentforge.tool.ToolSchema;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

 
@Component
public class CalculatorTool implements Tool {

    public static final String NAME = "calculator";

    private static final ToolSchema SCHEMA = ToolSchema.builder()
            .requiredString("expression", "数学表达式，仅支持数字、+ - * / 与括号，例如 (12+3)*4")
            .build();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "计算数学表达式的值，支持加减乘除、括号、负数与小数";
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.COMPUTE;
    }

    @Override
    public ToolSchema schema() {
        return SCHEMA;
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("compute");
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(2);
    }

    @Override
    public String execute(Map<String, Object> input) {
        String expression = String.valueOf(input.get("expression"));
        double result = ExpressionParser.parse(expression);
        if (Double.isInfinite(result) || Double.isNaN(result)) {
            throw new IllegalArgumentException("计算结果无效");
        }
        if (result == Math.rint(result) && Math.abs(result) < Long.MAX_VALUE) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }
}
