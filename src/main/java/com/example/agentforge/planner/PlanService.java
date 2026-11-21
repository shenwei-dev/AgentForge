package com.example.agentforge.planner;

import com.example.agentforge.chat.entity.Conversation;
import com.example.agentforge.chat.service.ConversationService;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.trace.TraceManager;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.model.ModelResponse;
import com.example.agentforge.planner.dto.PlanExecutionResult;
import com.example.agentforge.planner.dto.PlanRequest;
import com.example.agentforge.planner.dto.TaskPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

 

@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ConversationService conversationService;
    private final ModelAdapterRegistry modelRegistry;
    private final Planner planner;
    private final PlanExecutor executor;
    private final ObjectMapper objectMapper;
    private final TraceManager traceManager;
    private final ThreadPoolTaskExecutor agentExecutor;

    public PlanService(ConversationService conversationService,
                       ModelAdapterRegistry modelRegistry,
                       Planner planner,
                       PlanExecutor executor,
                       ObjectMapper objectMapper,
                       TraceManager traceManager,
                       @Qualifier("agentExecutor") ThreadPoolTaskExecutor agentExecutor) {
        this.conversationService = conversationService;
        this.modelRegistry = modelRegistry;
        this.planner = planner;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.traceManager = traceManager;
        this.agentExecutor = agentExecutor;
    }

     
    public TaskPlan generate(PlanRequest request) {
        return createAndPersist(prepare(request), request.message(), traceManager.sink());
    }

     
    public PlanExecutionResult execute(PlanRequest request, Consumer<ExecutionEvent> events) {
        return executePrepared(prepare(request), request.message(), traceManager.sink(events));
    }

     
    public SseEmitter stream(PlanRequest request) {
        PreparedRun prepared = prepare(request);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(() -> log.warn("计划执行 SSE 超时 runId={}", prepared.runId()));
        emitter.onError(e -> log.warn("计划执行 SSE 异常 runId={} error={}",
                prepared.runId(), e.getMessage()));

        agentExecutor.execute(() -> {
            try {
                executePrepared(prepared, request.message(), event -> emit(emitter, event));
                emitter.complete();
            } catch (Exception e) {
                log.error("计划流式执行失败 runId={}", prepared.runId(), e);
                try {
                    emit(emitter, ExecutionEvent.runFailed(prepared.runId(),
                            prepared.conversationId(), e.getMessage()));
                    emitter.complete();
                } catch (Exception sendFailure) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    private PlanExecutionResult executePrepared(PreparedRun prepared, String goal,
                                                Consumer<ExecutionEvent> events) {
        events.accept(ExecutionEvent.runStarted(prepared.runId(), prepared.conversationId(),
                prepared.adapter().provider(), prepared.adapter().model()));
        TaskPlan plan = createAndPersist(prepared, goal, events);
        PlanExecutionResult result = executor.execute(plan, prepared.adapter(),
                prepared.runId(), prepared.conversationId(), events);
        persistResult(prepared, result);
        events.accept(ExecutionEvent.runCompleted(prepared.runId(), prepared.conversationId(),
                result.summary().length(), result.latencyMs(), null, prepared.adapter().provider()));
        return result;
    }

    private TaskPlan createAndPersist(PreparedRun prepared, String goal,
                                      Consumer<ExecutionEvent> events) {
        TaskPlan plan = planner.createPlan(goal, prepared.adapter(), prepared.runId(),
                prepared.conversationId(), events);
        try {
            conversationService.savePlanMessage(prepared.conversationId(), prepared.runId(),
                    objectMapper.writeValueAsString(plan),
                    prepared.adapter().provider(), prepared.adapter().model());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("计划序列化失败", e);
        }
        conversationService.touch(prepared.conversationId(), goal);
        return plan;
    }

    private void persistResult(PreparedRun prepared, PlanExecutionResult result) {
        ModelResponse response = new ModelResponse(result.summary(), "stop", null,
                result.latencyMs(), prepared.adapter().provider(), prepared.adapter().model());
        conversationService.saveAssistantMessage(prepared.conversationId(), prepared.runId(), response);
    }

    private PreparedRun prepare(PlanRequest request) {
        Conversation conversation = conversationService.getOrCreate(request.conversationId());
        String runId = "run-" + UUID.randomUUID();
        conversationService.saveUserMessage(conversation.getId(), runId, request.message());
        ModelAdapter adapter = StringUtils.hasText(request.provider())
                ? modelRegistry.get(request.provider().trim())
                : modelRegistry.current();
        return new PreparedRun(runId, conversation.getId(), adapter);
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

    private record PreparedRun(String runId, String conversationId, ModelAdapter adapter) {
    }
}
