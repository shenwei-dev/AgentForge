package com.example.agentforge.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

 

@Service
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

     
    private final Map<String, Tool> tools = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Set<String> grantedPermissions;
    private final ObjectMapper objectMapper;
    private final ToolCallLogger callLogger;
    private final ExecutorService executor;
    private final Duration defaultTimeout;

    public ToolRegistry(List<Tool> toolBeans, ToolProperties properties,
                        ObjectMapper objectMapper, ToolCallLogger callLogger) {
        for (Tool tool : toolBeans) {
            if (tools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("工具名称重复: " + tool.name());
            }
        }
        this.grantedPermissions = Set.copyOf(properties.getGrantedPermissions());
        this.objectMapper = objectMapper;
        this.callLogger = callLogger;
        this.defaultTimeout = properties.getDefaultTimeout();
        this.executor = Executors.newFixedThreadPool(Math.max(2, properties.getPoolSize()), runnable -> {
            Thread thread = new Thread(runnable, "tool-exec-" + UUID.randomUUID().toString().substring(0, 8));
            thread.setDaemon(true);
            return thread;
        });
        log.info("Tool Registry 初始化完成，共注册 {} 个工具: {}", tools.size(), tools.keySet());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public Set<String> names() {
        synchronized (tools) {
            return Set.copyOf(tools.keySet());
        }
    }

    public List<ToolDescriptor> descriptors() {
        synchronized (tools) {
            return tools.values().stream().map(Tool::descriptor).toList();
        }
    }

     

    public boolean register(Tool tool) {
        synchronized (tools) {
            Tool existing = tools.get(tool.name());
            if (existing != null && existing.category() != ToolCategory.EXTERNAL) {
                return false;
            }
            tools.put(tool.name(), tool);
        }
        log.info("外部工具已注册: {}", tool.name());
        return true;
    }

     
    public String buildToolsPrompt() {
        List<Tool> snapshot;
        synchronized (tools) {
            snapshot = List.copyOf(tools.values());
        }
        StringBuilder builder = new StringBuilder();
        for (Tool tool : snapshot) {
            builder.append("- ").append(tool.name()).append(": ")
                    .append(tool.description()).append('\n');
            try {
                builder.append("  参数 Schema: ")
                        .append(objectMapper.writeValueAsString(tool.schema().toJsonSchema()))
                        .append('\n');
            } catch (JsonProcessingException e) {
                builder.append("  参数 Schema: 不可用\n");
            }
        }
        return builder.toString();
    }

    public ToolResult execute(String toolName, Map<String, Object> input) {
        long start = System.nanoTime();
        String callId = UUID.randomUUID().toString();
        Map<String, Object> safeInput = input == null ? Map.of() : input;

        Tool tool = tools.get(toolName);
        if (tool == null) {
            return finish(callId, toolName, safeInput, ToolResult.notFound(toolName), start);
        }
        if (!grantedPermissions.containsAll(tool.requiredPermissions())) {
            return finish(callId, toolName, safeInput,
                    ToolResult.denied(toolName, "权限不足，需要: " + tool.requiredPermissions()), start);
        }
        List<String> errors = tool.schema().validate(safeInput);
        if (!errors.isEmpty()) {
            return finish(callId, toolName, safeInput,
                    ToolResult.failed(toolName, "参数校验失败: " + String.join("; ", errors)), start);
        }

        Duration timeout = tool.timeout() == null ? defaultTimeout : tool.timeout();
        Future<String> future = executor.submit(() -> tool.execute(safeInput));
        try {
            String content = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return finish(callId, toolName, safeInput, ToolResult.ok(toolName, content), start);
        } catch (TimeoutException e) {
            future.cancel(true);
            return finish(callId, toolName, safeInput,
                    ToolResult.timeout(toolName, "工具执行超时（" + timeout.toMillis() + "ms）"), start);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String message = cause == null ? e.getMessage() : cause.getMessage();
            log.warn("工具执行异常 tool={}", toolName, cause);
            return finish(callId, toolName, safeInput,
                    ToolResult.failed(toolName, "工具执行异常: " + message), start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return finish(callId, toolName, safeInput,
                    ToolResult.failed(toolName, "工具执行被中断"), start);
        } catch (RejectedExecutionException e) {
            return finish(callId, toolName, safeInput,
                    ToolResult.failed(toolName, "工具执行线程池已满"), start);
        }
    }

    private ToolResult finish(String callId, String toolName, Map<String, Object> input,
                              ToolResult result, long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        ToolResult normalized = new ToolResult(result.success(), result.toolName(),
                result.content(), result.errorMessage(), result.status(), durationMs);
        ToolCallContext.Context context = ToolCallContext.current();
        callLogger.record(new ToolCallRecord(callId,
                context == null ? null : context.runId(),
                context == null ? null : context.agentId(),
                toolName, input, result.content(), result.status(),
                result.errorMessage(), durationMs));
        return normalized;
    }
}
