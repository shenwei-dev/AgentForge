package com.example.agentforge.tool.impl;

import com.example.agentforge.tool.Tool;
import com.example.agentforge.tool.ToolCategory;
import com.example.agentforge.tool.ToolProperties;
import com.example.agentforge.tool.ToolSchema;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

 
@Component
public class FileReadTool implements Tool {

    public static final String NAME = "file_read";

    private static final ToolSchema SCHEMA = ToolSchema.builder()
            .requiredString("path", "相对工作目录的文件路径，例如 src/main/resources/application.yml")
            .build();

    private final ToolProperties properties;

    public FileReadTool(ToolProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "读取指定工作目录内的文本文件内容（只读，限制文件大小）";
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.FILE;
    }

    @Override
    public ToolSchema schema() {
        return SCHEMA;
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("file:read");
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(5);
    }

    @Override
    public String execute(Map<String, Object> input) {
        Path root = WorkspacePaths.root(properties.getWorkspaceDir());
        Path target = WorkspacePaths.resolve(root, String.valueOf(input.get("path")));
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("目标不是普通文件");
        }
        try {
            long size = Files.size(target);
            if (size > properties.getFileReadMaxBytes()) {
                throw new IllegalArgumentException("文件过大，超过 "
                        + properties.getFileReadMaxBytes() + " 字节限制");
            }
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("文件读取失败: " + e.getMessage());
        }
    }
}
