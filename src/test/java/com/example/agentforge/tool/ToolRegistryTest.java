package com.example.agentforge.tool;

import com.example.agentforge.tool.impl.CalculatorTool;
import com.example.agentforge.tool.impl.EchoTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 
class ToolRegistryTest {

    private ToolRegistry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    @Test
    void shouldAutoRegisterAndExposeDescriptorsAndSchema() {
        registry = registry(Set.of("compute", "utility"),
                new CalculatorTool(), new EchoTool());

        assertEquals(Set.of("calculator", "echo"), registry.names());
        assertEquals(2, registry.descriptors().size());
        String prompt = registry.buildToolsPrompt();
        assertTrue(prompt.contains("calculator"));
        assertTrue(prompt.contains("参数 Schema"));
        assertTrue(prompt.contains("expression"));
    }

    @Test
    void shouldFailFastOnDuplicateToolNames() {
        assertThrows(IllegalStateException.class,
                () -> registry(Set.of(), stubTool("dup", Set.of(), input -> "a"),
                        stubTool("dup", Set.of(), input -> "b")));
    }

    @Test
    void shouldDenyWhenPermissionNotGranted() {
        registry = registry(Set.of(), new CalculatorTool());

        ToolResult result = registry.execute("calculator", Map.of("expression", "1+1"));

        assertEquals(false, result.success());
        assertEquals(ToolResult.DENIED, result.status());
        assertTrue(result.errorMessage().contains("权限不足"));
    }

    @Test
    void shouldRejectInvalidParametersBeforeExecution() {
        registry = registry(Set.of("compute"), new CalculatorTool());

        ToolResult result = registry.execute("calculator", Map.of());

        assertEquals(ToolResult.FAILED, result.status());
        assertTrue(result.errorMessage().contains("参数校验失败"));
    }

    @Test
    void shouldExecuteToolAndRecordLog() {
        ToolCallLogger logger = mock(ToolCallLogger.class);
        registry = new ToolRegistry(List.of(new CalculatorTool()), properties(Set.of("compute")),
                new ObjectMapper(), logger);

        ToolResult result = registry.execute("calculator", Map.of("expression", "1+2*3"));

        assertEquals(true, result.success());
        assertEquals("7", result.content());
        assertEquals(ToolResult.SUCCEEDED, result.status());
        assertTrue(result.durationMs() >= 0);
        ArgumentCaptor<ToolCallRecord> captor = ArgumentCaptor.forClass(ToolCallRecord.class);
        verify(logger, atLeastOnce()).record(captor.capture());
        assertEquals(ToolResult.SUCCEEDED, captor.getValue().status());
        assertEquals("calculator", captor.getValue().toolName());
    }

    @Test
    void shouldConvertToolExceptionToFailedResult() {
        registry = registry(Set.of("utility"),
                stubTool("boom", Set.of("utility"), input -> {
                    throw new IllegalStateException("工具内部炸了");
                }));

        ToolResult result = registry.execute("boom", Map.of());

        assertEquals(false, result.success());
        assertEquals(ToolResult.FAILED, result.status());
        assertTrue(result.errorMessage().contains("工具内部炸了"));
    }

    @Test
    void shouldTimeoutSlowTool() {
        Tool slow = new Tool() {
            @Override public String name() { return "slow"; }
            @Override public String description() { return "慢工具"; }
            @Override public ToolCategory category() { return ToolCategory.UTILITY; }
            @Override public ToolSchema schema() { return ToolSchema.builder().build(); }
            @Override public Set<String> requiredPermissions() { return Set.of("utility"); }
            @Override public Duration timeout() { return Duration.ofMillis(30); }
            @Override public String execute(Map<String, Object> input) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "done";
            }
        };
        registry = registry(Set.of("utility"), slow);

        ToolResult result = registry.execute("slow", Map.of());

        assertEquals(ToolResult.TIMEOUT, result.status());
        assertEquals(false, result.success());
    }

    @Test
    void shouldReturnNotFoundForUnknownTool() {
        registry = registry(Set.of("utility"), new EchoTool());

        ToolResult result = registry.execute("missing", Map.of());

        assertEquals(ToolResult.NOT_FOUND, result.status());
    }

    @Test
    void agentScopeShouldNarrowVisibleAndCallableTools() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        when(delegate.availableTools()).thenReturn(Set.of("echo", "calculator"));
        AgentScopedToolExecutor scoped = new AgentScopedToolExecutor(delegate, Set.of("echo"));

        assertEquals(Set.of("echo"), scoped.availableTools());
        assertEquals(ToolResult.DENIED, scoped.execute("calculator", Map.of()).status());
        verify(delegate, never()).execute(anyString(), anyMap());
    }

    private ToolRegistry registry(Set<String> granted, Tool... tools) {
        return new ToolRegistry(List.of(tools), properties(granted),
                new ObjectMapper(), mock(ToolCallLogger.class));
    }

    private ToolProperties properties(Set<String> granted) {
        ToolProperties properties = new ToolProperties();
        properties.setGrantedPermissions(granted);
        properties.setPoolSize(2);
        return properties;
    }

    private Tool stubTool(String name, Set<String> permissions,
                          Function<Map<String, Object>, String> body) {
        ToolSchema schema = ToolSchema.builder().optionalString("text", "输入").build();
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "测试工具 " + name; }
            @Override public ToolCategory category() { return ToolCategory.UTILITY; }
            @Override public ToolSchema schema() { return schema; }
            @Override public Set<String> requiredPermissions() { return permissions; }
            @Override public Duration timeout() { return Duration.ofSeconds(1); }
            @Override public String execute(Map<String, Object> input) { return body.apply(input); }
        };
    }
}
