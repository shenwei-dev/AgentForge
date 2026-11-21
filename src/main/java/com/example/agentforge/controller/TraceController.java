package com.example.agentforge.controller;

import com.example.agentforge.common.ApiResponse;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.trace.RunSummary;
import com.example.agentforge.trace.TraceManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

 
@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private final TraceManager traceManager;

    public TraceController(TraceManager traceManager) {
        this.traceManager = traceManager;
    }

    @GetMapping("/runs/{runId}/events")
    public ApiResponse<List<ExecutionEvent>> eventsByRun(@PathVariable String runId) {
        return ApiResponse.ok(traceManager.eventsByRun(runId));
    }

    @GetMapping("/conversations/{conversationId}/events")
    public ApiResponse<List<ExecutionEvent>> eventsByConversation(
            @PathVariable String conversationId) {
        return ApiResponse.ok(traceManager.eventsByConversation(conversationId));
    }

    @GetMapping("/conversations/{conversationId}/runs")
    public ApiResponse<List<RunSummary>> runsByConversation(
            @PathVariable String conversationId) {
        return ApiResponse.ok(traceManager.runsByConversation(conversationId));
    }
}
