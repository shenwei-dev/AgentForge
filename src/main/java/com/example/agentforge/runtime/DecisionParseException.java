package com.example.agentforge.runtime;

 
public class DecisionParseException extends RuntimeException {

    private final String rawOutput;

    public DecisionParseException(String message, String rawOutput) {
        super(message);
        this.rawOutput = rawOutput;
    }

    public String getRawOutput() {
        return rawOutput;
    }
}
