package com.example.agentforge.planner;

import com.example.agentforge.agent.AgentManager;
import com.example.agentforge.agent.AgentProfile;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.planner.dto.AgentOutput;
import com.example.agentforge.planner.dto.TaskPlan;
import com.example.agentforge.runtime.Agent;
import com.example.agentforge.runtime.AgentContext;
import com.example.agentforge.runtime.AgentResult;
import com.example.agentforge.runtime.AgentRuntime;
import com.example.agentforge.runtime.AgentState;
import com.example.agentforge.runtime.DefaultAgent;
import com.example.agentforge.runtime.TerminationReason;
import com.example.agentforge.tool.ToolExecutor;
import com.example.agentforge.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class ResultIntegratorTest {

    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final AgentManager manager = mock(AgentManager.class);
    private final ToolExecutor toolExecutor = mock(ToolExecutor.class);
    private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    private final ModelAdapter adapter = mock(ModelAdapter.class);

    private final ResultIntegrator integrator =
            new ResultIntegrator(runtime, manager, toolExecutor, toolRegistry);

    private final AgentProfile writer = new AgentProfile("writer", "写作", "WRITER",
            "写作提示词", Set.of("echo"), null, null, 8, 120_000L, true);

    @BeforeEach
    void setUp() {
        when(toolRegistry.buildToolsPrompt()).thenReturn("");
        when(manager.find("writer")).thenReturn(Optional.of(writer));
        when(manager.createAgent(eq("writer"), anyString()))
                .thenReturn(new DefaultAgent("writer", "写作系统提示", Set.of("echo")));
        when(manager.limitsFor(any()))
                .thenReturn(new AgentContext.Limits(8, 60_000, 100_000, 2, 2, 2));
    }

    @Test
    void shouldReturnWriterReportWhenRuntimeSucceeds() {
        when(runtime.run(any(), any(), any(), any(), any())).thenReturn(success("## 最终报告"));

        String result = integrator.integrate(plan(), outputs(), adapter,
                "run-1", "conv-1", event -> { });

        assertEquals("## 最终报告", result);
    }

    @Test
    void shouldReturnNullWhenRuntimeFails() {
        when(runtime.run(any(), any(), any(), any(), any())).thenReturn(failure());

        assertNull(integrator.integrate(plan(), outputs(), adapter,
                "run-1", "conv-1", event -> { }));
    }

    @Test
    void shouldReturnNullWhenRoleLookupThrows() {
        when(manager.find("writer")).thenThrow(new RuntimeException("角色服务不可用"));

        assertNull(integrator.integrate(plan(), outputs(), adapter,
                "run-1", "conv-1", event -> { }));
    }

    @Test
    void shouldPassAllAgentOutputsIntoWriterContext() {
        when(runtime.run(any(), any(), any(), any(), any())).thenReturn(success("报告"));

        integrator.integrate(plan(), outputs(), adapter, "run-1", "conv-1", event -> { });

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(runtime).run(any(), captor.capture(), any(), any(), any());
        String text = captor.getValue().getMessages().stream()
                .map(ChatMessage::content)
                .reduce("", String::concat);
        assertTrue(text.contains("RESEARCHER"));
        assertTrue(text.contains("ANALYST"));
        assertTrue(text.contains("资料摘要"));
    }

    private TaskPlan plan() {
        return new TaskPlan("对比框架", List.of());
    }

    private List<AgentOutput> outputs() {
        return List.of(
                new AgentOutput("A", "RESEARCHER", "资料摘要"),
                new AgentOutput("B", "ANALYST", "分析结论"));
    }

    private AgentResult success(String answer) {
        return new AgentResult("run-1", AgentState.SUCCEEDED, TerminationReason.FINAL_ANSWER,
                answer, 1, 5L, 10L, null);
    }

    private AgentResult failure() {
        return new AgentResult("run-1", AgentState.FAILED, TerminationReason.MAX_ROUNDS,
                null, 3, 5L, 10L, "轮数耗尽");
    }
}
