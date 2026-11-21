package com.example.agentforge.workflow.impl;

import com.example.agentforge.agent.AgentManager;
import com.example.agentforge.agent.AgentProfile;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.runtime.Agent;
import com.example.agentforge.runtime.AgentContext;
import com.example.agentforge.runtime.AgentResult;
import com.example.agentforge.runtime.AgentRuntime;
import com.example.agentforge.tool.AgentScopedToolExecutor;
import com.example.agentforge.tool.ToolExecutor;
import com.example.agentforge.tool.ToolRegistry;
import com.example.agentforge.workflow.NodeExecutor;
import com.example.agentforge.workflow.model.WorkflowDefinition;
import com.example.agentforge.workflow.model.WorkflowRunResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

 
public class AgentNodeExecutor implements NodeExecutor {

    private final AgentRuntime runtime;
    private final AgentManager agentManager;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final ModelAdapterRegistry modelRegistry;

    public AgentNodeExecutor(AgentRuntime runtime, AgentManager agentManager,
                             ToolExecutor toolExecutor, ToolRegistry toolRegistry,
                             ModelAdapterRegistry modelRegistry) {
        this.runtime = runtime;
        this.agentManager = agentManager;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.modelRegistry = modelRegistry;
    }

    @Override
    public WorkflowRunResult.NodeResult execute(WorkflowDefinition.Node node,
                                                Map<String, String> dependencyOutputs) {
        try {
            String roleId = node.agentId() == null || node.agentId().isBlank()
                    ? AgentManager.GENERAL : node.agentId();
            AgentProfile profile = agentManager.get(roleId);
            Agent agent = agentManager.createAgent(roleId, toolRegistry.buildToolsPrompt());
            ModelAdapter adapter = modelRegistry.current();

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(agent.systemPrompt()
                    + "\n你正在执行工作流中的一个节点，只完成本节点任务。"));
            StringBuilder prompt = new StringBuilder();
            prompt.append("节点 [").append(node.key()).append("] ")
                    .append(node.name()).append('\n');
            if (node.config() != null && !node.config().isEmpty()) {
                prompt.append("节点配置：").append(node.config()).append('\n');
            }
            if (dependencyOutputs != null && !dependencyOutputs.isEmpty()) {
                prompt.append("前置节点结果：\n");
                dependencyOutputs.forEach((key, value) -> prompt.append("- [").append(key)
                        .append("] ").append(value).append('\n'));
            }
            messages.add(ChatMessage.user(prompt.toString()));

            AgentContext context = new AgentContext("node-" + UUID.randomUUID(), "",
                    agent.id(), Clock.systemUTC(), agentManager.limitsFor(profile), messages);
            ToolExecutor scoped = new AgentScopedToolExecutor(toolExecutor, agent.allowedTools());
            AgentResult result = runtime.run(agent, context, adapter, scoped, event -> { });
            if (result.isSuccess()) {
                return WorkflowRunResult.NodeResult.ok(result.finalAnswer());
            }
            return WorkflowRunResult.NodeResult.failed(
                    "节点 Agent 未完成（" + result.terminationReason() + "）");
        } catch (Exception e) {
            return WorkflowRunResult.NodeResult.failed("节点执行异常: " + e.getMessage());
        }
    }
}
