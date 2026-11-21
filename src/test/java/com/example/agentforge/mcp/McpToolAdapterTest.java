package com.example.agentforge.mcp;

import com.example.agentforge.tool.ToolCategory;
import com.example.agentforge.tool.ToolSchema;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

 
class McpToolAdapterTest {

    @Test
    void shouldDelegateToRemoteToolAndDeclareExternalPermission() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.callTool(eq("echo"), anyMap())).thenReturn("Echo: hi");
        ToolSchema schema = ToolSchema.builder()
                .requiredString("text", "文本")
                .build();

        McpToolAdapter adapter = new McpToolAdapter("mcp_demo_echo", "echo", "demo",
                "回显", schema, "mcp:demo", client, Duration.ofSeconds(5));

        assertEquals("mcp_demo_echo", adapter.name());
        assertEquals("echo", adapter.remoteName());
        assertEquals(ToolCategory.EXTERNAL, adapter.category());
        assertEquals(Set.of("mcp:demo"), adapter.requiredPermissions());
        assertEquals("Echo: hi", adapter.execute(Map.of("text", "hi")));
    }
}
