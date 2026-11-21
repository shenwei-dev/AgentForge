package com.example.agentforge.controller;

import com.example.agentforge.common.ApiResponse;
import com.example.agentforge.tool.ToolDescriptor;
import com.example.agentforge.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

 
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolRegistry registry;

    public ToolController(ToolRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ApiResponse<List<ToolDescriptor>> list() {
        return ApiResponse.ok(registry.descriptors());
    }
}
