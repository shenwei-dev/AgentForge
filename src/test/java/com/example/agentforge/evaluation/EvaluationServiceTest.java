package com.example.agentforge.evaluation;

import com.example.agentforge.chat.entity.ChatMessageEntity;
import com.example.agentforge.chat.mapper.ChatMessageMapper;
import com.example.agentforge.common.BusinessException;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.config.EvaluationProperties;
import com.example.agentforge.tool.ToolCallEntity;
import com.example.agentforge.tool.mapper.ToolCallMapper;
import com.example.agentforge.trace.ExecutionEventEntity;
import com.example.agentforge.trace.mapper.ExecutionEventMapper;
import com.example.agentforge.workflow.mapper.WorkflowRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

 
class EvaluationServiceTest {

    private final ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
    private final ToolCallMapper toolCallMapper = mock(ToolCallMapper.class);
    private final ExecutionEventMapper eventMapper = mock(ExecutionEventMapper.class);
    private final WorkflowRunMapper workflowRunMapper = mock(WorkflowRunMapper.class);
    private final EvaluationProperties properties = new EvaluationProperties();
    private EvaluationService service;

    @BeforeEach
    void setUp() {
        EvaluationTask task = new EvaluationTask();
        task.setId("t1");
        task.setName("测试任务");
        task.setExpectedKeywords(List.of("答案"));
        properties.setTasks(List.of(task));
        properties.setLatencyThresholdMs(15000);
        properties.setMaxRoundsThreshold(6);
        properties.setPassScore(60);
        service = new EvaluationService(chatMessageMapper, toolCallMapper,
                eventMapper, workflowRunMapper, properties);
    }

    @Test
    void metricsShouldAggregateTokensRoundsLatencyAndStatus() {
        LocalDateTime start = LocalDateTime.now().minusSeconds(2);
        stub(List.of(
                        assistant("答案内容", "qianwen/deepseek-v4.1-flash", 10, 5, 120L)),
                List.of(tool("calculator", "SUCCEEDED")),
                List.of(event(ExecutionEvent.RUN_STARTED, "INFO", start),
                        event(ExecutionEvent.ROUND_STARTED, "INFO", start.plusSeconds(1)),
                        event(ExecutionEvent.RUN_COMPLETED, "INFO", start.plusSeconds(2))));

        RunMetrics metrics = service.metrics("run-1");

        assertEquals("qianwen/deepseek-v4.1-flash", metrics.modelName());
        assertEquals(1, metrics.modelCallCount());
        assertEquals(15, metrics.totalTokens());
        assertEquals(1, metrics.toolCallCount());
        assertEquals(1, metrics.toolSuccessCount());
        assertEquals(1, metrics.agentRounds());
        assertEquals(120L, metrics.modelLatencyMs());
        assertTrue(metrics.wallClockMs() >= 1900);
        assertTrue(metrics.hasFinalAnswer());
        assertEquals("SUCCEEDED", metrics.status());
        assertEquals(0, metrics.errorCount());
    }

    @Test
    void evaluateShouldGiveFullScoreWhenAllCriteriaMet() {
        LocalDateTime now = LocalDateTime.now();
        stub(List.of(assistant("这是答案", "model-a", 10, 5, 100L)),
                List.of(),
                List.of(event(ExecutionEvent.RUN_COMPLETED, "INFO", now)));

        EvaluationResult result = service.evaluate("run-1", "t1");

        assertEquals(100, result.score());
        assertTrue(result.passed());
        assertTrue(result.reasons().contains("评测通过"));
    }

