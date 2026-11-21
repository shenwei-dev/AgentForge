package com.example.agentforge.checkpoint;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agentforge.checkpoint.mapper.CheckpointMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

 

@Service
public class CheckpointManager {

    private static final Logger log = LoggerFactory.getLogger(CheckpointManager.class);

    private final CheckpointMapper mapper;
    private final ObjectMapper objectMapper;
    private final Map<String, List<CheckpointSnapshot>> memory = new ConcurrentHashMap<>();

    @Autowired
    public CheckpointManager(CheckpointMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public CheckpointManager(ObjectMapper objectMapper) {
        this(null, objectMapper);
    }

    public synchronized CheckpointSnapshot save(CheckpointSnapshot snapshot) {
        int version = nextVersion(snapshot.runId(), snapshot.nodeId(), snapshot.stateType());
        CheckpointSnapshot versioned = snapshot.withVersion(version);
        memory.computeIfAbsent(snapshot.runId(), key -> new CopyOnWriteArrayList<>())
                .add(versioned);
        if (mapper != null) {
            try {
                CheckpointEntity entity = new CheckpointEntity();
                entity.setId(UUID.randomUUID().toString());
                entity.setRunId(versioned.runId());
                entity.setNodeId(versioned.nodeId() == null ? "" : versioned.nodeId());
                entity.setStateType(versioned.stateType());
                entity.setStateJson(objectMapper.writeValueAsString(versioned));
                entity.setVersion(versioned.version());
                mapper.insert(entity);
            } catch (Exception e) {
                log.warn("Checkpoint 落库失败，已保留内存快照 runId={}", versioned.runId(), e);
            }
        }
        return versioned;
    }

     
    public Optional<CheckpointSnapshot> latestRun(String runId) {
        return list(runId).stream()
                .filter(s -> CheckpointSnapshot.TYPE_RUN.equals(s.stateType()))
                .max(Comparator.comparingInt(CheckpointSnapshot::version));
    }

    public Optional<CheckpointSnapshot> latest(String runId, String nodeId, String stateType) {
        return list(runId).stream()
                .filter(s -> stateType.equals(s.stateType()))
                .filter(s -> nodeId.equals(s.nodeId()))
                .max(Comparator.comparingInt(CheckpointSnapshot::version));
    }

    public List<CheckpointSnapshot> list(String runId) {
        List<CheckpointSnapshot> result = new ArrayList<>();
        List<CheckpointSnapshot> cached = memory.get(runId);
        if (cached != null) {
            result.addAll(cached);
        }
        if (mapper != null) {
            try {
                for (CheckpointEntity entity : mapper.selectList(
                        new LambdaQueryWrapper<CheckpointEntity>()
                                .eq(CheckpointEntity::getRunId, runId))) {
                    CheckpointSnapshot snapshot = objectMapper.readValue(
                            entity.getStateJson(), CheckpointSnapshot.class);
                    boolean duplicate = result.stream().anyMatch(s ->
                            s.version() == snapshot.version()
                                    && s.stateType().equals(snapshot.stateType())
                                    && s.nodeId().equals(snapshot.nodeId()));
                    if (!duplicate) {
                        result.add(snapshot);
                    }
                }
            } catch (Exception e) {
                log.warn("Checkpoint 读取失败，使用内存快照 runId={}", runId, e);
            }
        }
        result.sort(Comparator.comparingInt(CheckpointSnapshot::version));
        return result;
    }

    private int nextVersion(String runId, String nodeId, String stateType) {
        return list(runId).stream()
                .filter(s -> stateType.equals(s.stateType()))
                .filter(s -> (nodeId == null ? "" : nodeId).equals(s.nodeId()))
                .mapToInt(CheckpointSnapshot::version)
                .max().orElse(0) + 1;
    }
}
