package com.example.agentforge.trace;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.trace.mapper.ExecutionEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

 

@Service
public class ExecutionEventStore {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEventStore.class);
    private static final int MAX_MESSAGE_LENGTH = 1024;
    private static final int MAX_EVENTS = 500;

    private final ExecutionEventMapper mapper;
    private final ObjectMapper objectMapper;

    public ExecutionEventStore(ExecutionEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(ExecutionEvent event) {
        
        if (event == null || ExecutionEvent.MESSAGE_DELTA.equals(event.event())) {
            return;
        }
        try {
            mapper.insert(toEntity(event));
        } catch (Exception e) {
            log.warn("执行事件落库失败（不影响主链路） eventId={} type={}",
                    event.eventId(), event.event(), e);
        }
    }

    public List<ExecutionEvent> findByRun(String runId) {
        return query("run_id", runId);
    }

    public List<ExecutionEvent> findByConversation(String conversationId) {
        return query("conversation_id", conversationId);
    }

    private List<ExecutionEvent> query(String column, String value) {
        try {
            List<ExecutionEventEntity> rows = mapper.selectList(
                    new QueryWrapper<ExecutionEventEntity>()
                            .eq(column, value)
                            .orderByAsc("id")
                            .last("LIMIT " + MAX_EVENTS));
            List<ExecutionEvent> events = new ArrayList<>();
            for (ExecutionEventEntity row : rows) {
                events.add(toEvent(row));
            }
            return events;
        } catch (Exception e) {
            log.warn("执行事件查询失败 {}={}", column, value, e);
            return List.of();
        }
    }

    private ExecutionEventEntity toEntity(ExecutionEvent event) throws JsonProcessingException {
        ExecutionEventEntity entity = new ExecutionEventEntity();
        entity.setEventId(event.eventId());
        entity.setRunId(event.runId());
        entity.setConversationId(event.conversationId());
        entity.setEventType(event.event());
        entity.setAgentId(event.agentId());
        entity.setNodeId(event.nodeId());
        entity.setToolCallId(event.toolCallId());
        entity.setLevel(event.level() == null ? "INFO" : event.level());
        entity.setMessage(truncate(event.message()));
        if (event.payload() != null && !event.payload().isEmpty()) {
            entity.setPayloadJson(objectMapper.writeValueAsString(event.payload()));
        }
        entity.setCreatedAt(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.timestamp()), ZoneId.systemDefault()));
        return entity;
    }

    private ExecutionEvent toEvent(ExecutionEventEntity entity) {
        Map<String, Object> payload = null;
        if (entity.getPayloadJson() != null) {
            try {
                payload = objectMapper.readValue(entity.getPayloadJson(),
                        new TypeReference<Map<String, Object>>() { });
            } catch (Exception e) {
                payload = Map.of();
            }
        }
        long timestamp = entity.getCreatedAt() == null ? 0L
                : entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new ExecutionEvent(entity.getEventId(), entity.getEventType(),
                entity.getRunId(), entity.getConversationId(), entity.getAgentId(),
                entity.getNodeId(), entity.getToolCallId(), entity.getLevel(),
                entity.getMessage(), payload, timestamp);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }
}
