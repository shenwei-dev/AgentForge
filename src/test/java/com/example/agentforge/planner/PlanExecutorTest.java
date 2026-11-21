package com.example.agentforge.planner;

import com.example.agentforge.agent.AgentExecutionTracker;
import com.example.agentforge.agent.AgentManager;
import com.example.agentforge.agent.AgentProfile;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.config.PlannerProperties;
import com.example.agentforge.memory.MemoryManager;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.planner.dto.PlanExecutionResult;
import com.example.agentforge.planner.dto.PlanTask;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class PlanExecutorTest {

    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final ToolExecutor toolExecutor = mock(ToolExecutor.class);
    private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    private final AgentManager agentManager = mock(AgentManager.class);
    private final ModelAdapterRegistry modelRegistry = mock(ModelAdapterRegistry.class);
    private final AgentExecutionTracker tracker = new AgentExecutionTracker();
    private final ResultIntegrator integrator = mock(ResultIntegrator.class);
    private final MemoryManager memoryManager = mock(MemoryManager.class);
    private final ModelAdapter adapter = mock(ModelAdapter.class);

    private final AgentProfile generalProfile = new AgentProfile("general", "通用", "GENERAL",
            "通用提示词", Set.of(), null, null, 8, 120_000L, true);
    private final Agent generalAgent = new DefaultAgent("general", "通用系统提示", Set.of());

    private final PlanExecutor executor = new PlanExecutor(runtime,
            new PlanValidator(new PlannerProperties()), toolExecutor, toolRegistry,
            agentManager, modelRegistry, tracker, integrator, memoryManager);

    @BeforeEach
    void setUp() {
        when(toolExecutor.availableTools()).thenReturn(Set.of());
        when(toolRegistry.buildToolsPrompt()).thenReturn("");
        when(agentManager.get("general")).thenReturn(generalProfile);
        when(agentManager.find(anyString())).thenReturn(Optional.of(generalProfile));
        when(agentManager.createAgent(anyString(), anyString())).thenReturn(generalAgent);
        when(agentManager.limitsFor(any()))
                .thenReturn(new AgentContext.Limits(8, 60_000, 100_000, 2, 2, 2));
        when(integrator.integrate(any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(null);
        when(memoryManager.buildContext(any(), anyString(), anyString())).thenAnswer(invocation ->
                List.of(ChatMessage.system(invocation.getArgument(1)),
                        ChatMessage.user(invocation.getArgument(2))));
    }

    @Test
    void shouldExecuteInDependencyOrderAndPassUpstreamResults() {
        TaskPlan plan = new TaskPlan("目标", List.of(
                task("C", null, "B"), task("B", null, "A"), task("A", null)));
        when(runtime.run(any(), any(), any(), any(), any()))
                .thenReturn(success("答案A"), success("答案B"), success("答案C"));
        List<ExecutionEvent> events = new ArrayList<>();

        PlanExecutionResult result = executor.execute(plan, adapter, "run-1", "conv-1", events::add);

        assertEquals(3, result.totalTasks());
        assertEquals(3, result.succeededTasks());
        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(runtime, times(3)).run(any(), captor.capture(), any(), any(), any());
        List<AgentContext> contexts = captor.getAllValues();
        assertTrue(userText(contexts.get(0)).contains("[A]"));
        assertTrue(userText(contexts.get(1)).contains("答案A"));
        assertTrue(userText(contexts.get(2)).contains("答案B"));
        assertTrue(events.stream().anyMatch(e -> e.event().equals(ExecutionEvent.PLAN_TASK_STARTED)));
        assertTrue(events.stream().anyMatch(e -> e.event().equals(ExecutionEvent.PLAN_TASK_COMPLETED)));
    }

    @Test
    void shouldDelegateTaskToConfiguredRole() {
        TaskPlan plan = new TaskPlan("目标", List.of(task("A", "analyst")));
        AgentProfile analyst = new AgentProfile("analyst", "分析", "ANALYST",
                "分析提示词", Set.of("calculator"), null, null, 4, 30_000L, true);
        when(agentManager.find("analyst")).thenReturn(Optional.of(analyst));
        when(agentManager.createAgent(eq("analyst"), anyString()))
                .thenReturn(new DefaultAgent("analyst", "分析系统提示", Set.of("calculator")));
        when(runtime.run(any(), any(), any(), any(), any())).thenReturn(success("分析结论"));
        List<ExecutionEvent> events = new ArrayList<>();

        executor.execute(plan, adapter, "run-1", "conv-1", events::add);

        verify(agentManager).createAgent(eq("analyst"), anyString());
        ExecutionEvent started = events.stream()
                .filter(e -> e.event().equals(ExecutionEvent.PLAN_TASK_STARTED))
                .findFirst().orElseThrow();
        assertEquals("analyst", started.payload().get("agentRole"));
    }

    @Test
    void shouldUseWriterIntegrationWhenAvailable() {
        TaskPlan plan = new TaskPlan("目标", List.of(task("A", null)));
        when(runtime.run(any(), any(), any(), any(), any())).thenReturn(success("答案A"));
        when(integrator.integrate(any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn("## 整合后的最终报告");

        PlanExecutionResult result = executor.execute(plan, adapter, "run-1", "conv-1", event -> { });

        assertEquals("## 整合后的最终报告", result.summary());
        verify(integrator).integrate(any(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    void shouldTrackAgentExecutionStates() {
        TaskPlan plan = new TaskPlan("目标", List.of(task("A", null), task("B", null, "A")));
        when(runtime.run(any(), any(), any(), any(), any()))
                .thenReturn(success("答案A"), success("答案B"));

        PlanExecutionResult result = executor.execute(plan, adapter, "run-1", "conv-1", event -> { });

        assertEquals(2, result.agentStatuses().size());
        assertTrue(result.agentStatuses().stream()
                .allMatch(s -> "SUCCEEDED".equals(s.state())));
    }

    @Test
    void shouldCountFailuresAndNotPassFailedResultDownstream() {
        TaskPlan plan = new TaskPlan("目标", List.of(task("A", null), task("B", null, "A")));
        when(runtime.run(any(), any(), any(), any(), any()))
                .thenReturn(failure(), success("答案B"));

        PlanExecutionResult result = executor.execute(plan, adapter, "run-1", "conv-1", event -> { });

        assertEquals(1, result.succeededTasks());
        assertEquals(false, result.isSuccess());
        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(runtime, times(2)).run(any(), captor.capture(), any(), any(), any());
        assertTrue(!userText(captor.getAllValues().get(1)).contains("答案A"));
    }

    @Test
    void shouldFilterSubRunCompletionEvents() {
        TaskPlan plan = new TaskPlan("目标", List.of(task("A", null)));
        when(runtime.run(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Consumer<ExecutionEvent> sink = invocation.getArgument(4);
            sink.accept(ExecutionEvent.messageDelta("run-1", "conv-1", "片段"));
            sink.accept(ExecutionEvent.runCompleted("run-1", "conv-1", 2, 1L, null, null));
            return success("答案A");
        });
        List<ExecutionEvent> events = new ArrayList<>();

        executor.execute(plan, adapter, "run-1", "conv-1", events::add);

        assertTrue(events.stream().noneMatch(e -> e.event().equals(ExecutionEvent.MESSAGE_DELTA)));
        assertTrue(events.stream().noneMatch(e -> e.event().equals(ExecutionEvent.RUN_COMPLETED)));
    }

    private String userText(AgentContext context) {
        return context.getMessages().stream()
                .filter(m -> "user".equals(m.role()))
                .map(ChatMessage::content)
                .reduce("", String::concat);
    }

    private PlanTask task(String id, String role, String... dependsOn) {
        return new PlanTask(id, "任务 " + id, "描述 " + id, List.of(dependsOn), role);
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
