package com.example.agentforge.workflow;

import com.example.agentforge.common.BusinessException;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.config.WorkflowProperties;
import com.example.agentforge.workflow.model.WorkflowDefinition;
import com.example.agentforge.workflow.model.WorkflowRunResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

 
class WorkflowSchedulerTest {

    private final WorkflowProperties properties = new WorkflowProperties();
    private WorkflowScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void shouldRunIndependentNodesInParallel() {
        
        CountDownLatch latch = new CountDownLatch(2);
        NodeExecutor executor = (node, deps) -> {
            if ("A".equals(node.key()) || "B".equals(node.key())) {
                latch.countDown();
                try {
                    if (!latch.await(2, TimeUnit.SECONDS)) {
                        return WorkflowRunResult.NodeResult.failed("未并行执行");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return WorkflowRunResult.NodeResult.failed("被中断");
                }
            }
            return WorkflowRunResult.NodeResult.ok("结果-" + node.key());
        };
        scheduler = new WorkflowScheduler(executor, mock(WorkflowPersistence.class), properties);

        WorkflowRunResult result = scheduler.run(WorkflowDefinition.demo(),
                "run-1", "conv-1", event -> { });

        assertEquals(4, result.totalNodes());
        assertEquals(4, result.succeededNodes());
        assertEquals("SUCCEEDED", result.status());
    }

    @Test
    void shouldWaitForDependenciesAndPassUpstreamOutputs() {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        Map<String, Map<String, String>> captured = new ConcurrentHashMap<>();
        NodeExecutor executor = (node, deps) -> {
            order.add(node.key());
            captured.put(node.key(), Map.copyOf(deps));
            return WorkflowRunResult.NodeResult.ok("结果-" + node.key());
        };
        scheduler = new WorkflowScheduler(executor, mock(WorkflowPersistence.class), properties);

        WorkflowRunResult result = scheduler.run(WorkflowDefinition.demo(),
                "run-1", "conv-1", event -> { });

        assertTrue(order.indexOf("A") < order.indexOf("C"));
        assertTrue(order.indexOf("B") < order.indexOf("C"));
        assertTrue(order.indexOf("C") < order.indexOf("D"));
        assertTrue(captured.get("C").containsKey("A"));
        assertTrue(captured.get("C").get("A").contains("结果-A"));
        assertTrue(captured.get("D").containsKey("C"));
        assertEquals("SUCCEEDED", result.status());
    }

    @Test
    void shouldSkipDownstreamNodesWhenDependencyFails() {
        NodeExecutor executor = (node, deps) -> "A".equals(node.key())
                ? WorkflowRunResult.NodeResult.failed("A 执行失败")
                : WorkflowRunResult.NodeResult.ok("结果-" + node.key());
        scheduler = new WorkflowScheduler(executor, mock(WorkflowPersistence.class), properties);

        WorkflowRunResult result = scheduler.run(WorkflowDefinition.demo(),
                "run-1", "conv-1", event -> { });

        assertEquals(1, result.succeededNodes());
        assertEquals("PARTIAL", result.status());
        assertTrue(stateOf(result, "A").equals("FAILED"));
        assertTrue(stateOf(result, "C").equals("SKIPPED"));
        assertTrue(stateOf(result, "D").equals("SKIPPED"));
    }

    @Test
    void shouldEmitNodeEventsWithLevels() {
        NodeExecutor executor = (node, deps) -> WorkflowRunResult.NodeResult.ok("ok");
        scheduler = new WorkflowScheduler(executor, mock(WorkflowPersistence.class), properties);
        List<ExecutionEvent> events = Collections.synchronizedList(new ArrayList<>());

        scheduler.run(WorkflowDefinition.demo(), "run-1", "conv-1", events::add);

        assertTrue(events.stream().anyMatch(e -> e.event().equals(ExecutionEvent.NODE_STARTED)));
        assertTrue(events.stream().anyMatch(e -> e.event().equals(ExecutionEvent.NODE_COMPLETED)));
        assertTrue(events.stream().anyMatch(e -> e.event().equals(ExecutionEvent.RUN_COMPLETED)));
        assertTrue(events.stream()
                .filter(e -> e.event().equals(ExecutionEvent.NODE_STARTED))
                .anyMatch(e -> "0".equals(String.valueOf(e.payload().get("level")))));
    }

    @Test
    void shouldRejectCyclicWorkflow() {
        WorkflowDefinition cyclic = new WorkflowDefinition("cyclic", "环",
                List.of(node("A"), node("B")),
                List.of(new WorkflowDefinition.Edge("A", "B", null),
                        new WorkflowDefinition.Edge("B", "A", null)));
        scheduler = new WorkflowScheduler((n, d) -> WorkflowRunResult.NodeResult.ok("ok"),
                mock(WorkflowPersistence.class), properties);

        assertThrows(BusinessException.class,
                () -> scheduler.run(cyclic, "run-1", "conv-1", event -> { }));
    }

    private String stateOf(WorkflowRunResult result, String nodeKey) {
        return result.outcomes().stream()
                .filter(o -> o.nodeKey().equals(nodeKey))
                .findFirst().orElseThrow().state();
    }

    private WorkflowDefinition.Node node(String key) {
        return new WorkflowDefinition.Node(key, "节点 " + key, "DEMO", null, Map.of());
    }
}
