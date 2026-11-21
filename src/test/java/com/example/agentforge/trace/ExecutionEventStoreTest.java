package com.example.agentforge.trace;

import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.trace.mapper.ExecutionEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class ExecutionEventStoreTest {

    private final ExecutionEventMapper mapper = mock(ExecutionEventMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutionEventStore store = new ExecutionEventStore(mapper, objectMapper);

    @Test
    void shouldPersistAllTraceFieldsAndStructuredPayload() {
        ExecutionEvent event = ExecutionEvent.planTaskStarted(
                "run-1", "conv-1", "A", "收集资料", 1, 3, "researcher");

        store.record(event);

        ArgumentCaptor<ExecutionEventEntity> captor =
                ArgumentCaptor.forClass(ExecutionEventEntity.class);
        verify(mapper).insert(captor.capture());
        ExecutionEventEntity entity = captor.getValue();
        assertEquals(event.eventId(), entity.getEventId());
        assertEquals("run-1", entity.getRunId());
        assertEquals("conv-1", entity.getConversationId());
        assertEquals(ExecutionEvent.PLAN_TASK_STARTED, entity.getEventType());
        assertEquals("INFO", entity.getLevel());
        assertNotNull(entity.getCreatedAt());
        assertTrue(entity.getPayloadJson().contains("taskId"));
        assertTrue(entity.getPayloadJson().contains("researcher"));
    }

    @Test
    void shouldPersistToolAndNodeLinkageFields() {
        ExecutionEvent event = new ExecutionEvent("evt-1",
                ExecutionEvent.TOOL_CALL_COMPLETED, "run-1", "conv-1",
                "researcher", "node-A", "tool-1", "INFO", "工具完成",
                Map.of("success", true), System.currentTimeMillis());

        store.record(event);

        ArgumentCaptor<ExecutionEventEntity> captor =
                ArgumentCaptor.forClass(ExecutionEventEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals("researcher", captor.getValue().getAgentId());
        assertEquals("node-A", captor.getValue().getNodeId());
        assertEquals("tool-1", captor.getValue().getToolCallId());
        assertTrue(captor.getValue().getPayloadJson().contains("success"));
    }

    @Test
    void shouldSkipHighFrequencyMessageDeltas() {
        store.record(ExecutionEvent.messageDelta("run-1", "conv-1", "逐字片段"));

        verify(mapper, org.mockito.Mockito.never()).insert(any(ExecutionEventEntity.class));
    }

    @Test
    void shouldNotBreakWhenDatabaseFails() {
        when(mapper.insert(any(ExecutionEventEntity.class)))
                .thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() -> store.record(
                ExecutionEvent.runStarted("run-1", "conv-1", "deepseek", "deepseek-chat")));
    }

    @Test
    void shouldRestoreStructuredEventsFromRows() {
        ExecutionEventEntity row = new ExecutionEventEntity();
        row.setEventId("evt-2");
        row.setEventType(ExecutionEvent.TOOL_CALL_COMPLETED);
        row.setRunId("run-1");
        row.setConversationId("conv-1");
        row.setLevel("INFO");
        row.setMessage("工具完成");
        row.setPayloadJson("{\"toolName\":\"calculator\",\"success\":true}");
        row.setCreatedAt(LocalDateTime.now());
        when(mapper.selectList(any())).thenReturn(List.of(row));

        List<ExecutionEvent> events = store.findByRun("run-1");

        assertEquals(1, events.size());
        assertEquals(ExecutionEvent.TOOL_CALL_COMPLETED, events.get(0).event());
        assertEquals("calculator", events.get(0).payload().get("toolName"));
        assertTrue(events.get(0).timestamp() > 0);
    }

    @Test
    void shouldTruncateOverlongMessage() {
        ExecutionEvent event = new ExecutionEvent("evt-3", ExecutionEvent.RUN_FAILED,
                "run-1", "conv-1", null, null, null, "ERROR",
                "x".repeat(2000), Map.of(), System.currentTimeMillis());

        store.record(event);

        ArgumentCaptor<ExecutionEventEntity> captor =
                ArgumentCaptor.forClass(ExecutionEventEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1024, captor.getValue().getMessage().length());
    }
}
