package com.example.agentforge.planner;

import com.example.agentforge.config.PlannerProperties;
import com.example.agentforge.planner.dto.PlanTask;
import com.example.agentforge.planner.dto.TaskPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

 

@Component
public class PlanValidator {

    private final PlannerProperties properties;

    public PlanValidator(PlannerProperties properties) {
        this.properties = properties;
    }

    public List<String> validate(TaskPlan plan) {
        List<String> errors = new ArrayList<>();
        if (plan == null || plan.tasks().isEmpty()) {
            errors.add("计划任务列表不能为空");
            return errors;
        }
        if (plan.tasks().size() > properties.getMaxTasks()) {
            errors.add("任务数量超过上限 " + properties.getMaxTasks());
        }
        if (plan.goal() == null || plan.goal().isBlank()) {
            errors.add("goal 不能为空");
        }

        Set<String> ids = new LinkedHashSet<>();
        for (PlanTask task : plan.tasks()) {
            if (task.id() == null || task.id().isBlank()) {
                errors.add("任务 id 不能为空");
            } else if (!ids.add(task.id())) {
                errors.add("任务 id 重复: " + task.id());
            }
            if (task.title() == null || task.title().isBlank()) {
                errors.add("任务 title 不能为空: " + task.id());
            } else if (task.title().length() > properties.getMaxTitleLength()) {
                errors.add("任务 title 过长: " + task.id());
            }
            if (task.description() == null || task.description().isBlank()) {
                errors.add("任务 description 不能为空: " + task.id());
            } else if (task.description().length() > properties.getMaxDescriptionLength()) {
                errors.add("任务 description 过长: " + task.id());
            }
            for (String dependency : task.dependsOn()) {
                if (dependency == null || dependency.isBlank()) {
                    errors.add("任务 " + task.id() + " 存在空依赖");
                } else if (dependency.equals(task.id())) {
                    errors.add("任务不能依赖自身: " + task.id());
                }
            }
        }
        for (PlanTask task : plan.tasks()) {
            for (String dependency : task.dependsOn()) {
                if (dependency != null && !dependency.isBlank() && !ids.contains(dependency)) {
                    errors.add("任务 " + task.id() + " 依赖不存在的任务: " + dependency);
                }
            }
        }
        if (hasCycle(plan.tasks())) {
            errors.add("任务依赖存在环");
        }
        return errors;
    }

     
    public List<PlanTask> topologicalOrder(TaskPlan plan) {
        List<String> errors = validate(plan);
        if (!errors.isEmpty()) {
            throw new PlanValidationException("计划校验失败: " + String.join("; ", errors), null);
        }
        Map<String, PlanTask> byId = new LinkedHashMap<>();
        for (PlanTask task : plan.tasks()) {
            byId.put(task.id(), task);
        }
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (PlanTask task : plan.tasks()) {
            indegree.put(task.id(), 0);
        }
        for (PlanTask task : plan.tasks()) {
            for (String dependency : task.dependsOn()) {
                indegree.merge(task.id(), 1, Integer::sum);
                outgoing.computeIfAbsent(dependency, key -> new ArrayList<>()).add(task.id());
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                queue.add(id);
            }
        });
        List<PlanTask> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(byId.get(current));
            for (String next : outgoing.getOrDefault(current, List.of())) {
                int remaining = indegree.merge(next, -1, Integer::sum);
                if (remaining == 0) {
                    queue.add(next);
                }
            }
        }
        return order;
    }

     
    private boolean hasCycle(List<PlanTask> tasks) {
        Map<String, PlanTask> byId = new LinkedHashMap<>();
        for (PlanTask task : tasks) {
            byId.put(task.id(), task);
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (PlanTask task : tasks) {
            if (task.id() != null && dfs(task.id(), byId, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfs(String id, Map<String, PlanTask> byId,
                        Set<String> visiting, Set<String> visited) {
        if (visiting.contains(id)) {
            return true;
        }
        if (visited.contains(id)) {
            return false;
        }
        visiting.add(id);
        PlanTask task = byId.get(id);
        if (task != null) {
            for (String dependency : task.dependsOn()) {
                if (byId.containsKey(dependency) && dfs(dependency, byId, visiting, visited)) {
                    return true;
                }
            }
        }
        visiting.remove(id);
        visited.add(id);
        return false;
    }
}
