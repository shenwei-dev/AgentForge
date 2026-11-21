package com.example.agentforge.tool.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

 
final class WorkspacePaths {

    private WorkspacePaths() {
    }

    static Path root(String workspaceDir) {
        Path root = Paths.get(workspaceDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("工作目录不存在: " + root);
        }
        return root;
    }

     

    static Path resolve(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("非法路径：超出允许的工作目录");
        }
        try {
            Path real = candidate.toRealPath();
            Path realRoot = root.toRealPath();
            if (!real.startsWith(realRoot)) {
                throw new IllegalArgumentException("非法路径：符号链接逃逸");
            }
            return real;
        } catch (IOException e) {
            throw new IllegalArgumentException("文件不存在或不可访问: " + relativePath);
        }
    }
}
