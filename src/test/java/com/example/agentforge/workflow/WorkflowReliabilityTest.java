package com.example.agentforge.workflow;

import com.example.agentforge.checkpoint.CancellationRegistry;
import com.example.agentforge.checkpoint.CheckpointManager;
import com.example.agentforge.checkpoint.CheckpointSnapshot;
import com.example.agentforge.config.RecoveryProperties;
import com.example.agentforge.config.WorkflowProperties;
import com.example.agentforge.workflow.model.WorkflowDefinition;
import com.example.agentforge.workflow.model.WorkflowRunResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

 
class WorkflowReliabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkflowScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void resumeShouldReuseCompletedNodesWithoutReexecution() {
        CheckpointManager checkpoints = new CheckpointManager(objectMapper);
        CancellationRegistry cancellation = new CancellationRegistry();
        AtomicInteger calls = new AtomicInteger();
        NodeExecutor executor = (node, deps) -> {
            calls.incrementAndGet();
            return WorkflowRunResult.NodeResult.ok("结果-" + node.key());
        };
        scheduler = scheduler(executor, recovery(0, 0, 30), checkpoints, cancellation);

        WorkflowRunResult first = scheduler.run(WorkflowDefinition.demo(),
                "run-1", "conv-1", event -> { });
        int afterFirst = calls.get();

        WorkflowRunResult resumed = scheduler.resume("run-1", event -> { });

        assertEquals(4, first.totalNodes());
        assertEquals("SUCCEEDED", first.status());
        assertEquals("SUCCEEDED", resumed.status());
        assertEquals(afterFirst, calls.get(), "恢复不应重新执行已完成节点");
        assertEquals(4, resumed.succeededNodes());
    }

    @Test
    void failedNodeShouldBeRetriedWithinLimit() {
        AtomicInteger attempts = new AtomicInteger();
        NodeExecutor executor = (node, deps) -> {
            int current = attempts.incrementAndGet();
            return current < 3
                    ? WorkflowRunResult.NodeResult.failed("临时失败")
                    : WorkflowRunResult.NodeResult.ok("最终成功");
        };
        scheduler = scheduler(executor, recovery(2, 0, 30),
                new CheckpointManager(objectMapper), new CancellationRegistry());
        WorkflowDefinition single = singleNode();

        WorkflowRunResult result = scheduler.run(single, "run-2", "conv-1", event -> { });

        assertEquals("SUCCEEDED", result.status());
        assertEquals(3, attempts.get());
        assertEquals("最终成功", result.outcomes().get(0).output());
    }

    @Test
    void slowNodeShouldBeMarkedFailedByTimeout() {
        NodeExecutor slow = (node, deps) -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return WorkflowRunResult.NodeResult.ok("晚到的结果");
        };
        scheduler = scheduler(slow, recovery(0, 0, 1),
                new CheckpointManager(objectMapper), new CancellationRegistry());

        WorkflowRunResult result = scheduler.run(singleNode(),
                "run-3", "conv-1", event -> { });

        assertEquals("FAILED", result.outcomes().get(0).state());
        assertTrue(result.outcomes().get(0).error().contains("超时"));
    }

    @Test
    void cancellationShouldMarkRemainingNodesCancelled() {
        CancellationRegistry cancellation = new CancellationRegistry();
        NodeExecutor executor = (node, deps) -> WorkflowRunResult.NodeResult.ok("ok");
        scheduler = scheduler(executor, recovery(0, 0, 30),
                new CheckpointManager(objectMapper), cancellation);
        cancellation.cancel("run-4");

        WorkflowRunResult result = scheduler.run(WorkflowDefinition.demo(),
                "run-4", "conv-1", event -> { });

        assertEquals("CANCELLED", result.status());
        assertTrue(result.outcomes().stream()
                .allMatch(o -> "CANCELLED".equals(o.state())));
    }

    @Test
    void shouldSaveCheckpointsForRunLevelsAndToolStates() {
        CheckpointManager checkpoints = new CheckpointManager(objectMapper);
        NodeExecutor executor = (node, deps) -> WorkflowRunResult.NodeResult.ok("结果-" + node.key());
        scheduler = scheduler(executor, recovery(0, 0, 30), checkpoints,
                new CancellationRegistry());

        scheduler.run(WorkflowDefinition.demo(), "run-5", "conv-1", event -> { });

        List<CheckpointSnapshot> saved = checkpoints.list("run-5");
        assertTrue(saved.size() >= 4, "每个层级与结束都应保存快照");
        CheckpointSnapshot latest = checkpoints.latestRun("run-5").orElseThrow();
        assertEquals(4, latest.nodeResults().size());
        assertTrue(latest.toolCallStates().containsValue("SUCCEEDED"));
        assertTrue(latest.keyEvents().stream().anyMatch(e -> e.startsWith("run_")));
    }

    private WorkflowScheduler scheduler(NodeExecutor executor, RecoveryProperties recovery,
                                        CheckpointManager checkpoints,
                                        CancellationRegistry cancellation) {
        return new WorkflowScheduler(executor, mock(WorkflowPersistence.class),
                new WorkflowProperties(), checkpoints, cancellation, recovery, objectMapper);
    }

    private RecoveryProperties recovery(int retries, long delayMs, long timeoutSeconds) {
        RecoveryProperties properties = new RecoveryProperties();
        properties.setMaxNodeRetries(retries);
        properties.setRetryDelayMs(delayMs);
        properties.setNodeTimeoutSeconds(timeoutSeconds);
        properties.setCheckpointEveryLevel(true);
        properties.setResumeEnabled(true);
        return properties;
    }

    private WorkflowDefinition singleNode() {
        return new WorkflowDefinition("wf-single", "单节点",
                List.of(new WorkflowDefinition.Node("A", "节点 A", "DEMO", null, Map.of())),
                List.of());
    }
}
