package com.example.agentforge.workflow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

 

public final class DagGraph {

    private final Map<String, WorkflowDefinition.Node> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
    private final Map<String, Set<String>> successors = new LinkedHashMap<>();
    private final List<String> structuralErrors = new ArrayList<>();

    private DagGraph(WorkflowDefinition definition) {
        for (WorkflowDefinition.Node node : definition.nodes()) {
            if (node.key() == null || node.key().isBlank()) {
                structuralErrors.add("节点 key 不能为空");
                continue;
            }
            if (nodes.putIfAbsent(node.key(), node) != null) {
                structuralErrors.add("节点 key 重复: " + node.key());
            }
        }
        for (String key : nodes.keySet()) {
            dependencies.put(key, new LinkedHashSet<>());
            successors.put(key, new LinkedHashSet<>());
        }
        for (WorkflowDefinition.Edge edge : definition.edges()) {
            if (edge.from() == null || !nodes.containsKey(edge.from())) {
                structuralErrors.add("边引用了不存在的源节点: " + edge.from());
                continue;
            }
            if (edge.to() == null || !nodes.containsKey(edge.to())) {
                structuralErrors.add("边引用了不存在的目标节点: " + edge.to());
                continue;
            }
            if (edge.from().equals(edge.to())) {
                structuralErrors.add("节点不能依赖自身: " + edge.from());
                continue;
            }
            dependencies.get(edge.to()).add(edge.from());
            successors.get(edge.from()).add(edge.to());
        }
    }

    public static DagGraph of(WorkflowDefinition definition) {
        return new DagGraph(definition);
    }

     
    public List<String> validate() {
        List<String> errors = new ArrayList<>(structuralErrors);
        if (nodes.isEmpty()) {
            errors.add("工作流没有节点");
            return errors;
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        int scheduled = computeLevels().stream().mapToInt(List::size).sum();
        if (scheduled < nodes.size()) {
            errors.add("DAG 存在环，无法拓扑排序");
        }
        return errors;
    }

    public boolean hasCycle() {
        if (!structuralErrors.isEmpty() || nodes.isEmpty()) {
            return false;
        }
        return computeLevels().stream().mapToInt(List::size).sum() < nodes.size();
    }

     
    public List<List<String>> levels() {
        return computeLevels();
    }

    public Set<String> dependenciesOf(String nodeKey) {
        return Collections.unmodifiableSet(
                dependencies.getOrDefault(nodeKey, Set.of()));
    }

    public WorkflowDefinition.Node node(String key) {
        return nodes.get(key);
    }

    private List<List<String>> computeLevels() {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (String key : nodes.keySet()) {
            indegree.put(key, dependencies.get(key).size());
        }
        List<List<String>> levels = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        while (processed.size() < nodes.size()) {
            List<String> current = new ArrayList<>();
            for (String key : nodes.keySet()) {
                if (!processed.contains(key) && indegree.get(key) == 0) {
                    current.add(key);
                }
            }
            if (current.isEmpty()) {
                break;
            }
            levels.add(List.copyOf(current));
            for (String key : current) {
                processed.add(key);
                for (String next : successors.get(key)) {
                    indegree.merge(next, -1, Integer::sum);
                }
            }
        }
        return levels;
    }
}
