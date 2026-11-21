package com.example.agentforge.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class ExecutionEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void eventsShouldHaveDistinctIdsAndStableContractFields() {
        ExecutionEvent first = ExecutionEvent.runStarted("run-1", "conv-1", "deepseek", "deepseek-chat");
        ExecutionEvent second = ExecutionEvent.runStarted("run-1", "conv-1", "deepseek", "deepseek-chat");

        assertNotEquals(first.eventId(), second.eventId());
        assertEquals(ExecutionEvent.RUN_STARTED, first.event());
        assertEquals("run-1", first.runId());
        assertEquals("conv-1", first.conversationId());
        assertEquals("INFO", first.level());
        assertEquals("deepseek", first.payload().get("provider"));
        assertTrue(first.timestamp() > 0);
    }

    @Test
    void jsonShouldUseEventFieldAndOmitNullValues() throws Exception {
        ExecutionEvent event = ExecutionEvent.messageDelta("run-1", "conv-1", "增量文本");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(event));

        assertEquals(ExecutionEvent.MESSAGE_DELTA, node.path("event").asText());
        assertEquals("增量文本", node.path("payload").path("delta").asText());
        assertTrue(node.path("agentId").isMissingNode(), "null 字段不应出现在 SSE 数据中");
        assertTrue(node.path("nodeId").isMissingNode());
    }

    @Test
    void runFailedShouldCarryErrorLevelAndPayload() {
        ExecutionEvent event = ExecutionEvent.runFailed("run-1", "conv-1", "超时");

        assertEquals(ExecutionEvent.RUN_FAILED, event.event());
        assertEquals("ERROR", event.level());
        assertEquals("超时", event.payload().get("error"));
    }
}
