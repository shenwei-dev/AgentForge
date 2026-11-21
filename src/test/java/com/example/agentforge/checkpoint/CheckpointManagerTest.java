package com.example.agentforge.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class CheckpointManagerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void saveShouldIncrementVersion() {
        CheckpointManager manager = new CheckpointManager(mapper);
        CheckpointSnapshot base = CheckpointSnapshot.initial(
                "run-1", "wf-1", "conv-1", "{\"id\":\"wf-1\"}");

        assertEquals(1, manager.save(base).version());
        assertEquals(2, manager.save(base).version());
        assertEquals(3, manager.save(base).version());
        assertEquals(3, manager.list("run-1").size());
    }

    @Test
    void latestRunShouldReturnNewestSnapshot() {
        CheckpointManager manager = new CheckpointManager(mapper);
        CheckpointSnapshot base = CheckpointSnapshot.initial(
                "run-1", "wf-1", "conv-1", "{\"id\":\"wf-1\"}");
        manager.save(base.withState(Map.of("A", "RUNNING"), Map.of(), Map.of(), List.of()));
        manager.save(base.withRunStatus("SUCCEEDED")
                .withState(Map.of("A", "SUCCEEDED"), Map.of("A", "结果"),
                        Map.of(), List.of("node_succeeded:A")));

        CheckpointSnapshot latest = manager.latestRun("run-1").orElseThrow();

        assertEquals("SUCCEEDED", latest.runStatus());
        assertEquals("结果", latest.nodeResults().get("A"));
        assertEquals(2, latest.version());
    }

    @Test
    void shouldWorkWithoutDatabaseMapper() {
        CheckpointManager manager = new CheckpointManager(mapper);

        CheckpointSnapshot saved = manager.save(CheckpointSnapshot.initial(
                "run-2", "wf-1", "conv-1", "{}"));

        assertEquals(1, saved.version());
        assertTrue(manager.latestRun("run-2").isPresent());
    }

    @Test
    void snapshotShouldRoundTripThroughJson() throws Exception {
        CheckpointSnapshot snapshot = CheckpointSnapshot.initial(
                "run-3", "wf-1", "conv-1", "{}")
                .withState(Map.of("A", "SUCCEEDED"), Map.of("A", "内容"),
                        Map.of("key-1", "SUCCEEDED"), List.of("run_started"));

        String json = mapper.writeValueAsString(snapshot);
        CheckpointSnapshot restored = mapper.readValue(json, CheckpointSnapshot.class);

        assertEquals("内容", restored.nodeResults().get("A"));
        assertEquals("run_started", restored.keyEvents().get(0));
        assertEquals("key-1", restored.toolCallStates().keySet().iterator().next());
    }
}
