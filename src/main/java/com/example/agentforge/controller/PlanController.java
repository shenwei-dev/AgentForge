package com.example.agentforge.controller;

import com.example.agentforge.common.ApiResponse;
import com.example.agentforge.planner.PlanService;
import com.example.agentforge.planner.dto.PlanExecutionResult;
import com.example.agentforge.planner.dto.PlanRequest;
import com.example.agentforge.planner.dto.TaskPlan;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

 
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

     
    @PostMapping
    public ApiResponse<TaskPlan> generate(@Valid @RequestBody PlanRequest request) {
        return ApiResponse.ok(planService.generate(request));
    }

     
    @PostMapping("/execute")
    public ApiResponse<PlanExecutionResult> execute(@Valid @RequestBody PlanRequest request) {
        return ApiResponse.ok(planService.execute(request, event -> { }));
    }

     
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody PlanRequest request) {
        return planService.stream(request);
    }
}
