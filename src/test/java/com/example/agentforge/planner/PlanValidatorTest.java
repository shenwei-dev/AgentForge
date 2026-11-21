package com.example.agentforge.planner;

import com.example.agentforge.config.PlannerProperties;
import com.example.agentforge.planner.dto.PlanTask;
import com.example.agentforge.planner.dto.TaskPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class PlanValidatorTest {

    private final PlannerProperties properties = new PlannerProperties();
    private final PlanValidator validator = new PlanValidator(properties);

    @Test
    void shouldAcceptValidPlan() {
        TaskPlan plan = plan(task("A"), task("B", "A"), task("C", "A", "B"));

        assertTrue(validator.validate(plan).isEmpty());
    }

    @Test
    void shouldRejectDuplicateIds() {
        TaskPlan plan = plan(task("A"), task("A"));

        assertTrue(errorsContain(plan, "id 重复"));
    }

    @Test
    void shouldRejectMissingDependency() {
        TaskPlan plan = plan(task("A"), task("B", "Z"));

        assertTrue(errorsContain(plan, "依赖不存在的任务"));
    }

    @Test
    void shouldRejectSelfDependency() {
        TaskPlan plan = plan(task("A", "A"));

        assertTrue(errorsContain(plan, "依赖自身"));
    }

    @Test
    void shouldDetectCycle() {
        TaskPlan plan = plan(task("A", "C"), task("B", "A"), task("C", "B"));

        assertTrue(errorsContain(plan, "存在环"));
    }

    @Test
    void shouldRejectBlankFields() {
        TaskPlan plan = plan(new PlanTask("", "", "", List.of(), null));

        List<String> errors = validator.validate(plan);
        assertTrue(errors.stream().anyMatch(e -> e.contains("id 不能为空")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("title 不能为空")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("description 不能为空")));
    }

    @Test
    void shouldRejectTooManyTasks() {
        properties.setMaxTasks(2);
        TaskPlan plan = plan(task("A"), task("B"), task("C"));

        assertTrue(errorsContain(plan, "超过上限"));
    }

    @Test
    void shouldReturnDependencyOrderedTasks() {
        
        TaskPlan plan = plan(task("D", "C"), task("C", "A", "B"),
                task("B", "A"), task("A"));

        List<String> order = validator.topologicalOrder(plan).stream().map(PlanTask::id).toList();

        assertEquals(List.of("A", "B", "C", "D"), order);
    }

    @Test
    void shouldKeepStableOrderForIndependentTasks() {
        TaskPlan plan = plan(task("A"), task("B"), task("C", "A", "B"));

        assertEquals(List.of("A", "B", "C"),
                validator.topologicalOrder(plan).stream().map(PlanTask::id).toList());
    }

    @Test
    void shouldThrowWhenOrderingInvalidPlan() {
        TaskPlan plan = plan(task("A"), task("B", "Z"));

        assertThrows(PlanValidationException.class, () -> validator.topologicalOrder(plan));
    }

    @Test
    void shouldHandleLargePlanWithoutStackOverflow() {
        List<PlanTask> tasks = new ArrayList<>();
        tasks.add(task("T0"));
        for (int i = 1; i < 60; i++) {
            tasks.add(task("T" + i, "T" + (i - 1)));
        }
        properties.setMaxTasks(100);
        TaskPlan plan = new TaskPlan("长链计划", tasks);

        assertEquals(60, validator.topologicalOrder(plan).size());
    }

    private boolean errorsContain(TaskPlan plan, String keyword) {
        return validator.validate(plan).stream().anyMatch(e -> e.contains(keyword));
    }

    private TaskPlan plan(PlanTask... tasks) {
        return new TaskPlan("总体目标", List.of(tasks));
    }

    private PlanTask task(String id, String... dependsOn) {
        return new PlanTask(id, "任务 " + id, "描述 " + id, List.of(dependsOn), "general");
    }
}
