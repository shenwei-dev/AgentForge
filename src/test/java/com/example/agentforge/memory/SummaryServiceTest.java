package com.example.agentforge.memory;

import com.example.agentforge.config.MemoryProperties;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.model.ModelException;
import com.example.agentforge.model.ModelRequest;
import com.example.agentforge.model.ModelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class SummaryServiceTest {

    private final ModelAdapterRegistry registry = mock(ModelAdapterRegistry.class);
    private final ModelAdapter adapter = mock(ModelAdapter.class);
    private final MemoryProperties properties = new MemoryProperties();
    private final SummaryService service = new SummaryService(registry, properties);

    @BeforeEach
    void setUp() {
        when(registry.current()).thenReturn(adapter);
    }

    @Test
    void shouldMergePreviousSummaryAndDroppedMessages() {
        when(adapter.chat(any(ModelRequest.class)))
                .thenReturn(response("合并后的摘要"));

        String result = service.summarize("旧摘要", List.of(
                com.example.agentforge.model.ChatMessage.user("消息1"),
                com.example.agentforge.model.ChatMessage.assistant("回答1")));

        assertEquals("合并后的摘要", result);
        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(adapter).chat(captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertEquals(true, prompt.contains("旧摘要"));
        assertEquals(true, prompt.contains("消息1"));
    }

    @Test
    void shouldReturnPreviousWhenNothingDropped() {
        assertEquals("旧摘要", service.summarize("旧摘要", List.of()));
        assertEquals("旧摘要", service.summarize("旧摘要", null));
    }

    @Test
    void shouldReturnPreviousWhenModelFails() {
        when(adapter.chat(any(ModelRequest.class)))
                .thenThrow(new ModelException("模型不可用"));

        assertEquals("旧摘要", service.summarize("旧摘要", List.of(
                com.example.agentforge.model.ChatMessage.user("消息1"))));
    }

    @Test
    void shouldTrimOverlongSummary() {
        properties.setSummaryMaxChars(5);
        when(adapter.chat(any(ModelRequest.class)))
                .thenReturn(response("1234567890"));

        assertEquals("12345", service.summarize(null, List.of(
                com.example.agentforge.model.ChatMessage.user("消息1"))));
    }

    private ModelResponse response(String content) {
        return new ModelResponse(content, "stop", null, 5L, "stub", "stub-model");
    }
}
