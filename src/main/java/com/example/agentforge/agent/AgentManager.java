package com.example.agentforge.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agentforge.agent.mapper.AgentConfigMapper;
import com.example.agentforge.common.BusinessException;
import com.example.agentforge.config.RuntimeProperties;
import com.example.agentforge.runtime.Agent;
import com.example.agentforge.runtime.AgentContext;
import com.example.agentforge.runtime.DefaultAgent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

 

@Service
public class AgentManager {

    private static final Logger log = LoggerFactory.getLogger(AgentManager.class);

    public static final String GENERAL = "general";
    public static final String WRITER = "writer";

    private final AgentConfigMapper mapper;
    private final ObjectMapper objectMapper;
    private final RuntimeProperties runtimeProperties;
    private final Map<String, AgentProfile> profiles = new ConcurrentHashMap<>();
    private final Object loadLock = new Object();
    private volatile boolean loaded;

    public AgentManager(AgentConfigMapper mapper, ObjectMapper objectMapper,
                        RuntimeProperties runtimeProperties) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.runtimeProperties = runtimeProperties;
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (loadLock) {
            if (loaded) {
                return;
            }
            loadFromDatabase();
            loaded = true;
        }
    }

     
    public void refresh() {
        synchronized (loadLock) {
            loadFromDatabase();
            loaded = true;
        }
    }

    private void loadFromDatabase() {
        profiles.clear();
        for (AgentProfile profile : defaults()) {
            profiles.put(profile.id(), profile);
        }
        try {
            List<AgentConfigEntity> rows = mapper.selectList(
                    new LambdaQueryWrapper<AgentConfigEntity>()
                            .eq(AgentConfigEntity::getEnabled, 1));
            for (AgentConfigEntity row : rows) {
                profiles.put(row.getId(), toProfile(row));
            }
            log.info("Agent 角色加载完成，共 {} 个: {}", profiles.size(), profiles.keySet());
        } catch (Exception e) {
            log.warn("从 agent_config 读取角色失败，使用内置默认配置", e);
        }
    }

    public AgentProfile get(String roleId) {
        ensureLoaded();
        AgentProfile profile = profiles.get(roleId);
        if (profile == null) {
            throw new BusinessException(404, "未注册的 Agent 角色: " + roleId);
        }
        return profile;
    }

    public Optional<AgentProfile> find(String roleId) {
        ensureLoaded();
        return Optional.ofNullable(profiles.get(roleId));
    }

    public List<AgentProfile> list() {
        ensureLoaded();
        return profiles.values().stream()
                .sorted(Comparator.comparing(AgentProfile::id))
                .toList();
    }

     
    public Set<String> assignableRoleIds() {
        ensureLoaded();
        return Set.copyOf(profiles.keySet());
    }

     
    public String buildRoleDescriptions() {
        ensureLoaded();
        StringBuilder builder = new StringBuilder();
        for (AgentProfile profile : list()) {
            builder.append("- ").append(profile.id())
                    .append(" (").append(profile.role()).append("): ")
                    .append(profile.name()).append("，")
                    .append(abbreviate(profile.systemPrompt(), 80))
                    .append("，可用工具 ").append(profile.allowedTools())
                    .append('\n');
        }
        return builder.toString();
    }

     
    public Agent createAgent(String roleId, String toolsPrompt) {
        AgentProfile profile = get(roleId);
        String prompt = profile.systemPrompt() + "\n\n"
                + DefaultAgent.buildSystemPrompt(toolsPrompt);
        return new DefaultAgent(profile.id(), prompt, profile.allowedTools());
    }

     
    public AgentContext.Limits limitsFor(AgentProfile profile) {
        AgentContext.Limits base = runtimeProperties.toLimits();
        int maxRounds = profile.maxRounds() > 0
                ? Math.min(profile.maxRounds(), base.maxRounds()) : base.maxRounds();
        long timeoutMs = profile.timeoutMs() > 0
                ? Math.min(profile.timeoutMs(), base.timeoutMs()) : base.timeoutMs();
        return new AgentContext.Limits(maxRounds, timeoutMs, base.tokenBudget(),
                base.maxModelRetries(), base.maxToolRetries(), base.maxParseRetries());
    }

    private List<AgentProfile> defaults() {
        return List.of(
                new AgentProfile(GENERAL, "通用 Agent", "GENERAL",
                        "你是通用任务执行 Agent，依据任务上下文给出准确结论。",
                        Set.of("echo", "calculator", "datetime", "file_search", "file_read"),
                        null, null, 8, 120_000L, true),
                new AgentProfile("planner", "规划 Agent", "PLANNER",
                        "你负责把复杂任务拆解为有依赖关系的可执行子任务。",
                        Set.of(), null, null, 6, 120_000L, true),
                new AgentProfile("researcher", "研究 Agent", "RESEARCHER",
                        "你负责收集资料、检索文件，输出带依据的事实性摘要。",
                        Set.of("file_search", "file_read", "echo"),
                        null, null, 10, 180_000L, true),
                new AgentProfile("analyst", "分析 Agent", "ANALYST",
                        "你负责对资料做对比、计算和推理，给出结构化结论。",
                        Set.of("calculator", "echo"), null, null, 10, 180_000L, true),
                new AgentProfile("reviewer", "审核 Agent", "REVIEWER",
                        "你负责检查结论中的事实错误、逻辑漏洞与遗漏，并给出修改建议。",
                        Set.of("file_read"), null, null, 6, 120_000L, true),
                new AgentProfile(WRITER, "写作 Agent", "WRITER",
                        "你负责把多方结果整合为结构清晰、可读的 Markdown 最终报告。",
                        Set.of("echo"), null, null, 8, 180_000L, true));
    }

    private AgentProfile toProfile(AgentConfigEntity row) {
        return new AgentProfile(
                row.getId(),
                row.getAgentName(),
                row.getRole(),
                row.getSystemPrompt(),
                parseTools(row.getToolsJson()),
                row.getModelProvider(),
                row.getModelName(),
                row.getMaxRounds() == null ? 0 : row.getMaxRounds(),
                row.getTimeoutSeconds() == null ? 0L : row.getTimeoutSeconds() * 1000L,
                row.getEnabled() == null || row.getEnabled() == 1);
    }

    private Set<String> parseTools(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            Set<String> tools = new LinkedHashSet<>();
            if (node.isArray()) {
                node.forEach(item -> tools.add(item.asText()));
            }
            return Set.copyOf(tools);
        } catch (JsonProcessingException e) {
            log.warn("解析 tools_json 失败: {}", json);
            return Set.of();
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
