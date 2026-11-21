package com.example.agentforge.controller;

import com.example.agentforge.common.ApiResponse;
import com.example.agentforge.evaluation.EvaluationResult;
import com.example.agentforge.evaluation.EvaluationService;
import com.example.agentforge.evaluation.EvaluationSummary;
import com.example.agentforge.evaluation.EvaluationTask;
import com.example.agentforge.evaluation.ModelComparison;
import com.example.agentforge.evaluation.RunMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

 
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<EvaluationTask>> tasks() {
        return ApiResponse.ok(service.tasks());
    }

    @GetMapping("/runs/{runId}/metrics")
    public ApiResponse<RunMetrics> metrics(@PathVariable String runId) {
        return ApiResponse.ok(service.metrics(runId));
    }

    @PostMapping("/tasks/{taskId}/runs/{runId}")
    public ApiResponse<EvaluationResult> evaluate(@PathVariable String taskId,
                                                  @PathVariable String runId) {
        return ApiResponse.ok(service.evaluate(runId, taskId));
    }

    @GetMapping("/summary")
    public ApiResponse<EvaluationSummary> summary() {
        return ApiResponse.ok(service.summary());
    }

    @GetMapping("/models")
    public ApiResponse<List<ModelComparison>> models() {
        return ApiResponse.ok(service.compareModels());
    }
}
