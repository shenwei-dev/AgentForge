package com.example.agentforge.planner;

import com.example.agentforge.agent.AgentExecutionTracker;
import com.example.agentforge.agent.AgentManager;
import com.example.agentforge.agent.AgentProfile;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.memory.MemoryManager;
import com.example.agentforge.memory.MemoryScope;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.planner.dto.AgentOutput;
import com.example.agentforge.planner.dto.PlanExecutionResult;
import com.example.agentforge.planner.dto.PlanTask;
import com.example.agentforge.planner.dto.TaskPlan;
import com.example.agentforge.runtime.Agent;
import com.example.agentforge.runtime.AgentContext;
import com.example.agentforge.runtime.AgentResult;
import com.example.agentforge.runtime.AgentRuntime;
import com.example.agentforge.tool.AgentScopedToolExecutor;
import com.example.agentforge.tool.ToolCallContext;
import com.example.agentforge.tool.ToolExecutor;
import com.example.agentforge.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

 

@Service
public class PlanExecutor {

    private static final int MAX_UPSTREAM_RESULT_CHARS = 1000;

    private final AgentRuntime runtime;
    private final PlanValidator validator;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final AgentManager agentManager;
    private final ModelAdapterRegistry modelRegistry;
    private final AgentExecutionTracker tracker;
    private final ResultIntegrator integrator;
    private final MemoryManager memoryManager;

    public PlanExecutor(AgentRuntime runtime, PlanValidator validator,
                        ToolExecutor toolExecutor, ToolRegistry toolRegistry,
                        AgentManager agentManager, ModelAdapterRegistry modelRegistry,
                        AgentExecutionTracker tracker, ResultIntegrator integrator,
                        MemoryManager memoryManager) {
        this.runtime = runtime;
        this.validator = validator;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.agentManager = agentManager;
        this.modelRegistry = modelRegistry;
        this.tracker = tracker;
        this.integrator = integrator;
        this.memoryManager = memoryManager;
    }

    public PlanExecutionResult execute(TaskPlan plan, ModelAdapter adapter, String runId,
                                       String conversationId, Consumer<ExecutionEvent> events) {
        long start = System.currentTimeMillis();
        tracker.reset(runId);
        List<PlanTask> ordered = validator.topologicalOrder(plan);
        Map<String, AgentOutput> completedResults = new LinkedHashMap<>();
        List<AgentOutput> writerInputs = new ArrayList<>();
        List<PlanExecutionResult.TaskOutcome> outcomes = new ArrayList<>();
        int succeeded = 0;

        for (int index = 0; index < ordered.size(); index++) {
            PlanTask task = ordered.get(index);
            AgentProfile profile = resolveProfile(task.agentRole());
            Agent agent = agentManager.createAgent(profile.id(), toolRegistry.buildToolsPrompt());
            ModelAdapter taskAdapter = resolveAdapter(profile, adapter);

            events.accept(ExecutionEvent.planTaskStarted(runId, conversationId,
                    task.id(), task.title(), index + 1, ordered.size(), profile.id()));
            tracker.started(runId, task.id(), profile.id());

            AgentContext context = buildContext(runId, conversationId, plan, task,
                    completedResults, agent, profile);
            ToolExecutor scoped = new AgentScopedToolExecutor(toolExecutor, agent.allowedTools());
            ToolCallContext.set(runId, agent.id());

            long taskStart = System.currentTimeMillis();
            AgentResult result;
            try {
                result = runtime.run(agent, context, taskAdapter, scoped, subTaskEvents(events));
            } finally {
                ToolCallContext.clear();
            }
            long taskLatency = System.currentTimeMillis() - taskStart;

            boolean success = result.isSuccess();
            String answer = success ? result.finalAnswer()
                    : "任务未完成（" + result.terminationReason() + "）";
            if (success) {
                succeeded++;
                AgentOutput output = new AgentOutput(task.id(), profile.role(), answer);
                completedResults.put(task.id(), output);
                writerInputs.add(output);
                
                memoryManager.putTaskResult(conversationId, task.id(), answer);
            }
            tracker.finished(runId, task.id(), profile.id(), success, taskLatency);
            outcomes.add(new PlanExecutionResult.TaskOutcome(task.id(), task.title(),
                    success, success ? answer : null, success ? null : answer, taskLatency));
            events.accept(ExecutionEvent.planTaskCompleted(runId, conversationId,
                    task.id(), success, taskLatency, success ? null : answer));
        }

        String integrated = writerInputs.isEmpty() ? null
                : integrator.integrate(plan, writerInputs, adapter, runId, conversationId, events);
        String summary = integrated != null ? integrated : buildSummary(plan, outcomes);

        return new PlanExecutionResult(runId, plan.goal(), outcomes, summary,
                ordered.size(), succeeded, System.currentTimeMillis() - start, tracker.of(runId));
    }

     
    private AgentProfile resolveProfile(String role) {
        if (role == null || role.isBlank() || AgentManager.GENERAL.equals(role)) {
            return agentManager.get(AgentManager.GENERAL);
        }
        return agentManager.find(role).orElseGet(() -> agentManager.get(AgentManager.GENERAL));
    }

     
    private ModelAdapter resolveAdapter(AgentProfile profile, ModelAdapter defaultAdapter) {
        if (profile.modelProvider() == null || profile.modelProvider().isBlank()) {
            return defaultAdapter;
        }
        return modelRegistry.get(profile.modelProvider());
    }

     
    private Consumer<ExecutionEvent> subTaskEvents(Consumer<ExecutionEvent> delegate) {
        return event -> {
            String type = event.event();
            if (ExecutionEvent.RUN_COMPLETED.equals(type)
                    || ExecutionEvent.RUN_FAILED.equals(type)
                    || ExecutionEvent.MESSAGE_DELTA.equals(type)) {
                return;
            }
            delegate.accept(event);
        };
    }

