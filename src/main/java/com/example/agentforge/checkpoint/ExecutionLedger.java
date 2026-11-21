package com.example.agentforge.checkpoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

 

public final class ExecutionLedger {

    private static final int MAX_EVENTS = 100;

    private final Map<String, String> nodeStates = new ConcurrentHashMap<>();
    private final Map<String, String> nodeResults = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyKeys = new ConcurrentHashMap<>();
    private final Map<String, String> toolCallStates = new ConcurrentHashMap<>();
    private final List<String> keyEvents = new CopyOnWriteArrayList<>();

    public static ExecutionLedger fresh() {
        return new ExecutionLedger();
    }

    public static ExecutionLedger from(CheckpointSnapshot snapshot) {
        ExecutionLedger ledger = new ExecutionLedger();
        ledger.nodeStates.putAll(snapshot.nodeStates());
        ledger.nodeResults.putAll(snapshot.nodeResults());
        ledger.toolCallStates.putAll(snapshot.toolCallStates());
        ledger.keyEvents.addAll(snapshot.keyEvents());
        return ledger;
    }

    public boolean hasResult(String nodeKey) {
        return nodeResults.containsKey(nodeKey);
    }

    public String result(String nodeKey) {
        return nodeResults.get(nodeKey);
    }

    public void completed(String nodeKey, String state, String result, String idempotencyKey) {
        nodeStates.put(nodeKey, state);
        if (result != null) {
            nodeResults.put(nodeKey, result);
        }
        if (idempotencyKey != null) {
            idempotencyKeys.put(nodeKey, idempotencyKey);
        }
    }

    public void toolState(String idempotencyKey, String state) {
        toolCallStates.put(idempotencyKey, state);
    }

    public void event(String text) {
        if (keyEvents.size() < MAX_EVENTS) {
            keyEvents.add(text);
        }
    }

    public Map<String, String> nodeStates() {
        return new LinkedHashMap<>(nodeStates);
    }

    public Map<String, String> nodeResults() {
        return new LinkedHashMap<>(nodeResults);
    }

    public Map<String, String> toolCallStates() {
        return new LinkedHashMap<>(toolCallStates);
    }

    public List<String> keyEvents() {
        return new ArrayList<>(keyEvents);
    }
}
