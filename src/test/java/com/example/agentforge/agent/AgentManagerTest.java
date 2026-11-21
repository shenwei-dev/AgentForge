package com.example.agentforge.agent;

import com.example.agentforge.agent.mapper.AgentConfigMapper;
import com.example.agentforge.common.BusinessException;
import com.example.agentforge.config.RuntimeProperties;
import com.example.agentforge.runtime.Agent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

 
class AgentManagerTest {

    private final AgentConfigMapper mapper = mock(AgentConfigMapper.class);
    private final RuntimeProperties runtimeProperties = new RuntimeProperties();
    private AgentManager manager;

    @BeforeEach
    void setUp() {
        manager = new AgentManager(mapper, new ObjectMapper(), runtimeProperties);
    }

    @Test
    void shouldFallBackToDefaultsWhenDatabaseUnavailable() {
        when(mapper.selectList(any())).thenThrow(new RuntimeException("DB down"));

        manager.refresh();

        assertEquals(6, manager.list().size());
        assertTrue(manager.find("researcher").isPresent());
        assertEquals(Set.of("calculator", "echo"), manager.get("analyst").allowedTools());
    }

    @Test
    void shouldOverrideDefaultsWithDatabaseConfig() {
        AgentConfigEntity row = new AgentConfigEntity();
        row.setId("researcher");
        row.setAgentName("DB 研究");
        row.setRole("RESEARCHER");
        row.setSystemPrompt("DB 提示词");
        row.setToolsJson("[\"calculator\"]");
        row.setMaxRounds(3);
        row.setTimeoutSeconds(30);
        row.setEnabled(1);
        when(mapper.selectList(any())).thenReturn(List.of(row));

        manager.refresh();

        AgentProfile profile = manager.get("researcher");
        assertEquals("DB 研究", profile.name());
        assertEquals(Set.of("calculator"), profile.allowedTools());
        assertEquals(3, manager.limitsFor(profile).maxRounds());
    }

    @Test
    void shouldRejectUnknownRole() {
        when(mapper.selectList(any())).thenReturn(List.of());
        manager.refresh();

        assertThrows(BusinessException.class, () -> manager.get("ghost"));
    }

    @Test
    void createAgentShouldCombineRolePromptAndDecisionProtocol() {
        when(mapper.selectList(any())).thenReturn(List.of());
        manager.refresh();

        Agent agent = manager.createAgent("writer", "- echo: 回显\n");

        assertTrue(agent.systemPrompt().contains("整合"));
        assertTrue(agent.systemPrompt().contains("decision"));
        assertTrue(agent.systemPrompt().contains("echo"));
        assertTrue(agent.allowedTools().contains("echo"));
    }

    @Test
    void roleDescriptionsShouldIncludeAllRolesAndTools() {
        when(mapper.selectList(any())).thenReturn(List.of());
        manager.refresh();

        String descriptions = manager.buildRoleDescriptions();
        assertTrue(descriptions.contains("researcher"));
        assertTrue(descriptions.contains("writer"));
        assertTrue(descriptions.contains("calculator"));
        assertEquals(6, manager.assignableRoleIds().size());
    }

    @Test
    void limitsShouldNeverExceedGlobalBudget() {
        when(mapper.selectList(any())).thenReturn(List.of());
        manager.refresh();
        runtimeProperties.setMaxRounds(2);

        
        assertEquals(2, manager.limitsFor(manager.get("researcher")).maxRounds());
    }
}
