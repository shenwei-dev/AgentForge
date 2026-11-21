package com.example.agentforge.mcp;

import com.example.agentforge.tool.Tool;
import com.example.agentforge.tool.ToolCategory;
import com.example.agentforge.tool.ToolSchema;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

 

public class McpToolAdapter implements Tool {

    private final String exposedName;
    private final String remoteName;
    private final String serverName;
    private final String description;
    private final ToolSchema schema;
    private final String permission;
    private final McpClient client;
    private final Duration timeout;

    public McpToolAdapter(String exposedName, String remoteName, String serverName,
                          String description, ToolSchema schema, String permission,
                          McpClient client, Duration timeout) {
        this.exposedName = exposedName;
        this.remoteName = remoteName;
        this.serverName = serverName;
        this.description = description;
        this.schema = schema;
        this.permission = permission;
        this.client = client;
        this.timeout = timeout;
    }

    public String remoteName() {
        return remoteName;
    }

    public String serverName() {
        return serverName;
    }

    @Override
    public String name() {
        return exposedName;
    }

    @Override
    public String description() {
        return description + "（MCP Server: " + serverName + "）";
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.EXTERNAL;
    }

    @Override
    public ToolSchema schema() {
        return schema;
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of(permission);
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public String execute(Map<String, Object> input) {
        try {
            return client.callTool(remoteName, input);
        } catch (Exception e) {
            throw new IllegalStateException("MCP 工具调用失败: " + e.getMessage(), e);
        }
    }
}
