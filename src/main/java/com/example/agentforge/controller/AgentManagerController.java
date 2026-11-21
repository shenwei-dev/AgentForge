package com.example.agentforge.controller;

import com.example.agentforge.agent.AgentExecutionTracker;
import com.example.agentforge.agent.AgentManager;
import com.example.agentforge.agent.AgentProfile;
import com.example.agentforge.agent.AgentStatus;
import com.example.agentforge.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

 
@RestController
@RequestMapping("/api/agents")
public class AgentManagerController {

    private final AgentManager manager;
    private final AgentExecutionTracker tracker;

    public AgentManagerController(AgentManager manager, AgentExecutionTracker tracker) {
        this.manager = manager;
        this.tracker = tracker;
    }

    @GetMapping
    public ApiResponse<List<AgentProfile>> list() {
        return ApiResponse.ok(manager.list());
    }

    @GetMapping("/runs/{runId}/status")
    public ApiResponse<List<AgentStatus>> status(@PathVariable String runId) {
        return ApiResponse.ok(tracker.of(runId));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh() {
        manager.refresh();
        return ApiResponse.ok(Map.of("roles", manager.list().size()));
    }
}
