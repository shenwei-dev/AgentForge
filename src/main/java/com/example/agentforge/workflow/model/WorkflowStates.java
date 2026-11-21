package com.example.agentforge.workflow.model;

 
public final class WorkflowStates {

    private WorkflowStates() {
    }

    public enum NodeState {
        PENDING,
        READY,
        RUNNING,
        SUCCEEDED,
        FAILED,
        SKIPPED,
        WAITING_APPROVAL,
        CANCELLED
    }

    public enum RunStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        PARTIAL,
        CANCELLED
    }
}
