package com.example.agentforge.workflow.impl;

import com.example.agentforge.workflow.NodeExecutor;
import com.example.agentforge.workflow.model.WorkflowDefinition;
import com.example.agentforge.workflow.model.WorkflowRunResult;

import java.util.Map;

 

public class DemoNodeExecutor implements NodeExecutor {

    private final long delayMs;

    public DemoNodeExecutor(long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    @Override
    public WorkflowRunResult.NodeResult execute(WorkflowDefinition.Node node,
                                                Map<String, String> dependencyOutputs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WorkflowRunResult.NodeResult.failed("节点被中断: " + node.key());
        }
        String upstream = dependencyOutputs == null || dependencyOutputs.isEmpty()
                ? "无" : dependencyOutputs.keySet().toString();
        return WorkflowRunResult.NodeResult.ok(
                "节点 " + node.key() + " (" + node.name() + ") 执行完成；前置节点: " + upstream);
    }
}
