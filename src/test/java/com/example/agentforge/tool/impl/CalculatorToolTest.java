package com.example.agentforge.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

 
class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();

    @Test
    void shouldEvaluateArithmeticWithPrecedence() {
        assertEquals("7", tool.execute(Map.of("expression", "1+2*3")));
        assertEquals("10", tool.execute(Map.of("expression", "(1+2)*3+1")));
        assertEquals("-3", tool.execute(Map.of("expression", "1-4")));
        assertEquals("2.5", tool.execute(Map.of("expression", "5/2")));
        assertEquals("3.5", tool.execute(Map.of("expression", " -1 + 4.5 ")));
    }

    @Test
    void shouldRejectDivisionByZero() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(Map.of("expression", "1/0")));
    }

    @Test
    void shouldRejectScriptLikeInput() {
        
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(Map.of("expression", "Runtime.getRuntime().exec('calc')")));
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(Map.of("expression", "1; DROP TABLE user")));
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(Map.of("expression", "2 ** 3")));
    }
}
