package com.example.agentforge.mcp;

import com.example.agentforge.common.BusinessException;
import com.example.agentforge.config.McpProperties;
import com.example.agentforge.tool.ToolDescriptor;
import com.example.agentforge.tool.ToolRegistry;
import com.example.agentforge.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

 

@Service
public class McpToolService {

    private static final Logger log = LoggerFactory.getLogger(McpToolService.class);

    private final McpProperties properties;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper;
    private final Map<String, McpToolAdapter> adapters = new ConcurrentHashMap<>();
    private final List<McpClient> clients = new CopyOnWriteArrayList<>();
    private final List<ToolDescriptor> discovered = new CopyOnWriteArrayList<>();

    public McpToolService(McpProperties properties, ToolRegistry toolRegistry,
                          ObjectMapper mapper) {
        this.properties = properties;
        this.toolRegistry = toolRegistry;
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                discoverAndRegister();
            } catch (Exception e) {
                log.warn("MCP 自动发现失败，可用 /api/mcp/refresh 重试", e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        closeClients();
    }

     
    public synchronized List<ToolDescriptor> discoverAndRegister() {
        closeClients();
        adapters.clear();
        discovered.clear();
        List<ToolDescriptor> result = new ArrayList<>();
        if (!properties.isEnabled()) {
            return result;
        }
        for (McpProperties.Server server : properties.getServers()) {
            if (!server.isEnabled() || !StringUtils.hasText(server.getCommand())) {
                continue;
            }
            McpClient client = null;
            try {
                McpTransport transport = new StdioMcpTransport(server, mapper);
                client = new McpClient(transport, mapper, properties.getRequestTimeout());
                List<McpToolDefinition> definitions = client.listTools();
                clients.add(client);
                for (McpToolDefinition definition : definitions) {
                    if (!isAllowed(server, definition.name())) {
                        continue;
                    }
                    String exposedName = prefix(server) + definition.name();
                    McpToolAdapter adapter = new McpToolAdapter(
                            exposedName, definition.name(), server.getName(),
                            definition.description(),
                            McpSchemaConverter.convert(definition.inputSchema()),
                            server.getPermission(), client, properties.getRequestTimeout());
                    if (!toolRegistry.register(adapter)) {
                        log.warn("MCP 工具名称冲突，跳过注册: {}", exposedName);
                        continue;
                    }
                    adapters.put(exposedName, adapter);
                    result.add(adapter.descriptor());
                }
                log.info("MCP Server 发现完成 name={} tools={}", server.getName(), definitions.size());
            } catch (Exception e) {
                log.warn("MCP Server 连接或发现失败 name={}: {}", server.getName(), e.getMessage());
                if (client != null) {
                    client.close();
                }
            }
        }
        discovered.addAll(result);
        return result;
    }

    public List<ToolDescriptor> discovered() {
        return List.copyOf(discovered);
    }

     

    public String call(String exposedName, Map<String, Object> arguments) {
        if (!adapters.containsKey(exposedName)) {
            throw new BusinessException(404, "未注册的 MCP 工具: " + exposedName);
        }
        ToolResult result = toolRegistry.execute(exposedName, arguments);
        if (!result.success()) {
            throw new BusinessException(502, "MCP 工具调用失败: " + result.errorMessage());
        }
        return result.content();
    }

    private boolean isAllowed(McpProperties.Server server, String remoteName) {
        List<String> allow = server.getAllow();
        return allow == null || allow.isEmpty() || allow.contains("*") || allow.contains(remoteName);
    }

    private String prefix(McpProperties.Server server) {
        return StringUtils.hasText(server.getToolPrefix()) ? server.getToolPrefix() : "mcp_";
    }

    private void closeClients() {
        for (McpClient client : clients) {
            client.close();
        }
        clients.clear();
    }
}
