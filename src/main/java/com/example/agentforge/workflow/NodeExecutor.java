package com.example.agentforge.workflow;

import com.example.agentforge.workflow.model.WorkflowDefinition;
import com.example.agentforge.workflow.model.WorkflowRunResult;

import java.util.Map;

 

public interface NodeExecutor {

    WorkflowRunResult.NodeResult execute(WorkflowDefinition.Node node,
                                         Map<String, String> dependencyOutputs);
}
