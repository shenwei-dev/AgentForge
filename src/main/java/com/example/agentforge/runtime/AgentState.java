package com.example.agentforge.runtime;

 
public enum AgentState {
    PENDING,
    RUNNING,
    WAITING_MODEL,
    EXECUTING_TOOL,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    BUDGET_EXCEEDED
}
