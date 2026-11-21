package com.example.agentforge.runtime;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

 

@Service
public class AgentRunRegistry {

    private final Map<String, AgentContext> active = new ConcurrentHashMap<>();

    public void register(AgentContext context) {
        active.put(context.getRunId(), context);
    }

    public void unregister(String runId) {
        active.remove(runId);
    }

    public Optional<AgentContext> find(String runId) {
        return Optional.ofNullable(active.get(runId));
    }

     
    public boolean cancel(String runId) {
        AgentContext context = active.get(runId);
        if (context == null) {
            return false;
        }
        context.requestCancel();
        return true;
    }

    public Map<String, String> snapshot() {
        Map<String, String> states = new LinkedHashMap<>();
        active.forEach((runId, context) -> states.put(runId, context.getState().name()));
        return states;
    }
}
