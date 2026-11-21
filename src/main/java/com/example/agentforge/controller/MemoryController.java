package com.example.agentforge.controller;

import com.example.agentforge.common.ApiResponse;
import com.example.agentforge.memory.MemoryManager;
import com.example.agentforge.memory.MemoryScope;
import com.example.agentforge.memory.MemoryStats;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

 
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryManager memoryManager;

    public MemoryController(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    @GetMapping("/{conversationId}/stats")
    public ApiResponse<MemoryStats> stats(@PathVariable String conversationId) {
        return ApiResponse.ok(memoryManager.stats(conversationId));
    }

    @PostMapping("/{conversationId}/compact")
    public ApiResponse<MemoryStats> compact(@PathVariable String conversationId) {
        return ApiResponse.ok(memoryManager.compact(conversationId));
    }

    @DeleteMapping("/{conversationId}/working")
    public ApiResponse<Map<String, Object>> clear(@PathVariable String conversationId) {
        memoryManager.clear(MemoryScope.conversation(conversationId));
        return ApiResponse.ok(Map.of("conversationId", conversationId, "cleared", true));
    }

    @GetMapping("/{conversationId}/results")
    public ApiResponse<Map<String, String>> results(@PathVariable String conversationId) {
        return ApiResponse.ok(memoryManager.taskResults(conversationId));
    }
}
