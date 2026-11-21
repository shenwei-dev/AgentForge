package com.example.agentforge.checkpoint;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

 
@Service
public class CancellationRegistry {

    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    public void cancel(String runId) {
        cancelled.add(runId);
    }

    public boolean isCancelled(String runId) {
        return cancelled.contains(runId);
    }

    public void clear(String runId) {
        cancelled.remove(runId);
    }
}
