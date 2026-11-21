package com.example.agentforge.workflow;

import com.example.agentforge.checkpoint.CancellationRegistry;
import com.example.agentforge.checkpoint.CheckpointManager;
import com.example.agentforge.checkpoint.CheckpointSnapshot;
import com.example.agentforge.common.BusinessException;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.trace.TraceManager;
import com.example.agentforge.workflow.entity.NodeRunEntity;
import com.example.agentforge.workflow.entity.WorkflowEntity;
import com.example.agentforge.workflow.mapper.WorkflowMapper;
import com.example.agentforge.workflow.model.DagGraph;
import com.example.agentforge.workflow.model.WorkflowDefinition;
import com.example.agentforge.workflow.model.WorkflowRunResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

 
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final WorkflowMapper workflowMapper;
    private final WorkflowScheduler scheduler;
    private final WorkflowPersistence persistence;
    private final ObjectMapper objectMapper;
    private final CheckpointManager checkpointManager;
    private final CancellationRegistry cancellationRegistry;
    private final TraceManager traceManager;
    private final ThreadPoolTaskExecutor agentExecutor;

    public WorkflowService(WorkflowMapper workflowMapper, WorkflowScheduler scheduler,
                           WorkflowPersistence persistence, ObjectMapper objectMapper,
                           CheckpointManager checkpointManager,
                           CancellationRegistry cancellationRegistry,
                           TraceManager traceManager,
                           @Qualifier("agentExecutor") ThreadPoolTaskExecutor agentExecutor) {
        this.workflowMapper = workflowMapper;
        this.scheduler = scheduler;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
        this.checkpointManager = checkpointManager;
        this.cancellationRegistry = cancellationRegistry;
        this.traceManager = traceManager;
        this.agentExecutor = agentExecutor;
    }

     
    public WorkflowDefinition create(WorkflowDefinition definition) {
        List<String> errors = DagGraph.of(definition).validate();
        if (!errors.isEmpty()) {
            throw new BusinessException(400, "DAG 校验失败: " + String.join("; ", errors));
        }
        String id = definition.id() == null || definition.id().isBlank()
                ? "wf-" + UUID.randomUUID() : definition.id();
        WorkflowDefinition saved = definition.withId(id);
        try {
            WorkflowEntity entity = new WorkflowEntity();
            entity.setId(id);
            entity.setName(saved.name());
            entity.setStatus("PUBLISHED");
            entity.setVersion(1);
            entity.setDefinitionJson(objectMapper.writeValueAsString(saved));
            workflowMapper.insert(entity);
        } catch (Exception e) {
            log.warn("工作流定义落库失败，仍可在内存中执行 workflowId={}", id, e);
        }
        return saved;
    }

    public List<WorkflowDefinition> list() {
        List<WorkflowDefinition> result = new ArrayList<>();
        try {
            for (WorkflowEntity entity : workflowMapper.selectList(null)) {
                WorkflowDefinition parsed = parse(entity);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
        } catch (Exception e) {
            log.warn("工作流列表读取失败", e);
        }
        return result;
    }

    public WorkflowDefinition get(String id) {
        try {
            WorkflowEntity entity = workflowMapper.selectById(id);
            WorkflowDefinition parsed = parse(entity);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("工作流读取失败 workflowId={}", id, e);
        }
        if (WorkflowDefinition.demo().id().equals(id)) {
            return WorkflowDefinition.demo();
        }
        throw new BusinessException(404, "工作流不存在: " + id);
    }

    public Map<String, Object> validate(String id) {
        WorkflowDefinition definition = get(id);
        DagGraph graph = DagGraph.of(definition);
        List<String> errors = graph.validate();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        result.put("levels", errors.isEmpty() ? graph.levels() : List.of());
        return result;
    }

    public WorkflowRunResult run(String workflowId, String conversationId,
                                 Consumer<ExecutionEvent> events) {
        return scheduler.run(get(workflowId), newRunId(), conversationId,
                traceManager.sink(events));
    }

    public WorkflowRunResult runDemo(String conversationId, Consumer<ExecutionEvent> events) {
        return scheduler.run(WorkflowDefinition.demo(), newRunId(), conversationId,
                traceManager.sink(events));
    }

    public SseEmitter stream(String workflowId, String conversationId) {
        return streamInternal(get(workflowId), conversationId);
    }

    public SseEmitter streamDemo(String conversationId) {
        return streamInternal(WorkflowDefinition.demo(), conversationId);
    }

    public List<NodeRunEntity> nodeRuns(String runId) {
        return persistence.nodesOf(runId);
    }

     
    public WorkflowRunResult resume(String runId, Consumer<ExecutionEvent> events) {
        return scheduler.resume(runId, traceManager.sink(events));
    }

     
    public Map<String, Object> cancelRun(String runId) {
        cancellationRegistry.cancel(runId);
        return Map.of("runId", runId, "cancelled", true);
    }

    public List<CheckpointSnapshot> checkpoints(String runId) {
        return checkpointManager.list(runId);
    }

    private SseEmitter streamInternal(WorkflowDefinition definition, String conversationId) {
        String runId = newRunId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        agentExecutor.execute(() -> {
            try {
                scheduler.run(definition, runId, conversationId,
                        event -> emit(emitter, event));
                emitter.complete();
            } catch (Exception e) {
                log.error("工作流流式执行失败 runId={}", runId, e);
                try {
                    emit(emitter, ExecutionEvent.runFailed(runId, conversationId, e.getMessage()));
                    emitter.complete();
                } catch (Exception sendFailure) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    private void emit(SseEmitter emitter, ExecutionEvent event) {
        traceManager.sink().accept(event);
        try {
            emitter.send(SseEmitter.event()
                    .id(event.eventId())
                    .name(event.event())
                    .data(event));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 推送失败，客户端可能已断开连接", e);
        }
    }

    private WorkflowDefinition parse(WorkflowEntity entity) {
        if (entity == null || entity.getDefinitionJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(entity.getDefinitionJson(), WorkflowDefinition.class);
        } catch (JsonProcessingException e) {
            log.warn("工作流定义解析失败 id={}", entity.getId(), e);
            return null;
        }
    }

    private String newRunId() {
        return "run-" + UUID.randomUUID();
    }
}
