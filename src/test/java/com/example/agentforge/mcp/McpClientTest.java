package com.example.agentforge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class McpClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldInitializeOnceAndDiscoverTools() throws Exception {
        StubTransport transport = new StubTransport();
        transport.respond("initialize", node("""
                {"protocolVersion":"2024-11-05","serverInfo":{"name":"demo"}}"""));
        transport.respond("tools/list", node("""
                {"tools":[{"name":"echo","description":"回显",
                  "inputSchema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}}]}"""));
        McpClient client = new McpClient(transport, mapper, Duration.ofSeconds(2));

        List<McpToolDefinition> tools = client.listTools();
        client.listTools();

        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).name());
        assertEquals(1, transport.methods.stream().filter("initialize"::equals).count());
        assertTrue(transport.methods.contains("notifications/initialized"));
    }

    @Test
    void shouldFlattenTextContentAndRejectIsError() throws Exception {
        StubTransport transport = new StubTransport();
        transport.respond("initialize", node("{}"));
        transport.respond("tools/call", node("""
                {"content":[{"type":"text","text":"第一行"},{"type":"text","text":"第二行"}]}"""));
        McpClient client = new McpClient(transport, mapper, Duration.ofSeconds(2));

        assertEquals("第一行\n第二行", client.callTool("echo", Map.of("text", "x")));

        StubTransport failing = new StubTransport();
        failing.respond("initialize", node("{}"));
        failing.respond("tools/call", node("""
                {"content":[{"type":"text","text":"参数非法"}],"isError":true}"""));
        assertThrows(IllegalStateException.class, () -> new McpClient(failing, mapper,
                Duration.ofSeconds(2)).callTool("echo", Map.of()));
    }

    @Test
    void shouldPropagateJsonRpcError() throws Exception {
        StubTransport transport = new StubTransport();
        transport.failWith = "MCP 返回错误: {\"code\":-32601}";
        McpClient client = new McpClient(transport, mapper, Duration.ofSeconds(2));

        assertThrows(IllegalStateException.class, client::initialize);
    }

    private JsonNode node(String json) throws Exception {
        return mapper.readTree(json);
    }

     
    private static final class StubTransport implements McpTransport {

        private final Map<String, JsonNode> responses = new LinkedHashMap<>();
        private final List<String> methods = new ArrayList<>();
        private String failWith;

        private void respond(String method, JsonNode result) {
            responses.put(method, result);
        }

        @Override
        public JsonNode request(String method, Map<String, Object> params, Duration timeout)
                throws Exception {
            methods.add(method);
            if (failWith != null) {
                throw new IllegalStateException(failWith);
            }
            JsonNode result = responses.get(method);
            if (result == null) {
                throw new IOException("未预设响应: " + method);
            }
            return result;
        }

        @Override
        public void notify(String method, Map<String, Object> params) {
            methods.add(method);
        }

        @Override
        public void close() {
        }
    }
}
