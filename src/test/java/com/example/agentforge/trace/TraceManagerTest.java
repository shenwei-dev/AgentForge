package com.example.agentforge.trace;

import com.example.agentforge.common.ExecutionEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class TraceManagerTest {

    private final ExecutionEventStore store = mock(ExecutionEventStore.class);
    private final TraceManager manager = new TraceManager(store);

    @Test
    void sinkShouldRecordAndForwardSameEvent() {
        ExecutionEvent event = ExecutionEvent.runStarted(
                "run-1", "conv-1", "deepseek", "deepseek-chat");
        List<ExecutionEvent> received = new ArrayList<>();

        manager.sink(received::add).accept(event);

        verify(store).record(event);
        assertEquals(1, received.size());
        assertEquals(event.eventId(), received.get(0).eventId());
    }

    @Test
    void runsByConversationShouldAggregateByRunId() {
        when(store.findByConversation("conv-1")).thenReturn(List.of(
                event("run-1", ExecutionEvent.RUN_STARTED, 1000L),
                event("run-1", ExecutionEvent.RUN_COMPLETED, 2000L),
                event("run-2", ExecutionEvent.RUN_STARTED, 3000L)));

        List<RunSummary> summaries = manager.runsByConversation("conv-1");

        assertEquals(2, summaries.size());
        assertEquals("run-2", summaries.get(0).runId());
        assertEquals(2, summaries.get(1).eventCount());
        assertEquals(1000L, summaries.get(1).firstEventAt());
        assertEquals(2000L, summaries.get(1).lastEventAt());
        assertEquals(ExecutionEvent.RUN_COMPLETED, summaries.get(1).lastEventType());
    }

    @Test
    void shouldIgnoreEventsWithoutRunId() {
        when(store.findByConversation("conv-1")).thenReturn(List.of(
                event("run-1", ExecutionEvent.RUN_STARTED, 1000L),
                new ExecutionEvent(UUID.randomUUID().toString(), "internal", null,
                        "conv-1", null, null, null, "INFO", null, Map.of(), 1500L)));

        assertEquals(1, manager.runsByConversation("conv-1").size());
    }

    private ExecutionEvent event(String runId, String type, long timestamp) {
        return new ExecutionEvent(UUID.randomUUID().toString(), type, runId,
                "conv-1", null, null, null, "INFO", null, Map.of(), timestamp);
    }
}