    @Test
    void evaluateShouldDeductForMissingAnswerFailedToolAndRunFailure() {
        EvaluationTask task = new EvaluationTask();
        task.setId("t2");
        task.setName("工具任务");
        task.setExpectedTool("calculator");
        task.setExpectedKeywords(List.of("答案"));
        properties.setTasks(List.of(task));
        LocalDateTime now = LocalDateTime.now();
        stub(List.of(user("请计算")),
                List.of(tool("calculator", "FAILED")),
                List.of(event(ExecutionEvent.RUN_FAILED, "ERROR", now)));

        EvaluationResult result = service.evaluate("run-2", "t2");

        assertFalse(result.passed());
        assertTrue(result.score() < 60);
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("缺少最终答案")));
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("存在失败事件")));
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("未成功调用期望工具")));
        assertEquals("FAILED", result.metrics().status());
    }

    @Test
    void evaluateShouldRejectUnknownTask() {
        stub(List.of(), List.of(), List.of());

        assertThrows(BusinessException.class,
                () -> service.evaluate("run-1", "not-exist"));
    }

    @Test
    void summaryShouldComputeSuccessRateAndFailureDistribution() {
        stub(List.of(assistant("答案", "model-a", 10, 5, 100L)),
                List.of(tool("calculator", "SUCCEEDED")),
                List.of(event(ExecutionEvent.RUN_COMPLETED, "INFO", LocalDateTime.now()),
                        event(ExecutionEvent.RUN_FAILED, "ERROR", LocalDateTime.now())));

        EvaluationSummary summary = service.summary();

        assertEquals(1, summary.totalRuns());
        assertEquals(0.0, summary.successRate());
        assertEquals(15, summary.totalTokens());
        assertEquals(1.0, summary.toolSuccessRate());
        assertTrue(summary.failureReasons().containsKey(ExecutionEvent.RUN_FAILED));
    }

    @Test
    void compareModelsShouldGroupByModelName() {
        ChatMessageEntity fast = assistant("A", "model-a", 10, 5, 100L);
        fast.setRunId("run-1");
        ChatMessageEntity slow = assistant("B", "model-b", 20, 10, 300L);
        slow.setRunId("run-2");
        ExecutionEventEntity completed = event(ExecutionEvent.RUN_COMPLETED, "INFO", LocalDateTime.now());
        completed.setRunId("run-1");
        ExecutionEventEntity failed = event(ExecutionEvent.RUN_FAILED, "ERROR", LocalDateTime.now());
        failed.setRunId("run-2");
        stub(List.of(slow, fast), List.of(), List.of(failed, completed));

        List<ModelComparison> comparisons = service.compareModels();

        assertEquals(2, comparisons.size());
        assertEquals("model-a", comparisons.get(0).modelName());
        assertEquals(1.0, comparisons.get(0).successRate());
        assertEquals(0.0, comparisons.get(1).successRate());
    }

    private void stub(List<ChatMessageEntity> messages, List<ToolCallEntity> tools,
                      List<ExecutionEventEntity> events) {
        when(chatMessageMapper.selectList(any())).thenReturn(messages);
        when(toolCallMapper.selectList(any())).thenReturn(tools);
        when(eventMapper.selectList(any())).thenReturn(events);
    }

    private ChatMessageEntity assistant(String content, String model,
                                        int prompt, int completion, long latency) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setRunId("run-1");
        entity.setRole("ASSISTANT");
        entity.setContentType("MARKDOWN");
        entity.setContent(content);
        entity.setModelName(model);
        entity.setPromptTokens(prompt);
        entity.setCompletionTokens(completion);
        entity.setLatencyMs(latency);
        return entity;
    }

    private ChatMessageEntity user(String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setRunId("run-2");
        entity.setRole("USER");
        entity.setContentType("TEXT");
        entity.setContent(content);
        return entity;
    }

    private ToolCallEntity tool(String name, String status) {
        ToolCallEntity entity = new ToolCallEntity();
        entity.setToolName(name);
        entity.setStatus(status);
        return entity;
    }

    private ExecutionEventEntity event(String type, String level, LocalDateTime time) {
        ExecutionEventEntity entity = new ExecutionEventEntity();
        entity.setRunId("run-1");
        entity.setEventType(type);
        entity.setLevel(level);
        entity.setMessage(type);
        entity.setCreatedAt(time);
        return entity;
    }
}
