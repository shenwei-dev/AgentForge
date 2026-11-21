package com.example.agentforge.planner;

import com.example.agentforge.planner.dto.TaskPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class PlanParserTest {

    private final PlanParser parser = new PlanParser(new ObjectMapper());

    private static final String VALID_PLAN = """
            {"goal":"对比 Spring AI 与 LangChain4j","tasks":[
              {"id":"A","title":"收集资料","description":"收集 Spring AI 资料","depends_on":[],"agent_role":"general"},
              {"id":"B","title":"生成报告","description":"对比并输出报告","depends_on":["A"]}
            ]}
            """;

    @Test
    void shouldParseStructuredPlan() {
        TaskPlan plan = parser.parse(VALID_PLAN);

        assertEquals("对比 Spring AI 与 LangChain4j", plan.goal());
        assertEquals(2, plan.tasks().size());
        assertEquals("A", plan.tasks().get(0).id());
        assertEquals(List.of("A"), plan.tasks().get(1).dependsOn());
        assertEquals("general", plan.tasks().get(0).agentRole());
    }

    @Test
    void shouldTolerateMarkdownFenceAndSurroundingText() {
        String raw = "这是我的计划：\n```json\n" + VALID_PLAN + "\n```\n请检查";

        TaskPlan plan = parser.parse(raw);

        assertEquals(2, plan.tasks().size());
    }

    @Test
    void shouldParseMissingOptionalRoleAsNull() {
        String raw = """
                {"goal":"目标","tasks":[{"id":"A","title":"标题","description":"描述","depends_on":[]}]}
                """;

        assertNull(parser.parse(raw).tasks().get(0).agentRole());
    }

    @Test
    void shouldRejectNaturalLanguageOutput() {
        PlanValidationException exception = assertThrows(PlanValidationException.class,
                () -> parser.parse("我觉得可以分成三步走"));

        assertTrue(exception.getMessage().contains("未包含 JSON 对象"));
    }

    @Test
    void shouldRejectMissingTasksArray() {
        assertThrows(PlanValidationException.class,
                () -> parser.parse("{\"goal\":\"目标\"}"));
    }

    @Test
    void shouldRejectMalformedJson() {
        assertThrows(PlanValidationException.class,
                () -> parser.parse("{\"goal\": \"x\", \"tasks\": ["));
    }
}
