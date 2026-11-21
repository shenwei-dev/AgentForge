package com.example.agentforge.evaluation;

import java.util.List;

 
public record EvaluationResult(
        String runId,
        String taskId,
        String taskName,
        int score,
        int maxScore,
        boolean passed,
        List<String> reasons,
        RunMetrics metrics) {
}
