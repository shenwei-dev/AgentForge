package com.example.agentforge.workflow;

import com.example.agentforge.workflow.model.DagGraph;
import com.example.agentforge.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class DagGraphTest {

    @Test
    void demoShouldProduceThreeParallelLevels() {
        DagGraph graph = DagGraph.of(WorkflowDefinition.demo());

        assertTrue(graph.validate().isEmpty());
        assertEquals(List.of(List.of("A", "B"), List.of("C"), List.of("D")), graph.levels());
        assertEquals(Set.of("A", "B"), graph.dependenciesOf("C"));
        assertEquals(Set.of("C"), graph.dependenciesOf("D"));
    }

    @Test
    void shouldDetectCycle() {
        WorkflowDefinition definition = definition(
                List.of(node("A"), node("B"), node("C")),
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "A")));

        DagGraph graph = DagGraph.of(definition);

        assertTrue(graph.hasCycle());
        assertTrue(graph.validate().stream().anyMatch(e -> e.contains("存在环")));
    }

    @Test
    void shouldRejectDuplicateNodeKeys() {
        WorkflowDefinition definition = definition(
                List.of(node("A"), node("A")), List.of());

        assertTrue(DagGraph.of(definition).validate().stream()
                .anyMatch(e -> e.contains("key 重复")));
    }

    @Test
    void shouldRejectDanglingEdge() {
        WorkflowDefinition definition = definition(
                List.of(node("A")), List.of(edge("A", "MISSING")));

        assertTrue(DagGraph.of(definition).validate().stream()
                .anyMatch(e -> e.contains("不存在的目标节点")));
    }

    @Test
    void shouldRejectSelfDependency() {
        WorkflowDefinition definition = definition(
                List.of(node("A")), List.of(edge("A", "A")));

        assertTrue(DagGraph.of(definition).validate().stream()
                .anyMatch(e -> e.contains("依赖自身")));
    }

    @Test
    void shouldRejectEmptyWorkflow() {
        assertTrue(DagGraph.of(definition(List.of(), List.of())).validate().stream()
                .anyMatch(e -> e.contains("没有节点")));
    }

    private WorkflowDefinition definition(List<WorkflowDefinition.Node> nodes,
                                          List<WorkflowDefinition.Edge> edges) {
        return new WorkflowDefinition("wf-1", "测试", nodes, edges);
    }

    private WorkflowDefinition.Node node(String key) {
        return new WorkflowDefinition.Node(key, "节点 " + key, "DEMO", null, Map.of());
    }

    private WorkflowDefinition.Edge edge(String from, String to) {
        return new WorkflowDefinition.Edge(from, to, null);
    }
}
