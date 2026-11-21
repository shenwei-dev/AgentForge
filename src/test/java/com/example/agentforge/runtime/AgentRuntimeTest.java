package com.example.agentforge.runtime;

import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelException;
import com.example.agentforge.model.ModelRequest;
import com.example.agentforge.model.ModelResponse;
import com.example.agentforge.model.TokenUsage;
import com.example.agentforge.tool.ToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class AgentRuntimeTest {

    private final AgentRuntime runtime = new AgentRuntime(
            new AgentDecisionParser(new ObjectMapper()), new LoopGuard());
    private final Agent agent = DefaultAgent.general(Set.of("echo"));

    @Test
    void shouldFinishOnFirstRoundWhenModelReturnsFinal() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response(finalJson("答案")));
        List<ExecutionEvent> events = new ArrayList<>();

        AgentResult result = runtime.run(agent, context(limits(8, 60_000, 100_000, 2, 2, 2)),
                adapter, TestTools.echo(), events::add);

        assertEquals(AgentState.SUCCEEDED, result.state());
        assertEquals(TerminationReason.FINAL_ANSWER, result.terminationReason());
        assertEquals("答案", result.finalAnswer());
        assertEquals(1, result.rounds());
        assertTrue(events.stream().anyMatch(e -> e.event().equals(ExecutionEvent.AGENT_STARTED)));
        assertTrue(events.stream().anyMatch(e -> e.event().equals(ExecutionEvent.RUN_COMPLETED)));
    }

    @Test
    void shouldFeedToolResultIntoNextRound() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(
                response(toolJson("echo", "hello")),
                response(finalJson("回显结果: hello")));
        List<ExecutionEvent> events = new ArrayList<>();

        AgentResult result = runtime.run(agent, context(limits(8, 60_000, 100_000, 2, 2, 2)),
                adapter, TestTools.echo(), events::add);

        assertEquals(AgentState.SUCCEEDED, result.state());
        assertEquals(2, result.rounds());
        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(adapter, times(2)).chat(captor.capture());
        List<ChatMessage> secondRound = captor.getAllValues().get(1).messages();
        assertTrue(secondRound.stream().anyMatch(m -> m.content().contains("工具 echo 返回：hello")));
        assertTrue(events.stream().anyMatch(e -> e.event().equals(ExecutionEvent.TOOL_CALL_COMPLETED)));
    }

    @Test
    void shouldStopAtMaxRounds() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response(toolJson("echo", "x")));

        AgentResult result = runtime.run(agent, context(limits(3, 60_000, 100_000, 2, 10, 2)),
                adapter, TestTools.echo(), event -> { });

        assertEquals(TerminationReason.MAX_ROUNDS, result.terminationReason());
        assertEquals(AgentState.FAILED, result.state());
        assertEquals(3, result.rounds());
    }

    @Test
    void shouldStopWhenOverallDeadlineExceeded() {
        MutableClock clock = new MutableClock(1_000L);
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class))).thenAnswer(invocation -> {
            clock.advance(50);
            return response(toolJson("echo", "x"));
        });
        AgentContext ctx = new AgentContext("run-1", "conv-1", "general", clock,
                limits(8, 10, 100_000, 2, 10, 2), initialMessages());

        AgentResult result = runtime.run(agent, ctx, adapter, TestTools.echo(), event -> { });

        assertEquals(TerminationReason.TIMEOUT, result.terminationReason());
        assertEquals(AgentState.TIMED_OUT, result.state());
        assertEquals(1, result.rounds());
    }

    @Test
    void shouldStopWhenTokenBudgetExceeded() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(new ModelResponse(
                toolJson("echo", "x"), "stop", new TokenUsage(100, 100, 200),
                5L, "stub", "stub-model"));

        AgentResult result = runtime.run(agent, context(limits(8, 60_000, 100, 2, 10, 2)),
                adapter, TestTools.echo(), event -> { });

        assertEquals(TerminationReason.TOKEN_BUDGET_EXCEEDED, result.terminationReason());
        assertEquals(AgentState.BUDGET_EXCEEDED, result.state());
        assertEquals(200, result.totalTokens());
    }

    @Test
    void shouldStopWhenCancelledBeforeFirstRound() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        AgentContext ctx = context(limits(8, 60_000, 100_000, 2, 2, 2));
        ctx.requestCancel();

        AgentResult result = runtime.run(agent, ctx, adapter, TestTools.echo(), event -> { });

        assertEquals(TerminationReason.CANCELLED, result.terminationReason());
        assertEquals(AgentState.CANCELLED, result.state());
        assertEquals(0, result.rounds());
        verify(adapter, never()).chat(any(ModelRequest.class));
    }

    @Test
    void shouldStopWhenToolFailuresReachLimit() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response(toolJson("unknown", "x")));

        AgentResult result = runtime.run(agent, context(limits(8, 60_000, 100_000, 2, 2, 2)),
                adapter, TestTools.echo(), event -> { });

        assertEquals(TerminationReason.TOOL_RETRY_EXHAUSTED, result.terminationReason());
        assertEquals(2, result.rounds());
    }

    @Test
    void shouldRepairInvalidJsonByFeedingErrorBack() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(
                response("我觉得可以直接回答"),
                response(finalJson("修复后的答案")));

        AgentResult result = runtime.run(agent, context(limits(8, 60_000, 100_000, 2, 2, 2)),
                adapter, TestTools.echo(), event -> { });

        assertEquals(AgentState.SUCCEEDED, result.state());
        assertEquals(2, result.rounds());
        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(adapter, times(2)).chat(captor.capture());
        assertTrue(captor.getAllValues().get(1).messages().stream()
                .anyMatch(m -> m.content().contains("不是合法 JSON")));
    }

    @Test
    void shouldStopWhenParseRetriesExhausted() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response("永远不是 JSON"));

        AgentResult result = runtime.run(agent, context(limits(8, 60_000, 100_000, 2, 2, 0)),
                adapter, TestTools.echo(), event -> { });

        assertEquals(TerminationReason.PARSE_RETRY_EXHAUSTED, result.terminationReason());
        assertEquals(1, result.rounds());
    }

    @Test
    void shouldRetryRetryableModelException() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class)))
                .thenThrow(new ModelException("临时网络故障"))
                .thenReturn(response(finalJson("重试后成功")));

        AgentResult result = runtime.run(agent, context(limits(8, 60_000, 100_000, 2, 2, 2)),
                adapter, TestTools.echo(), event -> { });

        assertEquals(AgentState.SUCCEEDED, result.state());
        assertEquals(2, result.rounds());
    }

    @Test
    void shouldNotRetryNonRetryableModelException() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class)))
                .thenThrow(new ModelException("鉴权失败", "stub", 401, "{}"));

        AgentResult result = runtime.run(agent, context(limits(8, 60_000, 100_000, 2, 2, 2)),
                adapter, TestTools.echo(), event -> { });

        assertEquals(TerminationReason.UNRECOVERABLE_ERROR, result.terminationReason());
        assertEquals(AgentState.FAILED, result.state());
    }

    @Test
    void toolExceptionShouldBecomeObservationInsteadOfCrashingRuntime() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        when(adapter.provider()).thenReturn("stub");
        when(adapter.chat(any(ModelRequest.class))).thenReturn(
                response(toolJson("echo", "x")),
                response(finalJson("工具不可用，直接回答")));
        ToolExecutor broken = mock(ToolExecutor.class);
        when(broken.availableTools()).thenReturn(Set.of("echo"));
        when(broken.execute(anyString(), anyMap())).thenThrow(new RuntimeException("工具炸了"));

        AgentResult result = runtime.run(agent, context(limits(8, 60_000, 100_000, 2, 2, 2)),
                adapter, broken, event -> { });

        assertEquals(AgentState.SUCCEEDED, result.state());
        assertEquals(2, result.rounds());
    }

    private AgentContext context(AgentContext.Limits limits) {
        return new AgentContext("run-1", "conv-1", "general", Clock.systemUTC(),
                limits, initialMessages());
    }

    private List<ChatMessage> initialMessages() {
        return List.of(ChatMessage.system(DefaultAgent.GENERAL_SYSTEM_PROMPT),
                ChatMessage.user("任务"));
    }

    private AgentContext.Limits limits(int maxRounds, long timeoutMs, long tokenBudget,
                                       int modelRetries, int toolRetries, int parseRetries) {
        return new AgentContext.Limits(maxRounds, timeoutMs, tokenBudget,
                modelRetries, toolRetries, parseRetries);
    }

    private ModelResponse response(String content) {
        return new ModelResponse(content, "stop", new TokenUsage(1, 1, 2),
                5L, "stub", "stub-model");
    }

    private String finalJson(String answer) {
        return "{\"thought\":\"完成\",\"decision\":\"final\",\"answer\":\"" + answer + "\"}";
    }

    private String toolJson(String name, String text) {
        return "{\"thought\":\"需要工具\",\"decision\":\"tool\",\"tool_name\":\"" + name
                + "\",\"tool_input\":{\"text\":\"" + text + "\"}}";
    }

     
    private static final class MutableClock extends Clock {

        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        private void advance(long delta) {
            millis += delta;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}
