package com.example.agentforge.model;

 

public record ModelCapabilities(
        boolean functionCalling,
        boolean structuredOutput,
        boolean streaming,
        int contextWindow) {
}
