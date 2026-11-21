package com.example.agentforge.controller;

import com.example.agentforge.common.ApiResponse;
import com.example.agentforge.mcp.McpCallRequest;
import com.example.agentforge.mcp.McpToolService;
import com.example.agentforge.tool.ToolDescriptor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

 
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpToolService service;

    public McpController(McpToolService service) {
        this.service = service;
    }

    @GetMapping("/tools")
    public ApiResponse<List<ToolDescriptor>> tools() {
        return ApiResponse.ok(service.discovered());
    }

    @PostMapping("/refresh")
    public ApiResponse<List<ToolDescriptor>> refresh() {
        return ApiResponse.ok(service.discoverAndRegister());
    }

    @PostMapping("/call")
    public ApiResponse<Map<String, Object>> call(@Valid @RequestBody McpCallRequest request) {
        String content = service.call(request.tool(), request.arguments());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", request.tool());
        result.put("content", content == null ? "" : content);
        return ApiResponse.ok(result);
    }
}
