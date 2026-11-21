package com.example.agentforge.mcp;

import com.example.agentforge.config.McpProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

 

class StdioMcpTransportIntegrationTest {

    @Test
    void shouldTalkToRealMcpServerOverStdio() throws Exception {
        Assumptions.assumeTrue(nodeAvailable(), "Node.js 不可用，跳过真实 MCP 集成测试");

        McpProperties.Server server = new McpProperties.Server();
        server.setName("demo");
        server.setCommand("node");
        server.setArgs(List.of("mcp/echo-server.mjs"));
        ObjectMapper mapper = new ObjectMapper();

        try (McpClient client = new McpClient(
                new StdioMcpTransport(server, mapper), mapper, Duration.ofSeconds(5))) {
            List<McpToolDefinition> tools = client.listTools();

            assertEquals(3, tools.size());
            assertTrue(tools.stream().anyMatch(t -> t.name().equals("echo")));
            assertEquals("Echo: hello", client.callTool("echo", Map.of("text", "hello")));
            assertEquals("ABC", client.callTool("upper", Map.of("text", "abc")));
            assertEquals("3", client.callTool("sum", Map.of("a", 1, "b", 2)));
        }
    }

    private boolean nodeAvailable() {
        try {
            Process process = new ProcessBuilder("node", "--version").start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
