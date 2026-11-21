package com.example.agentforge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

 

public class McpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpTransport transport;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private volatile boolean initialized;
    private volatile boolean closed;

    public McpClient(McpTransport transport, ObjectMapper mapper, Duration timeout) {
        this.transport = transport;
        this.mapper = mapper;
        this.timeout = timeout;
    }

    public synchronized void initialize() throws Exception {
        if (initialized) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", "AgentForge", "version", "1.0.0"));
        JsonNode result = transport.request("initialize", params, timeout);
        log.debug("MCP 初始化完成 protocolVersion={} serverInfo={}",
                result.path("protocolVersion").asText(""), result.path("serverInfo"));
        transport.notify("notifications/initialized", Map.of());
        initialized = true;
    }

    public List<McpToolDefinition> listTools() throws Exception {
        initialize();
        JsonNode result = transport.request("tools/list", Map.of(), timeout);
        List<McpToolDefinition> definitions = new ArrayList<>();
        JsonNode tools = result.path("tools");
        if (tools.isArray()) {
            for (JsonNode tool : tools) {
                definitions.add(new McpToolDefinition(
                        tool.path("name").asText(""),
                        tool.path("description").asText(""),
                        tool.path("inputSchema")));
            }
        }
        return definitions;
    }

     
    public String callTool(String remoteName, Map<String, Object> arguments) throws Exception {
        initialize();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", remoteName);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        JsonNode result = transport.request("tools/call", params, timeout);
        if (result.path("isError").asBoolean(false)) {
            throw new IllegalStateException("MCP 工具返回错误: " + flatten(result));
        }
        return flatten(result);
    }

    private String flatten(JsonNode result) {
        StringBuilder builder = new StringBuilder();
        JsonNode content = result.path("content");
        if (content.isArray()) {
            for (JsonNode item : content) {
                String type = item.path("type").asText("text");
                if ("text".equals(type)) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(item.path("text").asText(""));
                } else {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append("[不支持的 MCP 内容类型: ").append(type).append(']');
                }
            }
        }
        return builder.toString();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        transport.close();
    }
}
