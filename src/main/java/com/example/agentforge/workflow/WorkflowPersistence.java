package com.example.agentforge.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agentforge.workflow.entity.NodeRunEntity;
import com.example.agentforge.workflow.entity.WorkflowRunEntity;
import com.example.agentforge.workflow.mapper.NodeRunMapper;
import com.example.agentforge.workflow.mapper.WorkflowRunMapper;
import com.example.agentforge.workflow.model.WorkflowStates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

 

@Service
public class WorkflowPersistence {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPersistence.class);

    private final WorkflowRunMapper runMapper;
    private final NodeRunMapper nodeMapper;

    public WorkflowPersistence(WorkflowRunMapper runMapper, NodeRunMapper nodeMapper) {
        this.runMapper = runMapper;
        this.nodeMapper = nodeMapper;
    }

    public void startRun(String runId, String workflowId, String conversationId) {
        try {
            WorkflowRunEntity entity = new WorkflowRunEntity();
            entity.setId(runId);
            entity.setWorkflowId(workflowId);
            entity.setConversationId(conversationId);
            entity.setStatus(WorkflowStates.RunStatus.RUNNING.name());
            entity.setStartedAt(LocalDateTime.now());
            runMapper.insert(entity);
        } catch (Exception e) {
            log.warn("工作流运行记录写入失败 runId={}", runId, e);
        }
    }

    public void recordNode(String runId, String nodeKey, int level, String state,
                           String inputText, String output, String error,
                           long latencyMs, LocalDateTime startedAt) {
        try {
            NodeRunEntity entity = new NodeRunEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setRunId(runId);
            entity.setNodeId(nodeKey);
            entity.setNodeKey(nodeKey);
            entity.setStatus(state);
            entity.setInputText(inputText);
            entity.setOutputText(output);
            entity.setErrorMessage(error);
            entity.setRetryCount(0);
            entity.setIdempotencyKey(runId + ":" + nodeKey + ":" + level);
            entity.setStartedAt(startedAt);
            entity.setFinishedAt(LocalDateTime.now());
            nodeMapper.insert(entity);
        } catch (Exception e) {
            log.warn("节点执行记录写入失败 runId={} node={}", runId, nodeKey, e);
        }
    }

    public void finishRun(String runId, String status, String outputText, String error) {
        try {
            WorkflowRunEntity entity = new WorkflowRunEntity();
            entity.setId(runId);
            entity.setStatus(status);
            entity.setOutputText(outputText);
            entity.setErrorMessage(error);
            entity.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(entity);
        } catch (Exception e) {
            log.warn("工作流运行状态更新失败 runId={}", runId, e);
        }
    }

    public List<NodeRunEntity> nodesOf(String runId) {
        try {
            return nodeMapper.selectList(new LambdaQueryWrapper<NodeRunEntity>()
                    .eq(NodeRunEntity::getRunId, runId)
                    .orderByAsc(NodeRunEntity::getCreatedAt));
        } catch (Exception e) {
            log.warn("节点执行记录查询失败 runId={}", runId, e);
            return List.of();
        }
    }
}
