package com.example.agentforge.planner.dto;

import java.util.List;

 
public record TaskPlan(String goal, List<PlanTask> tasks) {

    public TaskPlan {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
