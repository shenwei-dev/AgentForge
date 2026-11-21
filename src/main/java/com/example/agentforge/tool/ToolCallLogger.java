package com.example.agentforge.tool;

import com.example.agentforge.tool.mapper.ToolCallMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

 

@Service
public class ToolCallLogger {

    private static final Logger log = LoggerFactory.getLogger(ToolCallLogger.class);

    private final ToolCallMapper mapper;
    private final ObjectMapper objectMapper;

    public ToolCallLogger(ToolCallMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(ToolCallRecord record) {
        try {
            ToolCallEntity entity = new ToolCallEntity();
            entity.setId(record.id());
            entity.setRunId(record.runId());
            entity.setAgentId(record.agentId());
            entity.setToolName(record.toolName());
            entity.setInputJson(objectMapper.writeValueAsString(
                    record.input() == null ? Map.of() : record.input()));
            if (record.output() != null) {
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("content", record.output());
                entity.setOutputJson(objectMapper.writeValueAsString(output));
            }
            entity.setStatus(record.status());
            entity.setErrorMessage(record.errorMessage());
            entity.setDurationMs(record.durationMs());
            mapper.insert(entity);
        } catch (Exception e) {
            log.warn("工具调用日志写入失败（不影响主链路） tool={} status={}",
                    record.toolName(), record.status(), e);
        }
    }
}
