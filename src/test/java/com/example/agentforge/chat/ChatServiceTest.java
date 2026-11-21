package com.example.agentforge.chat.service;

import com.example.agentforge.chat.dto.ChatRequest;
import com.example.agentforge.chat.dto.MessageVO;
import com.example.agentforge.chat.entity.ChatMessageEntity;
import com.example.agentforge.chat.entity.Conversation;
import com.example.agentforge.memory.MemoryManager;
import com.example.agentforge.memory.MemoryScope;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.model.ModelRequest;
import com.example.agentforge.model.ModelResponse;
import com.example.agentforge.model.TokenUsage;
import com.example.agentforge.trace.TraceManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class ChatServiceTest {

    @Test
    void completeShouldSaveBothMessagesAndBuildSystemPromptPlusHistory() {
        ConversationService conversationService = mock(ConversationService.class);
        ModelAdapterRegistry registry = mock(ModelAdapterRegistry.class);
        MemoryManager memoryManager = mock(MemoryManager.class);
        TraceManager traceManager = mock(TraceManager.class);
        when(traceManager.sink()).thenReturn(event -> { });
        ModelAdapter adapter = mock(ModelAdapter.class);

        when(conversationService.getOrCreate(null)).thenReturn(conversation("c1"));
        when(memoryManager.buildContext(any(MemoryScope.class), anyString(), isNull()))
                .thenReturn(List.of(ChatMessage.system("系统"), ChatMessage.user("你好")));
        when(registry.current()).thenReturn(adapter);
        when(adapter.provider()).thenReturn("deepseek");
        when(adapter.model()).thenReturn("deepseek-chat");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(
                new ModelResponse("答案", "stop", new TokenUsage(5, 3, 8), 42L,
                        "deepseek", "deepseek-chat"));
        when(conversationService.saveAssistantMessage(eq("c1"), anyString(), any()))
                .thenReturn(assistantMessage("答案"));

        ChatService service = new ChatService(conversationService, registry,
                memoryManager, traceManager, new ThreadPoolTaskExecutor());
        MessageVO result = service.complete(new ChatRequest(null, "你好", null));

        assertEquals("答案", result.content());
        verify(conversationService).saveUserMessage(eq("c1"), anyString(), eq("你好"));
        verify(conversationService).touch("c1", "你好");
        verify(memoryManager).buildContext(any(MemoryScope.class), anyString(), isNull());

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(adapter).chat(captor.capture());
        List<ChatMessage> messages = captor.getValue().messages();
        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(1).role());
        assertEquals("你好", messages.get(1).content());
    }

    @Test
    void completeShouldHonorRequestedProvider() {
        ConversationService conversationService = mock(ConversationService.class);
        ModelAdapterRegistry registry = mock(ModelAdapterRegistry.class);
        MemoryManager memoryManager = mock(MemoryManager.class);
        TraceManager traceManager = mock(TraceManager.class);
        when(traceManager.sink()).thenReturn(event -> { });
        ModelAdapter adapter = mock(ModelAdapter.class);

        when(conversationService.getOrCreate("c1")).thenReturn(conversation("c1"));
        when(memoryManager.buildContext(any(MemoryScope.class), anyString(), isNull()))
                .thenReturn(List.of(ChatMessage.system("系统"), ChatMessage.user("测试")));
        when(registry.get("ollama")).thenReturn(adapter);
        when(adapter.provider()).thenReturn("ollama");
        when(adapter.model()).thenReturn("qwen2.5:7b");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(
                new ModelResponse("本地回答", "stop", null, 10L, "ollama", "qwen2.5:7b"));
        when(conversationService.saveAssistantMessage(eq("c1"), anyString(), any()))
                .thenReturn(assistantMessage("本地回答"));

        ChatService service = new ChatService(conversationService, registry,
                memoryManager, traceManager, new ThreadPoolTaskExecutor());
        service.complete(new ChatRequest("c1", "测试", "ollama"));

        verify(registry).get("ollama");
    }

    private Conversation conversation(String id) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        return conversation;
    }

    private ChatMessageEntity assistantMessage(String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId("m1");
        entity.setConversationId("c1");
        entity.setRole("ASSISTANT");
        entity.setContent(content);
        return entity;
    }
}
