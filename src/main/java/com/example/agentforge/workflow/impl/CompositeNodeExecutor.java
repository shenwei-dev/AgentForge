package com.example.agentforge.workflow.impl;

import com.example.agentforge.agent.AgentManager;
import com.example.agentforge.config.WorkflowProperties;
import com.example.agentforge.model.ModelAdapterRegistry;
import com.example.agentforge.runtime.AgentRuntime;
import com.example.agentforge.tool.ToolExecutor;
import com.example.agentforge.tool.ToolRegistry;
import com.example.agentforge.workflow.NodeExecutor;
import com.example.agentforge.workflow.model.WorkflowDefinition;
import com.example.agentforge.workflow.model.WorkflowRunResult;
import org.springframework.stereotype.Component;

import java.util.Map;

 
@Component
public class CompositeNodeExecutor implements NodeExecutor {

    private final Map<String, NodeExecutor> delegates;

    public CompositeNodeExecutor(AgentRuntime runtime, AgentManager agentManager,
                                 ToolExecutor toolExecutor, ToolRegistry toolRegistry,
                                 ModelAdapterRegistry modelRegistry, WorkflowProperties properties) {
        this.delegates = Map.of(
                "DEMO", new DemoNodeExecutor(properties.getDemoDelayMs()),
                "AGENT", new AgentNodeExecutor(runtime, agentManager, toolExecutor,
                        toolRegistry, modelRegistry));
    }

    @Override
    public WorkflowRunResult.NodeResult execute(WorkflowDefinition.Node node,
                                                Map<String, String> dependencyOutputs) {
        String type = node.resolvedType();
        NodeExecutor delegate = delegates.get(type);
        if (delegate == null) {
            return WorkflowRunResult.NodeResult.failed("不支持的节点类型: " + type);
        }
        return delegate.execute(node, dependencyOutputs);
    }
}