    private AgentContext buildContext(String runId, String conversationId, TaskPlan plan,
                                      PlanTask task, Map<String, AgentOutput> completedResults,
                                      Agent agent, AgentProfile profile) {
        String system = agent.systemPrompt()
                + "\n你正在执行总计划中的单个子任务，只完成当前任务，不要替其他任务下结论。";

        StringBuilder prompt = new StringBuilder();
        prompt.append("总目标：").append(plan.goal()).append('\n');
        prompt.append("当前任务 [").append(task.id()).append("] ")
                .append(task.title()).append('\n');
        prompt.append("任务描述：").append(task.description()).append('\n');
        if (!completedResults.isEmpty()) {
            prompt.append("已完成的前置任务结果（含产出角色）：\n");
            completedResults.forEach((id, output) -> prompt.append("- [").append(id)
                    .append("] (").append(output.role()).append(") ")
                    .append(abbreviate(output.content())).append('\n'));
        }
        
        List<ChatMessage> messages = memoryManager.buildContext(
                MemoryScope.agent(conversationId, profile.id()), system, prompt.toString());

        return new AgentContext(runId, conversationId, profile.id(), Clock.systemUTC(),
                agentManager.limitsFor(profile), messages);
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_UPSTREAM_RESULT_CHARS
                ? text.substring(0, MAX_UPSTREAM_RESULT_CHARS) + "\n[结果已截断]"
                : text;
    }

    private String buildSummary(TaskPlan plan, List<PlanExecutionResult.TaskOutcome> outcomes) {
        StringBuilder builder = new StringBuilder();
        builder.append("## 任务执行结果\n\n目标：").append(plan.goal()).append("\n\n");
        for (PlanExecutionResult.TaskOutcome outcome : outcomes) {
            builder.append("### ").append(outcome.taskId()).append(" · ")
                    .append(outcome.title()).append('\n');
            builder.append(outcome.success() ? outcome.result() : outcome.errorMessage())
                    .append("\n\n");
        }
        return builder.toString();
    }
}
