package com.example.agentforge.workflow.model;

import com.example.agentforge.workflow.model.WorkflowStates.NodeState;

import java.util.Map;
import java.util.Set;

 
public final class NodeStateMachine {

    private static final Map<NodeState, Set<NodeState>> ALLOWED = Map.of(
            NodeState.PENDING, Set.of(NodeState.READY, NodeState.SKIPPED, NodeState.CANCELLED),
            NodeState.READY, Set.of(NodeState.RUNNING, NodeState.SKIPPED, NodeState.CANCELLED),
            NodeState.RUNNING, Set.of(NodeState.SUCCEEDED, NodeState.FAILED, NodeState.CANCELLED),
            NodeState.WAITING_APPROVAL, Set.of(NodeState.READY, NodeState.SKIPPED, NodeState.CANCELLED),
            NodeState.SUCCEEDED, Set.of(),
            NodeState.FAILED, Set.of(),
            NodeState.SKIPPED, Set.of(),
            NodeState.CANCELLED, Set.of());

    private NodeStateMachine() {
    }

    public static void assertTransition(NodeState from, NodeState to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("非法节点状态转换: " + from + " -> " + to);
        }
    }

    public static boolean isTerminal(NodeState state) {
        return ALLOWED.getOrDefault(state, Set.of()).isEmpty();
    }
}
