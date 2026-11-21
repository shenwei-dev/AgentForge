package com.example.agentforge.tool.impl;

import com.example.agentforge.tool.Tool;
import com.example.agentforge.tool.ToolCategory;
import com.example.agentforge.tool.ToolProperties;
import com.example.agentforge.tool.ToolSchema;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

 
@Component
public class FileSearchTool implements Tool {

    public static final String NAME = "file_search";

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".idea", ".mvn", "node_modules", "target", "dist");

    private static final ToolSchema SCHEMA = ToolSchema.builder()
            .requiredString("keyword", "文件名中包含的关键词")
            .build();

    private final ToolProperties properties;

    public FileSearchTool(ToolProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "在指定工作目录中按文件名关键词搜索文件，返回相对路径列表";
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
        String keyword = String.valueOf(input.get("keyword"));
        Path root = WorkspacePaths.root(properties.getWorkspaceDir());
        try (Stream<Path> paths = Files.walk(root, 5)) {
            List<String> matches = paths
                    .filter(path -> !hasIgnoredSegment(root, path))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains(keyword))
                    .limit(properties.getFileSearchMaxResults())
                    .map(path -> root.relativize(path).toString())
                    .toList();
            return matches.isEmpty() ? "未找到匹配文件" : String.join("\n", matches);
        } catch (IOException e) {
            throw new IllegalStateException("文件搜索失败: " + e.getMessage());
        }
    }

    private boolean hasIgnoredSegment(Path root, Path path) {
        for (Path segment : root.relativize(path)) {
            if (IGNORED_DIRS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }
}
