package com.example.agentforge.runtime;

import com.example.agentforge.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class AgentRunRegistryTest {

    private final AgentRunRegistry registry = new AgentRunRegistry();

    @Test
    void registerFindAndUnregister() {
        AgentContext context = context("run-1");

        registry.register(context);
        assertTrue(registry.find("run-1").isPresent());
        assertEquals("RUNNING", registry.snapshot().get("run-1"));

        registry.unregister("run-1");
        assertFalse(registry.find("run-1").isPresent());
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    void cancelShouldSetCooperativeFlagAndBeIdempotent() {
        AgentContext context = context("run-2");
        registry.register(context);

        assertTrue(registry.cancel("run-2"));
        assertTrue(context.isCancelled());
        assertTrue(registry.cancel("run-2"), "重复取消应视为已标记取消");
    }

    @Test
    void cancelUnknownRunShouldReturnFalse() {
        assertFalse(registry.cancel("not-exist"));
    }

    private AgentContext context(String runId) {
        AgentContext context = new AgentContext(runId, "conv-1", "general", Clock.systemUTC(),
                new AgentContext.Limits(8, 60_000, 100_000, 2, 2, 2),
                List.of(ChatMessage.user("任务")));
        context.setState(AgentState.RUNNING);
        return context;
    }
}
