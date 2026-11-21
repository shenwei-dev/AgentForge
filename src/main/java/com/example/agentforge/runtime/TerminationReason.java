package com.example.agentforge.runtime;

 

public enum TerminationReason {
    FINAL_ANSWER,
    MAX_ROUNDS,
    TIMEOUT,
    TOKEN_BUDGET_EXCEEDED,
    CANCELLED,
    TOOL_RETRY_EXHAUSTED,
    PARSE_RETRY_EXHAUSTED,
    UNRECOVERABLE_ERROR
}
