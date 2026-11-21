package com.example.agentforge.model;

import com.example.agentforge.config.ModelProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

 

public class OpenAiCompatibleAdapter implements ModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAdapter.class);
    private static final String DONE_MARKER = "[DONE]";

    private final String provider;
    private final ModelProperties.Provider config;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleAdapter(String provider, ModelProperties.Provider config, ObjectMapper mapper) {
        this.provider = provider;
        this.config = config;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public String model() {
        return config.getModel();
    }

    @Override
    public ModelCapabilities capabilities() {
        ModelProperties.Capabilities c = config.getCapabilities();
        return new ModelCapabilities(c.isFunctionCalling(), c.isStructuredOutput(),
                c.isStreaming(), c.getContextWindow());
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        long start = System.nanoTime();
        String requestBody = buildRequestBody(request, false);
        try {
            HttpResponse<String> response = httpClient.send(
                    buildHttpRequest(requestBody, false),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body();
            requireSuccess(response.statusCode(), responseBody);

            JsonNode root = mapper.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
            String content = choice.path("message").path("content").asText("");
            String finishReason = textOrNull(choice.path("finish_reason"));
            TokenUsage usage = parseUsage(root.path("usage"));
            long latencyMs = elapsedMs(start);
            log.debug("模型调用完成 provider={} model={} latencyMs={} totalTokens={}",
                    provider, config.getModel(), latencyMs,
                    usage == null ? null : usage.totalTokens());
            return new ModelResponse(content, finishReason, usage, latencyMs, provider, config.getModel());
        } catch (JsonProcessingException e) {
            throw new ModelException("解析模型响应失败: " + e.getOriginalMessage(),
                    provider, 200, null, e);
        } catch (IOException e) {
            throw new ModelException("模型调用 IO 异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException("模型调用被中断", e);
        }
    }

    @Override
    public void chatStream(ModelRequest request, Consumer<StreamChunk> chunkConsumer) {
        
        if (!capabilities().streaming()) {
            ModelResponse response = chat(request);
            chunkConsumer.accept(new StreamChunk(response.content(), response.finishReason(),
                    response.usage(), false));
            chunkConsumer.accept(StreamChunk.finished());
            return;
        }

        long start = System.nanoTime();
        String requestBody = buildRequestBody(request, true);
        try {
            HttpResponse<Stream<String>> response = httpClient.send(
                    buildHttpRequest(requestBody, true),
                    HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() / 100 != 2) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                throw new ModelException("模型流式调用失败: HTTP " + response.statusCode(),
                        provider, response.statusCode(), errorBody);
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> handleSseLine(line, chunkConsumer));
            }
            chunkConsumer.accept(StreamChunk.finished());
            log.debug("模型流式调用结束 provider={} model={} latencyMs={}",
                    provider, config.getModel(), elapsedMs(start));
        } catch (IOException e) {
            throw new ModelException("模型流式调用 IO 异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException("模型流式调用被中断", e);
        }
    }

     
    private void handleSseLine(String line, Consumer<StreamChunk> chunkConsumer) {
        if (line == null || line.isBlank() || line.startsWith(":")) {
            return;
        }
        if (!line.startsWith("data:")) {
            return;
        }
        String data = line.substring("data:".length()).trim();
        if (data.isEmpty() || DONE_MARKER.equals(data)) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(data);
            if (root.hasNonNull("error")) {
                throw new ModelException("模型流式响应返回错误: " + root.path("error"),
                        provider, 200, data);
            }
            JsonNode choice = root.path("choices").path(0);
            TokenUsage usage = parseUsage(root.path("usage"));
            if (choice.isMissingNode()) {
                
                if (usage != null) {
                    chunkConsumer.accept(new StreamChunk("", null, usage, false));
                }
                return;
            }
            String delta = choice.path("delta").path("content").asText("");
            String finishReason = textOrNull(choice.path("finish_reason"));
            chunkConsumer.accept(new StreamChunk(delta, finishReason, usage, false));
        } catch (JsonProcessingException e) {
            throw new ModelException("无法解析模型流式数据: " + data, provider, 200, data, e);
        }
    }

    private String buildRequestBody(ModelRequest request, boolean stream) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", config.getModel());
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode node = messages.addObject();
            node.put("role", message.role());
            node.put("content", message.content());
        }
        double temperature = request.temperature() == null ? config.getTemperature() : request.temperature();
        int maxTokens = request.maxTokens() == null ? config.getMaxTokens() : request.maxTokens();
        root.put("temperature", temperature);
        root.put("max_tokens", maxTokens);
        root.put("stream", stream);
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ModelException("构造模型请求体失败", e);
        }
    }

    private HttpRequest buildHttpRequest(String json, boolean stream) {
        return HttpRequest.newBuilder()
                .uri(URI.create(resolveCompletionsUrl()))
                .timeout(config.getReadTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

     
    private String resolveCompletionsUrl() {
        String base = config.getBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/v1")
                ? base + "/chat/completions"
                : base + "/v1/chat/completions";
    }

    private void requireSuccess(int statusCode, String responseBody) {
        if (statusCode / 100 != 2) {
            throw new ModelException("模型调用失败: HTTP " + statusCode, provider, statusCode, responseBody);
        }
    }

    private TokenUsage parseUsage(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        int prompt = node.path("prompt_tokens").asInt(0);
        int completion = node.path("completion_tokens").asInt(0);
        int total = node.path("total_tokens").asInt(prompt + completion);
        if (prompt == 0 && completion == 0 && total == 0) {
            return null;
        }
        return new TokenUsage(prompt, completion, total);
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.asText();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
