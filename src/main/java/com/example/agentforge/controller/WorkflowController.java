package com.example.agentforge.controller;

import com.example.agentforge.checkpoint.CheckpointSnapshot;
import com.example.agentforge.common.ApiResponse;
import com.example.agentforge.workflow.WorkflowService;
import com.example.agentforge.workflow.entity.NodeRunEntity;
import com.example.agentforge.workflow.model.WorkflowDefinition;
import com.example.agentforge.workflow.model.WorkflowRunResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

 
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<WorkflowDefinition> create(@RequestBody WorkflowDefinition definition) {
        return ApiResponse.ok(service.create(definition));
    }

    @GetMapping
    public ApiResponse<List<WorkflowDefinition>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowDefinition> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

     
    @PostMapping("/{id}/validate")
    public ApiResponse<Map<String, Object>> validate(@PathVariable String id) {
        return ApiResponse.ok(service.validate(id));
    }

    @PostMapping("/{id}/runs")
    public ApiResponse<WorkflowRunResult> run(
            @PathVariable String id,
            @RequestParam(required = false) String conversationId) {
        return ApiResponse.ok(service.run(id, conversationId, event -> { }));
    }

    @PostMapping(value = "/{id}/runs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String id,
            @RequestParam(required = false) String conversationId) {
        return service.stream(id, conversationId);
    }

     
    @PostMapping("/demo/run")
    public ApiResponse<WorkflowRunResult> runDemo(
            @RequestParam(required = false) String conversationId) {
        return ApiResponse.ok(service.runDemo(conversationId, event -> { }));
    }

    @PostMapping(value = "/demo/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDemo(@RequestParam(required = false) String conversationId) {
        return service.streamDemo(conversationId);
    }

    @GetMapping("/runs/{runId}/nodes")
    public ApiResponse<List<NodeRunEntity>> nodeRuns(@PathVariable String runId) {
        return ApiResponse.ok(service.nodeRuns(runId));
    }

     
    @PostMapping("/runs/{runId}/resume")
    public ApiResponse<WorkflowRunResult> resume(@PathVariable String runId) {
        return ApiResponse.ok(service.resume(runId, event -> { }));
    }

     
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String runId) {
        return ApiResponse.ok(service.cancelRun(runId));
    }

    @GetMapping("/runs/{runId}/checkpoints")
    public ApiResponse<List<CheckpointSnapshot>> checkpoints(@PathVariable String runId) {
        return ApiResponse.ok(service.checkpoints(runId));
    }
}
