package com.example.agentforge.mcp;

import com.example.agentforge.config.McpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

 

public class StdioMcpTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpTransport.class);

    private final ObjectMapper mapper;
    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong();
    private volatile boolean closed;

    public StdioMcpTransport(McpProperties.Server server, ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        List<String> command = new ArrayList<>();
        command.add(server.getCommand());
        command.addAll(server.getArgs());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(new File(System.getProperty("user.dir")));
        if (server.getEnv() != null && !server.getEnv().isEmpty()) {
            builder.environment().putAll(server.getEnv());
        }
        builder.redirectErrorStream(false);
        this.process = builder.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8));
        startStdoutReader(server.getName());
        startStderrReader(server.getName());
    }

    @Override
    public JsonNode request(String method, Map<String, Object> params, Duration timeout)
            throws Exception {
        ensureOpen();
        long id = idGenerator.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", mapper.valueToTree(params == null ? Map.of() : params));
        try {
            write(request);
            JsonNode response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.hasNonNull("error")) {
                throw new IllegalStateException("MCP 返回错误: " + response.get("error"));
            }
            return response.path("result");
        } catch (TimeoutException e) {
            throw new TimeoutException("MCP 请求超时: " + method);
        } finally {
            pending.remove(id);
        }
    }

    @Override
    public void notify(String method, Map<String, Object> params) throws IOException {
        ensureOpen();
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", mapper.valueToTree(params == null ? Map.of() : params));
        write(notification);
    }

    private void write(ObjectNode message) throws IOException {
        synchronized (writer) {
            if (closed) {
                throw new IOException("MCP 传输已关闭");
            }
            writer.write(mapper.writeValueAsString(message));
            writer.write("\n");
            writer.flush();
        }
    }

    private void startStdoutReader(String serverName) {
        Thread thread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        JsonNode message = mapper.readTree(line);
                        if (message.hasNonNull("id")) {
                            CompletableFuture<JsonNode> future = pending.remove(message.get("id").asLong());
                            if (future != null) {
                                future.complete(message);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("MCP 响应解析失败 server={} line={}", serverName, line, e);
                    }
                }
            } catch (IOException e) {
                if (!closed) {
                    log.warn("MCP stdout 读取结束 server={}", serverName, e);
                }
            } finally {
                pending.values().forEach(future -> future.completeExceptionally(
                        new IOException("MCP Server 进程已退出")));
                pending.clear();
            }
        }, "mcp-stdout-" + serverName);
        thread.setDaemon(true);
        thread.start();
    }

    private void startStderrReader(String serverName) {
        Thread thread = new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(
                    process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    log.debug("[MCP {}] {}", serverName, line);
                }
            } catch (IOException ignored) {
                
            }
        }, "mcp-stderr-" + serverName);
        thread.setDaemon(true);
        thread.start();
    }

    private void ensureOpen() throws IOException {
        if (closed || !process.isAlive()) {
            throw new IOException("MCP Server 进程不可用");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending.values().forEach(future -> future.completeExceptionally(
                new IOException("MCP 传输已关闭")));
        pending.clear();
        try {
            writer.close();
        } catch (IOException ignored) {
            
        }
        try {
            reader.close();
        } catch (IOException ignored) {
            
        }
        process.destroy();
    }
}
