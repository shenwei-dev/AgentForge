package com.example.agentforge.planner;

import com.example.agentforge.agent.AgentManager;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.config.PlannerProperties;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelRequest;
import com.example.agentforge.model.ModelResponse;
import com.example.agentforge.model.TokenUsage;
import com.example.agentforge.planner.dto.TaskPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class PlannerTest {

    private static final String VALID_PLAN = """
            {"goal":"对比两个框架","tasks":[
              {"id":"A","title":"收集资料","description":"收集资料","depends_on":[],"agent_role":"general"},
              {"id":"B","title":"写报告","description":"输出对比报告","depends_on":["A"],"agent_role":"general"}
            ]}
            """;

    private static final String ROLE_AWARE_PLAN = """
            {"goal":"对比两个框架","tasks":[
              {"id":"A","title":"收集资料","description":"收集资料","depends_on":[],"agent_role":"researcher"},
              {"id":"B","title":"写报告","description":"输出对比报告","depends_on":["A"],"agent_role":"writer"}
            ]}
            """;

    private final PlannerProperties properties = new PlannerProperties();
    private final AgentManager agentManager = mock(AgentManager.class);
    private final Planner planner = new Planner(new PlanParser(new ObjectMapper()),
            new PlanValidator(properties), properties, agentManager);
    private final ModelAdapter adapter = mock(ModelAdapter.class);

    @BeforeEach
    void setUp() {
        when(agentManager.assignableRoleIds()).thenReturn(
                Set.of("general", "planner", "researcher", "analyst", "reviewer", "writer"));
        when(agentManager.buildRoleDescriptions())
                .thenReturn("- researcher: 资料研究\n- writer: 报告生成\n");
    }

    @Test
    void shouldReturnPlanAndEmitPlanCreatedEvent() {
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response(VALID_PLAN));
        List<ExecutionEvent> events = new ArrayList<>();

        TaskPlan plan = planner.createPlan("对比两个框架", adapter, "run-1", "conv-1", events::add);

        assertEquals(2, plan.tasks().size());
        assertEquals("对比两个框架", plan.goal());
        ExecutionEvent created = events.stream()
                .filter(e -> e.event().equals(ExecutionEvent.PLAN_CREATED))
                .findFirst().orElseThrow();
        assertEquals(2, created.payload().get("taskCount"));
    }

    @Test
    void shouldDelegateTasksToRegisteredRoles() {
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response(ROLE_AWARE_PLAN));
        List<ExecutionEvent> events = new ArrayList<>();

        TaskPlan plan = planner.createPlan("对比两个框架", adapter, "run-1", "conv-1", events::add);

        assertEquals("researcher", plan.tasks().get(0).agentRole());
        assertEquals("writer", plan.tasks().get(1).agentRole());
        ExecutionEvent created = events.stream()
                .filter(e -> e.event().equals(ExecutionEvent.PLAN_CREATED))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> views = (List<Map<String, Object>>) created.payload().get("tasks");
        assertEquals("researcher", views.get(0).get("agentRole"));
    }

    @Test
    void shouldInjectRoleDescriptionsIntoSystemPrompt() {
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response(VALID_PLAN));

        planner.createPlan("目标", adapter, "run-1", "conv-1", event -> { });

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(adapter).chat(captor.capture());
        assertTrue(captor.getValue().messages().get(0).content().contains("researcher"));
    }

    @Test
    void shouldRepairInvalidOutputByFeedingErrorsBack() {
        when(adapter.chat(any(ModelRequest.class))).thenReturn(
                response("我觉得应该分三步"), response(VALID_PLAN));

        TaskPlan plan = planner.createPlan("对比两个框架", adapter, "run-1", "conv-1", event -> { });

        assertEquals(2, plan.tasks().size());
        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(adapter, times(2)).chat(captor.capture());
        assertTrue(captor.getAllValues().get(1).messages().stream()
                .anyMatch(m -> m.content().contains("不合法")));
    }

    @Test
    void shouldRepairPlanWithCycle() {
        String cyclic = """
                {"goal":"目标","tasks":[
                  {"id":"A","title":"A","description":"A","depends_on":["B"],"agent_role":"general"},
                  {"id":"B","title":"B","description":"B","depends_on":["A"],"agent_role":"general"}
                ]}
                """;
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response(cyclic), response(VALID_PLAN));

        TaskPlan plan = planner.createPlan("目标", adapter, "run-1", "conv-1", event -> { });

        assertEquals(2, plan.tasks().size());
        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(adapter, times(2)).chat(captor.capture());
        assertTrue(captor.getAllValues().get(1).messages().stream()
                .anyMatch(m -> m.content().contains("存在环")));
    }

    @Test
    void shouldRepairUnknownAgentRole() {
        String unknownRole = """
                {"goal":"目标","tasks":[
                  {"id":"A","title":"收集","description":"收集","depends_on":[],"agent_role":"hacker"}
                ]}
                """;
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response(unknownRole), response(VALID_PLAN));

        planner.createPlan("目标", adapter, "run-1", "conv-1", event -> { });

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(adapter, times(2)).chat(captor.capture());
        assertTrue(captor.getAllValues().get(1).messages().stream()
                .anyMatch(m -> m.content().contains("未注册的 Agent 角色")));
    }

    @Test
    void shouldThrowAfterRepairAttemptsExhausted() {
        when(adapter.chat(any(ModelRequest.class))).thenReturn(response("永远不是 JSON"));

        assertThrows(PlanningException.class,
                () -> planner.createPlan("目标", adapter, "run-1", "conv-1", event -> { }));
        verify(adapter, times(3)).chat(any(ModelRequest.class));
    }

    private ModelResponse response(String content) {
        return new ModelResponse(content, "stop", new TokenUsage(1, 1, 2),
                5L, "stub", "stub-model");
    }
}
