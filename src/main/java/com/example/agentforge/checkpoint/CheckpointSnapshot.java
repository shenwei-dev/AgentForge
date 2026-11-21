package com.example.agentforge.checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

 

public record CheckpointSnapshot(
        String runId,
        String workflowId,
        String conversationId,
        String nodeId,
        String stateType,
        int version,
        String runStatus,
        String definitionJson,
        Map<String, String> nodeStates,
        Map<String, String> nodeResults,
        Map<String, String> toolCallStates,
        List<String> keyEvents,
        long createdAtMillis) {

    public static final String TYPE_RUN = "RUN";
    public static final String TYPE_NODE = "NODE";
    public static final String TYPE_AGENT = "AGENT";
    public static final String TYPE_TOOL = "TOOL";

    public CheckpointSnapshot {
        nodeStates = nodeStates == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(nodeStates));
        nodeResults = nodeResults == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(nodeResults));
        toolCallStates = toolCallStates == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(toolCallStates));
        keyEvents = keyEvents == null ? List.of() : List.copyOf(keyEvents);
    }

    public static CheckpointSnapshot initial(String runId, String workflowId,
                                             String conversationId, String definitionJson) {
        return new CheckpointSnapshot(runId, workflowId, conversationId, "", TYPE_RUN, 0,
                "RUNNING", definitionJson, Map.of(), Map.of(), Map.of(), List.of(),
                System.currentTimeMillis());
    }

    public CheckpointSnapshot withVersion(int newVersion) {
        return new CheckpointSnapshot(runId, workflowId, conversationId, nodeId, stateType,
                newVersion, runStatus, definitionJson, nodeStates, nodeResults,
                toolCallStates, keyEvents, createdAtMillis);
    }

    public CheckpointSnapshot withRunStatus(String status) {
        return new CheckpointSnapshot(runId, workflowId, conversationId, nodeId, stateType,
                version, status, definitionJson, nodeStates, nodeResults,
                toolCallStates, keyEvents, System.currentTimeMillis());
    }

    public CheckpointSnapshot withState(Map<String, String> states, Map<String, String> results,
                                        Map<String, String> toolStates, List<String> events) {
        return new CheckpointSnapshot(runId, workflowId, conversationId, nodeId, stateType,
                version, runStatus, definitionJson, states, results, toolStates,
                new ArrayList<>(events), System.currentTimeMillis());
    }
}
