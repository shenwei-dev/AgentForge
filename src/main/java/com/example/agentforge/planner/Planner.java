package com.example.agentforge.planner;

import com.example.agentforge.agent.AgentManager;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.config.PlannerProperties;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelRequest;
import com.example.agentforge.model.ModelResponse;
import com.example.agentforge.planner.dto.PlanTask;
import com.example.agentforge.planner.dto.TaskPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

 

@Service
public class Planner {

    private static final Logger log = LoggerFactory.getLogger(Planner.class);

    public static final String PLANNER_SYSTEM_PROMPT = """
            你是 AgentForge 的任务规划器。只输出一个 JSON 对象，禁止 Markdown 代码块、解释文字或多余内容。
            协议格式：
            {"goal":"总体目标","tasks":[{"id":"A","title":"任务标题","description":"具体要做什么","depends_on":["前置任务id"],"agent_role":"researcher"}]}
            规则：
            1. id 在计划内唯一，使用简短字母或数字；
            2. depends_on 列出必须先完成的任务 id，没有依赖时用空数组；
            3. 禁止自依赖、依赖不存在的任务、形成环；
            4. 任务数量控制在 2~6 个，覆盖资料收集、分析对比、报告生成等必要环节；
            5. agent_role 必须从下方"可用 Agent 角色"中选择，按任务性质委派；
            6. 你只负责拆解与委派，计划是否合法由后端校验，是否执行由 Runtime 决定。
            """;

    private final PlanParser parser;
    private final PlanValidator validator;
    private final PlannerProperties properties;
    private final AgentManager agentManager;

    public Planner(PlanParser parser, PlanValidator validator,
                   PlannerProperties properties, AgentManager agentManager) {
        this.parser = parser;
        this.validator = validator;
        this.properties = properties;
        this.agentManager = agentManager;
    }

    public TaskPlan createPlan(String goal, ModelAdapter adapter, String runId,
                               String conversationId, Consumer<ExecutionEvent> events) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt()));
        messages.add(ChatMessage.user("请拆解以下任务并为每个任务委派角色：\n" + goal));

        int maxAttempts = properties.getMaxRepairAttempts() + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ModelResponse response = adapter.chat(ModelRequest.of(List.copyOf(messages)));
            try {
                TaskPlan plan = parser.parse(response.content());
                List<String> errors = new ArrayList<>(validator.validate(plan));
                errors.addAll(validateRoles(plan));
                if (!errors.isEmpty()) {
                    throw new PlanValidationException(
                            "计划校验失败: " + String.join("; ", errors), response.content());
                }
                events.accept(ExecutionEvent.planCreated(runId, conversationId,
                        plan.goal(), taskViews(plan)));
                return plan;
            } catch (PlanValidationException e) {
                log.warn("计划生成第 {}/{} 次失败: {}", attempt, maxAttempts, e.getMessage());
                if (attempt == maxAttempts) {
                    throw new PlanningException(
                            "计划生成失败，已尝试 " + maxAttempts + " 次: " + e.getMessage(), e);
                }
                messages.add(ChatMessage.assistant(response.content()));
                messages.add(ChatMessage.user("上一次输出不合法：" + e.getMessage()
                        + "。请严格按 JSON 协议重新输出完整计划，不要输出解释文字。"));
            }
        }
        throw new PlanningException("计划生成失败");
    }

    private String systemPrompt() {
        return PLANNER_SYSTEM_PROMPT + "\n可用 Agent 角色（agent_role 必须取自此列表）：\n"
                + agentManager.buildRoleDescriptions();
    }

     
    private List<String> validateRoles(TaskPlan plan) {
        Set<String> allowed = agentManager.assignableRoleIds();
        List<String> errors = new ArrayList<>();
        for (PlanTask task : plan.tasks()) {
            String role = task.agentRole();
            if (role == null || role.isBlank() || AgentManager.GENERAL.equals(role)) {
                continue;
            }
            if (!allowed.contains(role)) {
                errors.add("任务 " + task.id() + " 指定了未注册的 Agent 角色: " + role);
            }
        }
        return errors;
    }

    private List<Map<String, Object>> taskViews(TaskPlan plan) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (PlanTask task : plan.tasks()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", task.id());
            view.put("title", task.title());
            view.put("dependsOn", task.dependsOn());
            view.put("agentRole", task.agentRole() == null
                    ? AgentManager.GENERAL : task.agentRole());
            views.add(view);
        }
        return views;
    }
}
