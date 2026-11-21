package com.example.agentforge.memory;

import com.example.agentforge.chat.service.ConversationService;
import com.example.agentforge.config.MemoryProperties;
import com.example.agentforge.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class DefaultMemoryManagerTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final SummaryService summaryService = mock(SummaryService.class);
    private final MemoryProperties properties = new MemoryProperties();
    private DefaultMemoryManager manager;

    @BeforeEach
    void setUp() {
        properties.setCharBudget(10_000);
        properties.setSummaryThreshold(2);
        manager = new DefaultMemoryManager(conversationService, summaryService, properties);
        when(conversationService.findLatestSummary(anyString())).thenReturn(null);
        when(conversationService.loadAllHistory(anyString())).thenReturn(List.of());
    }

    @Test
    void shouldIncludeSystemPromptHistoryAndCurrentInput() {
        when(conversationService.loadAllHistory("c1")).thenReturn(List.of(
                ChatMessage.user("旧问题"), ChatMessage.assistant("旧答案")));

        List<ChatMessage> context = manager.buildContext(
                MemoryScope.conversation("c1"), "系统提示", "新问题");

        assertEquals("system", context.get(0).role());
        assertEquals("系统提示", context.get(0).content());
        assertTrue(context.stream().anyMatch(m -> m.content().equals("旧问题")));
        assertEquals("新问题", context.get(context.size() - 1).content());
    }

    @Test
    void shouldNotDuplicateCurrentInputAlreadyInHistory() {
        when(conversationService.loadAllHistory("c1"))
                .thenReturn(List.of(ChatMessage.user("同一个问题")));

        List<ChatMessage> context = manager.buildContext(
                MemoryScope.conversation("c1"), "系统", "同一个问题");

        assertEquals(1, context.stream()
                .filter(m -> m.content().equals("同一个问题")).count());
    }

    @Test
    void shouldCompactOldMessagesAndPersistSummary() {
        properties.setCharBudget(120);
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            history.add(ChatMessage.user("较早的问题 " + i + " 一些补充内容占位文本"));
            history.add(ChatMessage.assistant("较早的回答 " + i + " 一些补充内容占位文本"));
        }
        when(conversationService.loadAllHistory("c1")).thenReturn(history);
        when(summaryService.summarize(isNull(), anyList())).thenReturn("合并后的要点摘要");

        List<ChatMessage> context = manager.buildContext(
                MemoryScope.conversation("c1"), "系统", "当前问题");

        verify(conversationService).saveSummaryMessage(eq("c1"), anyString(), eq("合并后的要点摘要"));
        assertTrue(context.stream().anyMatch(m -> m.content().contains("合并后的要点摘要")));
        assertTrue(manager.stats("c1").compactedCount() > 0);
        assertTrue(manager.stats("c1").summaryPresent());
    }

    @Test
    void shouldDegradeWhenSummaryFails() {
        properties.setCharBudget(100);
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            history.add(ChatMessage.user("旧消息 " + i + " 占位占位占位占位占位占位"));
        }
        when(conversationService.loadAllHistory("c1")).thenReturn(history);
        when(summaryService.summarize(isNull(), anyList())).thenReturn(null);

        List<ChatMessage> context = manager.buildContext(
                MemoryScope.conversation("c1"), "系统", null);

        verify(conversationService, never())
                .saveSummaryMessage(anyString(), anyString(), anyString());
        assertTrue(context.stream().anyMatch(m -> m.content().equals("系统")));
    }

    @Test
    void shouldInjectExistingSummary() {
        when(conversationService.findLatestSummary("c1")).thenReturn("已有历史摘要");

        List<ChatMessage> context = manager.buildContext(
                MemoryScope.conversation("c1"), "系统", null);

        assertTrue(context.stream().anyMatch(m -> m.content().contains("已有历史摘要")));
    }

    @Test
    void shouldIsolateWorkingMemoryByAgent() {
        MemoryScope researcher = MemoryScope.agent("c1", "researcher");
        MemoryScope writer = MemoryScope.agent("c1", "writer");
        manager.appendWorkingMemory(researcher, "assistant", "研究员的私有结论");

        List<ChatMessage> writerContext = manager.buildContext(writer, "系统", null);
        List<ChatMessage> researcherContext = manager.buildContext(researcher, "系统", null);

        assertTrue(writerContext.stream()
                .noneMatch(m -> m.content().contains("私有结论")));
        assertTrue(researcherContext.stream()
                .anyMatch(m -> m.content().contains("私有结论")));
    }

    @Test
    void shouldReuseTaskResultsAcrossTurns() {
        manager.putTaskResult("c1", "A", "任务 A 的结论");
        manager.putTaskResult("c1", "B", "任务 B 的结论");

        List<ChatMessage> context = manager.buildContext(
                MemoryScope.conversation("c1"), "系统", "基于上面的结论继续");

        assertTrue(context.stream().anyMatch(m -> m.content().contains("任务 A 的结论")));
        assertEquals(2, manager.stats("c1").reusableResults());
        assertEquals(2, manager.taskResults("c1").size());
    }

    @Test
    void clearShouldRemoveOnlyTargetScope() {
        MemoryScope researcher = MemoryScope.agent("c1", "researcher");
        MemoryScope writer = MemoryScope.agent("c1", "writer");
        manager.appendWorkingMemory(researcher, "assistant", "研究结论");
        manager.appendWorkingMemory(writer, "assistant", "写作结论");

        manager.clear(researcher);

        assertTrue(manager.buildContext(researcher, "系统", null).stream()
                .noneMatch(m -> m.content().contains("研究结论")));
        assertTrue(manager.buildContext(writer, "系统", null).stream()
                .anyMatch(m -> m.content().contains("写作结论")));
    }

    @Test
    void statsShouldReflectWindowUsage() {
        when(conversationService.loadAllHistory("c1")).thenReturn(List.of(
                ChatMessage.user("一"), ChatMessage.assistant("二")));

        manager.buildContext(MemoryScope.conversation("c1"), "系统", null);

        MemoryStats stats = manager.stats("c1");
        assertEquals(2, stats.historyCount());
        assertEquals(2, stats.retainedCount());
        assertTrue(stats.contextChars() > 0);
    }
}
