package com.example.agentforge.controller;

import com.example.agentforge.common.ApiResponse;
import com.example.agentforge.common.BusinessException;
import com.example.agentforge.runtime.AgentResult;
import com.example.agentforge.runtime.AgentRunService;
import com.example.agentforge.runtime.dto.AgentRunRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

 
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentRunService agentRunService;

    public AgentController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

     
    @PostMapping("/runs")
    public ApiResponse<AgentResult> run(@Valid @RequestBody AgentRunRequest request) {
        return ApiResponse.ok(agentRunService.execute(request, event -> { }));
    }

     
    @PostMapping(value = "/runs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AgentRunRequest request) {
        return agentRunService.stream(request);
    }

     
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String runId) {
        if (!agentRunService.cancel(runId)) {
            throw new BusinessException(404, "执行不存在或已结束: " + runId);
        }
        return ApiResponse.ok(Map.of("runId", runId, "cancelled", true));
    }

     
    @GetMapping("/runs")
    public ApiResponse<Map<String, String>> activeRuns() {
        return ApiResponse.ok(agentRunService.activeStates());
    }
}
