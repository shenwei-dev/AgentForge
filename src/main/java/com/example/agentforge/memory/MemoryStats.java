package com.example.agentforge.memory;

 
public record MemoryStats(
        int historyCount,
        int retainedCount,
        int compactedCount,
        int contextChars,
        boolean summaryPresent,
        int reusableResults) {
}
