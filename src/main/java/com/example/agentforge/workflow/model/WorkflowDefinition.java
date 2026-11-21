package com.example.agentforge.workflow.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

 
public record WorkflowDefinition(String id, String name,
                                 List<Node> nodes, List<Edge> edges) {

    public record Node(String key, String name, String type, String agentId,
                       Map<String, Object> config) {
        public Node {
            config = config == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(config));
        }

         
        public String resolvedType() {
            return type == null || type.isBlank()
                    ? "DEMO" : type.trim().toUpperCase(Locale.ROOT);
        }
    }

    public record Edge(String from, String to, String condition) {
    }

    public WorkflowDefinition {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public WorkflowDefinition withId(String newId) {
        return new WorkflowDefinition(newId, name, nodes, edges);
    }

     
    public static WorkflowDefinition demo() {
        return new WorkflowDefinition("demo-dag", "示例并行工作流",
                List.of(
                        new Node("A", "收集资料 A", "DEMO", null, Map.of()),
                        new Node("B", "收集资料 B", "DEMO", null, Map.of()),
                        new Node("C", "汇总 A 与 B", "DEMO", null, Map.of()),
                        new Node("D", "生成最终结论", "DEMO", null, Map.of())),
                List.of(
                        new Edge("A", "C", null),
                        new Edge("B", "C", null),
                        new Edge("C", "D", null)));
    }
}
