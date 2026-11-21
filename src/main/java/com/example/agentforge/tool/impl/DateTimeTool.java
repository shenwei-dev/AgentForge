package com.example.agentforge.tool.impl;

import com.example.agentforge.tool.Tool;
import com.example.agentforge.tool.ToolCategory;
import com.example.agentforge.tool.ToolSchema;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

 
@Component
public class DateTimeTool implements Tool {

    public static final String NAME = "datetime";

    private static final ToolSchema SCHEMA = ToolSchema.builder()
            .optionalString("zone", "时区 ID，例如 Asia/Shanghai，默认 Asia/Shanghai")
            .optionalString("format", "输出格式，默认 yyyy-MM-dd HH:mm:ss")
            .build();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "获取当前日期和时间，可指定时区与输出格式";
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.DATETIME;
    }

    @Override
    public ToolSchema schema() {
        return SCHEMA;
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("system:time");
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(2);
    }

    @Override
    public String execute(Map<String, Object> input) {
        String zoneText = input.get("zone") == null ? "Asia/Shanghai" : String.valueOf(input.get("zone"));
        String pattern = input.get("format") == null
                ? "yyyy-MM-dd HH:mm:ss" : String.valueOf(input.get("format"));
        if (pattern.length() > 64) {
            throw new IllegalArgumentException("format 过长");
        }
        final ZoneId zone;
        try {
            zone = ZoneId.of(zoneText);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("非法时区: " + zoneText);
        }
        final DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法时间格式: " + pattern);
        }
        return ZonedDateTime.now(zone).format(formatter);
    }
}
