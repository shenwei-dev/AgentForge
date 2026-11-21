package com.example.agentforge.model;

 

public record StreamChunk(String delta, String finishReason, TokenUsage usage, boolean done) {

     
    public static StreamChunk finished() {
        return new StreamChunk(null, null, null, true);
    }
}
