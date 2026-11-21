package com.example.agentforge.workflow;

import com.example.agentforge.checkpoint.CancellationRegistry;
import com.example.agentforge.checkpoint.CheckpointManager;
import com.example.agentforge.checkpoint.CheckpointSnapshot;
import com.example.agentforge.checkpoint.ExecutionLedger;
import com.example.agentforge.checkpoint.IdempotencyKeys;
import com.example.agentforge.common.BusinessException;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.config.RecoveryProperties;
import com.example.agentforge.config.WorkflowProperties;
import com.example.agentforge.workflow.model.DagGraph;
import com.example.agentforge.workflow.model.NodeStateMachine;
import com.example.agentforge.workflow.model.WorkflowDefinition;
import com.example.agentforge.workflow.model.WorkflowRunResult;
import com.example.agentforge.workflow.model.WorkflowStates;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

 

@Service
public class WorkflowScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);

    private final NodeExecutor nodeExecutor;
    private final WorkflowPersistence persistence;
    private final WorkflowProperties properties;
    private final CheckpointManager checkpointManager;
    private final CancellationRegistry cancellationRegistry;
    private final RecoveryProperties recoveryProperties;
    private final ObjectMapper objectMapper;
    private final ExecutorService pool;

    @Autowired
    public WorkflowScheduler(NodeExecutor nodeExecutor, WorkflowPersistence persistence,
                             WorkflowProperties properties, CheckpointManager checkpointManager,
                             CancellationRegistry cancellationRegistry,
                             RecoveryProperties recoveryProperties, ObjectMapper objectMapper) {
        this.nodeExecutor = nodeExecutor;
        this.persistence = persistence;
        this.properties = properties;
        this.checkpointManager = checkpointManager;
        this.cancellationRegistry = cancellationRegistry;
        this.recoveryProperties = recoveryProperties;
        this.objectMapper = objectMapper;
        this.pool = newPool(properties.getPoolSize());
    }

     
    public WorkflowScheduler(NodeExecutor nodeExecutor, WorkflowPersistence persistence,
                             WorkflowProperties properties) {
        this(nodeExecutor, persistence, properties,
                new CheckpointManager(new ObjectMapper()), new CancellationRegistry(),
                new RecoveryProperties(), new ObjectMapper());
    }

    private static ExecutorService newPool(int size) {
        return Executors.newFixedThreadPool(Math.max(2, size), runnable -> {
            Thread thread = new Thread(runnable,
                    "workflow-node-" + UUID.randomUUID().toString().substring(0, 8));
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }

    public WorkflowRunResult run(WorkflowDefinition definition, String runId,
                                 String conversationId, Consumer<ExecutionEvent> events) {
        return execute(definition, runId, conversationId, events, false, null);
    }

     
    public WorkflowRunResult resume(String runId, Consumer<ExecutionEvent> events) {
        if (!recoveryProperties.isResumeEnabled()) {
            throw new BusinessException(400, "任务恢复功能已关闭");
        }
        CheckpointSnapshot snapshot = checkpointManager.latestRun(runId)
                .orElseThrow(() -> new BusinessException(404, "没有可恢复的 Checkpoint: " + runId));
        try {
            WorkflowDefinition definition = objectMapper.readValue(
                    snapshot.definitionJson(), WorkflowDefinition.class);
            return execute(definition, runId, snapshot.conversationId(), events, true, snapshot);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "Checkpoint 恢复失败: " + e.getMessage());
        }
    }

    private WorkflowRunResult execute(WorkflowDefinition definition, String runId,
                                      String conversationId, Consumer<ExecutionEvent> events,
                                      boolean recovery, CheckpointSnapshot restored) {
        DagGraph graph = DagGraph.of(definition);
        List<String> errors = graph.validate();
        if (!errors.isEmpty()) {
            throw new BusinessException(400, "DAG 校验失败: " + String.join("; ", errors));
        }
        long start = System.currentTimeMillis();
        Consumer<ExecutionEvent> safeEvents = event -> {
            synchronized (events) {
                events.accept(event);
            }
        };
        String definitionJson;
        try {
            definitionJson = objectMapper.writeValueAsString(definition);
        } catch (Exception e) {
            throw new BusinessException(500, "工作流定义序列化失败: " + e.getMessage());
        }

        ExecutionLedger ledger = recovery
                ? ExecutionLedger.from(restored) : ExecutionLedger.fresh();
        CheckpointSnapshot base = restored == null
                ? CheckpointSnapshot.initial(runId, definition.id(), conversationId, definitionJson)
                : restored.withRunStatus(WorkflowStates.RunStatus.RUNNING.name());
        checkpointManager.save(base);
        ledger.event(recovery ? "resume" : "run_started");
        safeEvents.accept(ExecutionEvent.of(ExecutionEvent.RUN_STARTED, runId, conversationId,
                "INFO", recovery ? "工作流恢复执行" : "工作流开始执行",
                Map.of("workflowId", String.valueOf(definition.id()), "recovery", recovery)));
        persistence.startRun(runId, definition.id(), conversationId);

        List<List<String>> levels = graph.levels();
        Map<String, String> outputs = new ConcurrentHashMap<>();
        Set<String> blocked = ConcurrentHashMap.newKeySet();
        Set<String> settled = ConcurrentHashMap.newKeySet();
        List<WorkflowRunResult.NodeOutcome> outcomes =
                Collections.synchronizedList(new ArrayList<>());
        AtomicInteger succeeded = new AtomicInteger();
        if (recovery) {
            outputs.putAll(ledger.nodeResults());
            ledger.nodeStates().forEach((key, state) -> {
                if ("FAILED".equals(state) || "SKIPPED".equals(state)) {
                    blocked.add(key);
                }
            });
        }

        boolean cancelled = false;
        for (int level = 0; level < levels.size(); level++) {
            if (cancellationRegistry.isCancelled(runId)) {
                cancelled = true;
                markRemainingCancelled(levels, level, outcomes, ledger);
                break;
            }
            final int currentLevel = level;
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String key : levels.get(level)) {
                String idempotencyKey = IdempotencyKeys.forNode(runId, graph.node(key));
                CompletableFuture<Void> future = CompletableFuture
                        .runAsync(() -> runNode(graph, key, currentLevel, recovery, idempotencyKey,
                                outputs, blocked, settled, ledger, outcomes, succeeded,
                                runId, conversationId, safeEvents), pool)
                        .orTimeout(recoveryProperties.getNodeTimeoutSeconds(), TimeUnit.SECONDS)
                        .exceptionally(error -> {
                            if (settled.add(key)) {
                                blocked.add(key);
                                outcomes.add(new WorkflowRunResult.NodeOutcome(key, currentLevel,
                                        "FAILED", null, "节点执行超时或异常", 0));
                                ledger.completed(key, "FAILED", null, idempotencyKey);
                                ledger.event("node_timeout:" + key);
                                safeEvents.accept(ExecutionEvent.nodeCompleted(runId,
                                        conversationId, key, false, 0, "FAILED"));
                            }
                            return null;
                        });
                futures.add(future);
            }
            try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .get(properties.getLevelTimeoutSeconds(), TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                futures.forEach(f -> f.cancel(true));
                throw new BusinessException(504, "工作流第 " + level + " 层执行超时");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(500, "工作流执行被中断");
            } catch (ExecutionException e) {
                throw new BusinessException(500, "工作流执行异常: " + e.getMessage());
            }
            if (recoveryProperties.isCheckpointEveryLevel()) {
                saveLedger(base, ledger, WorkflowStates.RunStatus.RUNNING.name());
            }
        }

        List<WorkflowRunResult.NodeOutcome> ordered = outcomes.stream()
                .sorted(Comparator.comparingInt(WorkflowRunResult.NodeOutcome::level)
                        .thenComparing(WorkflowRunResult.NodeOutcome::nodeKey))
                .toList();
        int total = definition.nodes().size();
        int ok = succeeded.get();
        WorkflowStates.RunStatus status = cancelled
                ? WorkflowStates.RunStatus.CANCELLED
                : ok == total ? WorkflowStates.RunStatus.SUCCEEDED
                : ok == 0 ? WorkflowStates.RunStatus.FAILED : WorkflowStates.RunStatus.PARTIAL;
        long latencyMs = System.currentTimeMillis() - start;
        String summary = buildSummary(ordered);
        ledger.event("run_finished:" + status);
        saveLedger(base, ledger, status.name());
        persistence.finishRun(runId, status.name(), summary,
                status == WorkflowStates.RunStatus.SUCCEEDED ? null : "存在未成功或已取消的节点");
        cancellationRegistry.clear(runId);
        if (status == WorkflowStates.RunStatus.SUCCEEDED) {
            safeEvents.accept(ExecutionEvent.runCompleted(runId, conversationId,
                    summary.length(), latencyMs, null, null));
        } else {
            safeEvents.accept(ExecutionEvent.runFailed(runId, conversationId,
                    "工作流结束状态: " + status));
        }
        log.info("工作流执行结束 runId={} status={} nodes={}/{} latencyMs={}",
                runId, status, ok, total, latencyMs);
        return new WorkflowRunResult(runId, definition.id(), status.name(),
                ordered, total, ok, latencyMs);
    }

    private void saveLedger(CheckpointSnapshot base, ExecutionLedger ledger, String status) {
        CheckpointSnapshot snapshot = base.withRunStatus(status)
                .withState(ledger.nodeStates(), ledger.nodeResults(),
                        ledger.toolCallStates(), ledger.keyEvents());
        checkpointManager.save(snapshot);
    }

    private void markRemainingCancelled(List<List<String>> levels, int fromLevel,
                                        List<WorkflowRunResult.NodeOutcome> outcomes,
                                        ExecutionLedger ledger) {
        for (int level = fromLevel; level < levels.size(); level++) {
            for (String key : levels.get(level)) {
                outcomes.add(new WorkflowRunResult.NodeOutcome(key, level,
                        WorkflowStates.NodeState.CANCELLED.name(), null, "用户取消", 0));
                ledger.completed(key, WorkflowStates.NodeState.CANCELLED.name(), null, null);
            }
        }
        ledger.event("run_cancelled");
    }

    private void runNode(DagGraph graph, String key, int level, boolean recovery,
                         String idempotencyKey, Map<String, String> outputs,
                         Set<String> blocked, Set<String> settled, ExecutionLedger ledger,
                         List<WorkflowRunResult.NodeOutcome> outcomes, AtomicInteger succeeded,
                         String runId, String conversationId, Consumer<ExecutionEvent> events) {
        long nodeStart = System.currentTimeMillis();
        LocalDateTime startedAt = LocalDateTime.now();
        Set<String> dependencies = graph.dependenciesOf(key);
        WorkflowStates.NodeState state;
        WorkflowRunResult.NodeResult result;

        if (recovery && ledger.hasResult(key)) {
            
            String reused = ledger.result(key);
            outputs.put(key, reused);
            succeeded.incrementAndGet();
            state = WorkflowStates.NodeState.SUCCEEDED;
            result = WorkflowRunResult.NodeResult.ok(reused);
            events.accept(ExecutionEvent.nodeStarted(runId, conversationId, key, level));
        } else if (cancellationRegistry.isCancelled(runId)) {
            NodeStateMachine.assertTransition(WorkflowStates.NodeState.PENDING,
                    WorkflowStates.NodeState.READY);
            NodeStateMachine.assertTransition(WorkflowStates.NodeState.READY,
                    WorkflowStates.NodeState.CANCELLED);
            state = WorkflowStates.NodeState.CANCELLED;
            result = WorkflowRunResult.NodeResult.failed("用户取消");
            blocked.add(key);
        } else if (dependencies.stream().anyMatch(blocked::contains)) {
            NodeStateMachine.assertTransition(WorkflowStates.NodeState.PENDING,
                    WorkflowStates.NodeState.READY);
            NodeStateMachine.assertTransition(WorkflowStates.NodeState.READY,
                    WorkflowStates.NodeState.SKIPPED);
            state = WorkflowStates.NodeState.SKIPPED;
            result = WorkflowRunResult.NodeResult.failed("前置节点未完成，跳过: " + dependencies);
            blocked.add(key);
        } else {
            NodeStateMachine.assertTransition(WorkflowStates.NodeState.PENDING,
                    WorkflowStates.NodeState.READY);
            NodeStateMachine.assertTransition(WorkflowStates.NodeState.READY,
                    WorkflowStates.NodeState.RUNNING);
            events.accept(ExecutionEvent.nodeStarted(runId, conversationId, key, level));
            Map<String, String> dependencyOutputs = new LinkedHashMap<>();
            for (String dependency : dependencies) {
                String value = outputs.get(dependency);
                if (value != null) {
                    dependencyOutputs.put(dependency, value);
                }
            }
            result = executeWithRetry(graph.node(key), dependencyOutputs, idempotencyKey, ledger);
            if (result.success()) {
                NodeStateMachine.assertTransition(WorkflowStates.NodeState.RUNNING,
                        WorkflowStates.NodeState.SUCCEEDED);
                state = WorkflowStates.NodeState.SUCCEEDED;
                outputs.put(key, result.output());
                succeeded.incrementAndGet();
            } else {
                state = WorkflowStates.NodeState.FAILED;
                blocked.add(key);
            }
        }

        long latencyMs = System.currentTimeMillis() - nodeStart;
        if (!settled.add(key)) {
            return;
        }
        ledger.completed(key, state.name(), result.success() ? result.output() : null,
                idempotencyKey);
        ledger.event("node_" + state.name().toLowerCase() + ":" + key);
        outcomes.add(new WorkflowRunResult.NodeOutcome(key, level, state.name(),
                result.output(), result.error(), latencyMs));
        events.accept(ExecutionEvent.nodeCompleted(runId, conversationId, key,
                state == WorkflowStates.NodeState.SUCCEEDED, latencyMs, state.name()));
        persistence.recordNode(runId, key, level, state.name(), dependencies.toString(),
                result.output(), result.error(), latencyMs, startedAt);
    }

     
    private WorkflowRunResult.NodeResult executeWithRetry(WorkflowDefinition.Node node,
                                                         Map<String, String> dependencyOutputs,
                                                         String idempotencyKey,
                                                         ExecutionLedger ledger) {
        int attempts = Math.max(1, recoveryProperties.getMaxNodeRetries() + 1);
        WorkflowRunResult.NodeResult last = WorkflowRunResult.NodeResult.failed("未执行");
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                last = nodeExecutor.execute(node, dependencyOutputs);
            } catch (Exception e) {
                last = WorkflowRunResult.NodeResult.failed("节点异常: " + e.getMessage());
            }
            ledger.toolState(idempotencyKey, last.success() ? "SUCCEEDED" : "FAILED");
            if (last.success()) {
                return last;
            }
            if (attempt < attempts - 1 && recoveryProperties.getRetryDelayMs() > 0) {
                try {
                    Thread.sleep(recoveryProperties.getRetryDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return last;
    }

    private String buildSummary(List<WorkflowRunResult.NodeOutcome> outcomes) {
        StringBuilder builder = new StringBuilder("## 工作流执行结果\n\n");
        for (WorkflowRunResult.NodeOutcome outcome : outcomes) {
            builder.append("- L").append(outcome.level()).append(" `")
                    .append(outcome.nodeKey()).append("`: ")
                    .append(outcome.state()).append(" (")
                    .append(outcome.latencyMs()).append("ms)\n");
        }
        return builder.toString();
    }
}
