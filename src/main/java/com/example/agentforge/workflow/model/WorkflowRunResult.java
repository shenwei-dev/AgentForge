package com.example.agentforge.workflow.model;

import java.util.List;

 
public record WorkflowRunResult(
        String runId,
        String workflowId,
        String status,
        List<NodeOutcome> outcomes,
        int totalNodes,
        int succeededNodes,
        long latencyMs) {

    public record NodeOutcome(
            String nodeKey,
            int level,
            String state,
            String output,
            String error,
            long latencyMs) {
    }

     
    public record NodeResult(boolean success, String output, String error) {
        public static NodeResult ok(String output) {
            return new NodeResult(true, output, null);
        }

        public static NodeResult failed(String error) {
            return new NodeResult(false, null, error);
        }
    }

    public boolean isSuccess() {
        return totalNodes > 0 && succeededNodes == totalNodes;
    }
}
