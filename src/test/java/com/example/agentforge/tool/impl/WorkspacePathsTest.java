package com.example.agentforge.tool.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class WorkspacePathsTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldAllowFileInsideWorkspace() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        Path root = WorkspacePaths.root(tempDir.toString());

        Path resolved = WorkspacePaths.resolve(root, "a.txt");

        assertTrue(resolved.startsWith(root.toRealPath()));
        assertEquals("hello", Files.readString(resolved));
    }

    @Test
    void shouldRejectParentTraversal() {
        Path root = WorkspacePaths.root(tempDir.toString());

        assertThrows(IllegalArgumentException.class,
                () -> WorkspacePaths.resolve(root, "../outside.txt"));
    }

    @Test
    void shouldRejectAbsolutePathOutsideWorkspace() {
        Path root = WorkspacePaths.root(tempDir.toString());
        Path outside = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();

        assertThrows(IllegalArgumentException.class,
                () -> WorkspacePaths.resolve(root, outside.toString()));
    }

    @Test
    void shouldRejectBlankPath() {
        Path root = WorkspacePaths.root(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolve(root, "  "));
    }
}
