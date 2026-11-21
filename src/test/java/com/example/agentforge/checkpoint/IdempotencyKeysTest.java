package com.example.agentforge.checkpoint;

import com.example.agentforge.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

 
class IdempotencyKeysTest {

    @Test
    void shouldBeStableForSameRunAndNode() {
        WorkflowDefinition.Node node = node("A", Map.of("x", 1, "y", 2));

        assertEquals(IdempotencyKeys.forNode("run-1", node),
                IdempotencyKeys.forNode("run-1", node));
    }

    @Test
    void shouldBeIndependentOfMapInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("x", 1);
        first.put("y", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("y", 2);
        second.put("x", 1);

        assertEquals(IdempotencyKeys.forNode("run-1", node("A", first)),
                IdempotencyKeys.forNode("run-1", node("A", second)));
    }

    @Test
    void shouldDifferByRunIdNodeKeyAndConfig() {
        String base = IdempotencyKeys.forNode("run-1", node("A", Map.of("x", 1)));

        assertNotEquals(base, IdempotencyKeys.forNode("run-2", node("A", Map.of("x", 1))));
        assertNotEquals(base, IdempotencyKeys.forNode("run-1", node("B", Map.of("x", 1))));
        assertNotEquals(base, IdempotencyKeys.forNode("run-1", node("A", Map.of("x", 2))));
    }

    private WorkflowDefinition.Node node(String key, Map<String, Object> config) {
        return new WorkflowDefinition.Node(key, "节点 " + key, "DEMO", null, config);
    }
}
