package com.example.agentforge.planner;

 
public class PlanValidationException extends RuntimeException {

    private final String rawOutput;

    public PlanValidationException(String message, String rawOutput) {
        super(message);
        this.rawOutput = rawOutput;
    }

    public String getRawOutput() {
        return rawOutput;
    }
}
