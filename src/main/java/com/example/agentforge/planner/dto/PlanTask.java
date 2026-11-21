package com.example.agentforge.planner.dto;

import java.util.List;

 

public record PlanTask(
        String id,
        String title,
        String description,
        List<String> dependsOn,
        String agentRole) {

    public PlanTask {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
