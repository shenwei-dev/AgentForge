package com.example.agentforge.chat.service;

import com.example.agentforge.chat.dto.ChatRequest;
import com.example.agentforge.chat.dto.MessageVO;
import com.example.agentforge.chat.entity.ChatMessageEntity;
import com.example.agentforge.chat.entity.Conversation;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.memory.MemoryManager;
import com.example.agentforge.trace.TraceManager;
import com.example.agentforge.memory.MemoryScope;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.model.ModelRequest;
import com.example.agentforge.model.ModelResponse;
import com.example.agentforge.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

 

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private static final String SYSTEM_PROMPT = """
            你是 AgentForge 企业级智能体运行时与编排引擎的对话助手。
            请基于对话历史给出准确、简洁、结构化的回答；内容较长时使用 Markdown 组织。
            当前为第一阶段（基础聊天链路），后续阶段将接入 Agent Runtime、工具调用与多 Agent 协作。
            """;

    private final ConversationService conversationService;
    private final ModelAdapterRegistry registry;
    private final MemoryManager memoryManager;
    private final TraceManager traceManager;
    private final ThreadPoolTaskExecutor agentExecutor;

    public ChatService(ConversationService conversationService,
                       ModelAdapterRegistry registry,
                       MemoryManager memoryManager,
                       TraceManager traceManager,
                       @Qualifier("agentExecutor") ThreadPoolTaskExecutor agentExecutor) {
        this.conversationService = conversationService;
        this.registry = registry;
        this.memoryManager = memoryManager;
        this.traceManager = traceManager;
        this.agentExecutor = agentExecutor;
    }

     
    public MessageVO complete(ChatRequest request) {
        Conversation conversation = conversationService.getOrCreate(request.conversationId());
        String runId = newRunId();
        conversationService.saveUserMessage(conversation.getId(), runId, request.message());

        ModelAdapter adapter = resolveAdapter(request.provider());
        Consumer<ExecutionEvent> events = traceManager.sink();
        events.accept(ExecutionEvent.runStarted(runId, conversation.getId(),
                adapter.provider(), adapter.model()));
        ModelResponse response = adapter.chat(ModelRequest.of(buildMessages(conversation.getId())));
        ChatMessageEntity saved = conversationService.saveAssistantMessage(
                conversation.getId(), runId, response);
        conversationService.touch(conversation.getId(), request.message());
        events.accept(ExecutionEvent.runCompleted(runId, conversation.getId(),
                response.content() == null ? 0 : response.content().length(),
                response.latencyMs(), null, adapter.provider()));
        return MessageVO.from(saved);
    }

     
    public SseEmitter stream(ChatRequest request) {
        Conversation conversation = conversationService.getOrCreate(request.conversationId());
        String runId = newRunId();
        conversationService.saveUserMessage(conversation.getId(), runId, request.message());

        ModelAdapter adapter = resolveAdapter(request.provider());
        List<ChatMessage> messages = buildMessages(conversation.getId());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(() -> log.warn("SSE 连接超时 runId={}", runId));
        emitter.onError(error -> log.warn("SSE 连接异常 runId={} error={}", runId, error.getMessage()));

        agentExecutor.execute(() -> runStream(emitter, conversation.getId(), runId,
                request.message(), adapter, messages));
        return emitter;
    }

    private void runStream(SseEmitter emitter, String conversationId, String runId,
                           String firstUserMessage, ModelAdapter adapter, List<ChatMessage> messages) {
        StringBuilder answer = new StringBuilder();
        TokenUsage[] usageHolder = new TokenUsage[1];
        long start = System.currentTimeMillis();
        try {
            emit(emitter, ExecutionEvent.runStarted(runId, conversationId,
                    adapter.provider(), adapter.model()));

            adapter.chatStream(ModelRequest.of(messages), chunk -> {
                if (chunk.done()) {
                    return;
                }
                if (chunk.usage() != null) {
                    usageHolder[0] = chunk.usage();
                }
                if (StringUtils.hasText(chunk.delta())) {
                    answer.append(chunk.delta());
                    emit(emitter, ExecutionEvent.messageDelta(runId, conversationId, chunk.delta()));
                }
            });

            long latencyMs = System.currentTimeMillis() - start;
            ModelResponse response = new ModelResponse(answer.toString(), "stop", usageHolder[0],
                    latencyMs, adapter.provider(), adapter.model());
            conversationService.saveAssistantMessage(conversationId, runId, response);
            conversationService.touch(conversationId, firstUserMessage);

            emit(emitter, ExecutionEvent.runCompleted(runId, conversationId, answer.length(),
                    latencyMs, usageHolder[0] == null ? null : usageHolder[0].totalTokens(),
                    adapter.provider()));
            emitter.complete();
        } catch (Exception e) {
            
            log.error("流式执行失败 runId={}", runId, e);
            try {
                emit(emitter, ExecutionEvent.runFailed(runId, conversationId, e.getMessage()));
                emitter.complete();
            } catch (Exception sendFailure) {
                emitter.completeWithError(e);
            }
        }
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

    private List<ChatMessage> buildMessages(String conversationId) {
        
        return memoryManager.buildContext(
                MemoryScope.conversation(conversationId), SYSTEM_PROMPT, null);
    }

    private ModelAdapter resolveAdapter(String provider) {
        return StringUtils.hasText(provider) ? registry.get(provider.trim()) : registry.current();
    }

    private String newRunId() {
        return "run-" + UUID.randomUUID();
    }
}
