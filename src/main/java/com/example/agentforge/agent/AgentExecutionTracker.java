package com.example.agentforge.agent;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

 

@Service
public class AgentExecutionTracker {

    private static final int MAX_TRACKED_RUNS = 200;

    private final Map<String, List<AgentStatus>> byRun = new ConcurrentHashMap<>();

    public void reset(String runId) {
        byRun.remove(runId);
        if (byRun.size() > MAX_TRACKED_RUNS) {
            byRun.clear();
        }
    }

    public void started(String runId, String taskId, String role) {
        byRun.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>())
                .add(new AgentStatus(taskId, role, AgentStatus.RUNNING, null));
    }

    public void finished(String runId, String taskId, String role,
                         boolean success, long latencyMs) {
        List<AgentStatus> statuses = byRun.get(runId);
        if (statuses == null) {
            return;
        }
        String state = success ? AgentStatus.SUCCEEDED : AgentStatus.FAILED;
        for (int i = statuses.size() - 1; i >= 0; i--) {
            AgentStatus current = statuses.get(i);
            if (current.taskId().equals(taskId)
                    && AgentStatus.RUNNING.equals(current.state())) {
                statuses.set(i, new AgentStatus(taskId, role, state, latencyMs));
                return;
            }
        }
    }

    public List<AgentStatus> of(String runId) {
        List<AgentStatus> statuses = byRun.get(runId);
        return statuses == null ? List.of() : new ArrayList<>(statuses);
    }
}
