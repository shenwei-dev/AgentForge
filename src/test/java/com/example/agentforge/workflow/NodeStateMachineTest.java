package com.example.agentforge.workflow;

import com.example.agentforge.workflow.model.NodeStateMachine;
import com.example.agentforge.workflow.model.WorkflowStates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class NodeStateMachineTest {

    @Test
    void shouldAllowNormalLifecycle() {
        assertDoesNotThrow(() -> NodeStateMachine.assertTransition(
                WorkflowStates.NodeState.PENDING, WorkflowStates.NodeState.READY));
        assertDoesNotThrow(() -> NodeStateMachine.assertTransition(
                WorkflowStates.NodeState.READY, WorkflowStates.NodeState.RUNNING));
        assertDoesNotThrow(() -> NodeStateMachine.assertTransition(
                WorkflowStates.NodeState.RUNNING, WorkflowStates.NodeState.SUCCEEDED));
    }

    @Test
    void shouldAllowSkipAndCancel() {
        assertDoesNotThrow(() -> NodeStateMachine.assertTransition(
                WorkflowStates.NodeState.PENDING, WorkflowStates.NodeState.SKIPPED));
        assertDoesNotThrow(() -> NodeStateMachine.assertTransition(
                WorkflowStates.NodeState.RUNNING, WorkflowStates.NodeState.CANCELLED));
    }

    @Test
    void shouldRejectIllegalTransition() {
        assertThrows(IllegalStateException.class, () -> NodeStateMachine.assertTransition(
                WorkflowStates.NodeState.PENDING, WorkflowStates.NodeState.SUCCEEDED));
        assertThrows(IllegalStateException.class, () -> NodeStateMachine.assertTransition(
                WorkflowStates.NodeState.SUCCEEDED, WorkflowStates.NodeState.RUNNING));
    }

    @Test
    void terminalStatesShouldNotChange() {
        assertTrue(NodeStateMachine.isTerminal(WorkflowStates.NodeState.SUCCEEDED));
        assertTrue(NodeStateMachine.isTerminal(WorkflowStates.NodeState.FAILED));
        assertTrue(NodeStateMachine.isTerminal(WorkflowStates.NodeState.SKIPPED));
        assertFalse(NodeStateMachine.isTerminal(WorkflowStates.NodeState.RUNNING));
    }
}
