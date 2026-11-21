package com.example.agentforge.runtime;

import com.example.agentforge.chat.entity.Conversation;
import com.example.agentforge.chat.service.ConversationService;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.config.RuntimeProperties;
import com.example.agentforge.trace.TraceManager;
import com.example.agentforge.memory.MemoryManager;
import com.example.agentforge.memory.MemoryScope;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.model.ModelResponse;
import com.example.agentforge.model.TokenUsage;
import com.example.agentforge.runtime.dto.AgentRunRequest;
import com.example.agentforge.tool.AgentScopedToolExecutor;
import com.example.agentforge.tool.ToolCallContext;
import com.example.agentforge.tool.ToolExecutor;
import com.example.agentforge.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

 

@Service
public class AgentRunService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunService.class);
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ConversationService conversationService;
    private final ModelAdapterRegistry modelRegistry;
    private final AgentRuntime agentRuntime;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final RuntimeProperties runtimeProperties;
    private final AgentRunRegistry runRegistry;
    private final MemoryManager memoryManager;
    private final TraceManager traceManager;
    private final ThreadPoolTaskExecutor agentExecutor;

    public AgentRunService(ConversationService conversationService,
                           ModelAdapterRegistry modelRegistry,
                           AgentRuntime agentRuntime,
                           ToolExecutor toolExecutor,
                           ToolRegistry toolRegistry,
                           RuntimeProperties runtimeProperties,
                           AgentRunRegistry runRegistry,
                           MemoryManager memoryManager,
                           TraceManager traceManager,
                           @Qualifier("agentExecutor") ThreadPoolTaskExecutor agentExecutor) {
        this.conversationService = conversationService;
        this.modelRegistry = modelRegistry;
        this.agentRuntime = agentRuntime;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.runtimeProperties = runtimeProperties;
        this.runRegistry = runRegistry;
        this.memoryManager = memoryManager;
        this.traceManager = traceManager;
        this.agentExecutor = agentExecutor;
    }

     
    public AgentResult execute(AgentRunRequest request, Consumer<ExecutionEvent> events) {
        return runPrepared(prepare(request), traceManager.sink(events));
    }

     
    public SseEmitter stream(AgentRunRequest request) {
        PreparedRun prepared = prepare(request);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(() -> log.warn("Agent SSE 连接超时 runId={}", prepared.runId()));
        emitter.onError(e -> log.warn("Agent SSE 连接异常 runId={} error={}",
                prepared.runId(), e.getMessage()));

        agentExecutor.execute(() -> {
            try {
                runPrepared(prepared, event -> emit(emitter, event));
                emitter.complete();
            } catch (Exception e) {
                
                log.error("Agent 流式执行失败 runId={}", prepared.runId(), e);
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

    public boolean cancel(String runId) {
        return runRegistry.cancel(runId);
    }

    public Map<String, String> activeStates() {
        return runRegistry.snapshot();
    }

     
    private PreparedRun prepare(AgentRunRequest request) {
        Conversation conversation = conversationService.getOrCreate(request.conversationId());
        String runId = newRunId();
        conversationService.saveUserMessage(conversation.getId(), runId, request.message());

        ModelAdapter adapter = resolveAdapter(request.provider());
        Agent agent = DefaultAgent.general(toolExecutor.availableTools(),
                toolRegistry.buildToolsPrompt());
        
        List<ChatMessage> messages = memoryManager.buildContext(
                MemoryScope.agent(conversation.getId(), agent.id()),
                agent.systemPrompt(), null);

        AgentContext context = new AgentContext(runId, conversation.getId(), agent.id(),
                Clock.systemUTC(), runtimeProperties.toLimits(), messages);
        runRegistry.register(context);
        return new PreparedRun(runId, conversation.getId(), request.message(),
                agent, context, adapter);
    }

    private AgentResult runPrepared(PreparedRun prepared, Consumer<ExecutionEvent> events) {
        ToolExecutor scoped = new AgentScopedToolExecutor(
                toolExecutor, prepared.agent().allowedTools());
        ToolCallContext.set(prepared.runId(), prepared.agent().id());
        try {
            events.accept(ExecutionEvent.runStarted(prepared.runId(), prepared.conversationId(),
                    prepared.adapter().provider(), prepared.adapter().model()));
            AgentResult result = agentRuntime.run(prepared.agent(), prepared.context(),
                    prepared.adapter(), scoped, events);
            persistResult(prepared, result);
            memoryManager.appendWorkingMemory(
                    MemoryScope.agent(prepared.conversationId(), prepared.agent().id()),
                    "assistant",
                    result.isSuccess() ? result.finalAnswer() : result.terminationReason().name());
            return result;
        } finally {
            ToolCallContext.clear();
            runRegistry.unregister(prepared.runId());
        }
    }

    private void persistResult(PreparedRun prepared, AgentResult result) {
        String content = result.isSuccess()
                ? result.finalAnswer()
                : "执行未完成（" + result.terminationReason() + "）："
                + (result.errorMessage() == null ? "" : result.errorMessage());
        ModelResponse response = new ModelResponse(
                content,
                result.isSuccess() ? "stop" : result.terminationReason().name(),
                new TokenUsage(null, null, (int) result.totalTokens()),
                result.latencyMs(),
                prepared.adapter().provider(),
                prepared.adapter().model());
        conversationService.saveAssistantMessage(prepared.conversationId(), prepared.runId(), response);
        conversationService.touch(prepared.conversationId(), prepared.firstUserMessage());
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

    private ModelAdapter resolveAdapter(String provider) {
        return StringUtils.hasText(provider) ? modelRegistry.get(provider.trim()) : modelRegistry.current();
    }

    private String newRunId() {
        return "run-" + UUID.randomUUID();
    }

    private record PreparedRun(String runId, String conversationId, String firstUserMessage,
                               Agent agent, AgentContext context, ModelAdapter adapter) {
    }
}
