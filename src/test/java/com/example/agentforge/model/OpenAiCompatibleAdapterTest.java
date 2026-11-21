package com.example.agentforge.model;

import com.example.agentforge.config.ModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 

class OpenAiCompatibleAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    private HttpServer server;
    private ModelProperties.Provider config;
    private OpenAiCompatibleAdapter adapter;
    private volatile int stubStatus = 200;
    private volatile String stubResponse = "";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();

        config = new ModelProperties.Provider();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setApiKey("test-key");
        config.setModel("stub-model");
        config.setConnectTimeout(Duration.ofSeconds(2));
        config.setReadTimeout(Duration.ofSeconds(5));
        adapter = new OpenAiCompatibleAdapter("stub", config, mapper);
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = stubResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(stubStatus, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chatShouldParseContentUsageAndFinishReason() {
        stubResponse = """
                {"choices":[{"message":{"content":"你好，我是 AgentForge"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20}}
                """;

        ModelResponse response = adapter.chat(ModelRequest.of(
                ChatMessage.system("你是一个助手"), ChatMessage.user("你好")));

        assertEquals("你好，我是 AgentForge", response.content());
        assertEquals("stop", response.finishReason());
        assertEquals(20, response.usage().totalTokens());
        assertEquals("stub", response.provider());
        assertEquals("stub-model", response.model());
        assertTrue(response.latencyMs() >= 0);

        String body = lastRequestBody.get();
        assertTrue(body.contains("\"stream\":false"));
        assertTrue(body.contains("\"model\":\"stub-model\""));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.contains("\"role\":\"user\""));
    }

    @Test
    void chatStreamShouldEmitDeltasThenDone() {
        stubResponse = ": ping\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"Spring AI\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\" 对比\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}\n\n"
                + "data: [DONE]\n\n";

        List<StreamChunk> chunks = new ArrayList<>();
        adapter.chatStream(ModelRequest.of(ChatMessage.user("对比一下")), chunks::add);

        String text = chunks.stream()
                .filter(chunk -> !chunk.done())
                .map(StreamChunk::delta)
                .collect(Collectors.joining());
        assertEquals("Spring AI 对比", text);
        assertEquals(15, chunks.get(1).usage().totalTokens());
        assertEquals("stop", chunks.get(1).finishReason());
        assertTrue(chunks.get(chunks.size() - 1).done());
        assertTrue(lastRequestBody.get().contains("\"stream\":true"));
    }

    @Test
    void chatShouldExposeHttpErrorAndNotRetryAuthFailure() {
        stubStatus = 401;
        stubResponse = "{\"error\":{\"message\":\"invalid api key\"}}";

        ModelException exception = assertThrows(ModelException.class,
                () -> adapter.chat(ModelRequest.of(ChatMessage.user("hi"))));

        assertEquals(401, exception.getStatusCode());
        assertTrue(exception.getResponseBody().contains("invalid api key"));
        assertFalse(exception.isRetryable());
    }

    @Test
    void chatStreamShouldSurfaceModelErrorChunk() {
        stubResponse = "data: {\"error\":{\"message\":\"rate limited\"}}\n\n";

        ModelException exception = assertThrows(ModelException.class,
                () -> adapter.chatStream(ModelRequest.of(ChatMessage.user("hi")), chunk -> { }));

        assertTrue(exception.getMessage().contains("rate limited"));
    }

    @Test
    void chatStreamShouldFallbackWhenStreamingUnsupported() {
        ModelProperties.Capabilities capabilities = new ModelProperties.Capabilities();
        capabilities.setStreaming(false);
        config.setCapabilities(capabilities);
        stubResponse = "{\"choices\":[{\"message\":{\"content\":\"降级输出\"},\"finish_reason\":\"stop\"}]}";

        List<StreamChunk> chunks = new ArrayList<>();
        adapter.chatStream(ModelRequest.of(ChatMessage.user("hi")), chunks::add);

        assertEquals(2, chunks.size());
        assertEquals("降级输出", chunks.get(0).delta());
        assertTrue(chunks.get(1).done());
        assertTrue(lastRequestBody.get().contains("\"stream\":false"));
    }
}
