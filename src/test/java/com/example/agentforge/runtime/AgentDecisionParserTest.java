package com.example.agentforge.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class AgentDecisionParserTest {

    private final AgentDecisionParser parser = new AgentDecisionParser(new ObjectMapper());

    @Test
    void shouldParseFinalDecision() {
        String raw = """
                {"thought":"已有结果","decision":"final","answer":"最终答案"}
                """;

        AgentDecision decision = parser.parse(raw);

        assertEquals(AgentDecision.Decision.FINAL, decision.decision());
        assertEquals("最终答案", decision.answer());
        assertEquals("已有结果", decision.thought());
    }

    @Test
    void shouldParseToolDecisionWithInput() {
        String raw = """
                {"thought":"需要回显","decision":"tool","tool_name":"echo","tool_input":{"text":"hello"}}
                """;

        AgentDecision decision = parser.parse(raw);

        assertEquals(AgentDecision.Decision.TOOL, decision.decision());
        assertEquals("echo", decision.toolName());
        assertEquals("hello", decision.toolInput().get("text"));
    }

    @Test
    void shouldTolerateMarkdownFenceAndSurroundingText() {
        String raw = "这是我的决策：\n```json\n"
                + "{\"decision\":\"final\",\"answer\":\"带围栏的答案\"}\n```\n谢谢";

        AgentDecision decision = parser.parse(raw);

        assertEquals(AgentDecision.Decision.FINAL, decision.decision());
        assertEquals("带围栏的答案", decision.answer());
    }

    @Test
    void shouldRejectNonJsonOutput() {
        DecisionParseException exception = assertThrows(DecisionParseException.class,
                () -> parser.parse("我觉得可以直接回答这个问题"));

        assertEquals(true, exception.getMessage().contains("未包含 JSON 对象"));
    }

    @Test
    void shouldRejectFinalWithoutAnswer() {
        assertThrows(DecisionParseException.class,
                () -> parser.parse("{\"decision\":\"final\"}"));
    }

    @Test
    void shouldRejectToolWithoutName() {
        assertThrows(DecisionParseException.class,
                () -> parser.parse("{\"decision\":\"tool\",\"tool_input\":{}}"));
    }

    @Test
    void shouldRejectUnknownDecisionValue() {
        DecisionParseException exception = assertThrows(DecisionParseException.class,
                () -> parser.parse("{\"decision\":\"maybe\",\"answer\":\"x\"}"));

        assertTrue(exception.getMessage().contains("只能是 tool 或 final"));
    }
}
