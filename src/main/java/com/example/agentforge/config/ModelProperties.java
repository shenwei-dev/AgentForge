package com.example.agentforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

 

@Component
@ConfigurationProperties(prefix = "agentforge.model")
public class ModelProperties {

    private String active = "deepseek";

     
    private Map<String, Provider> providers = new LinkedHashMap<>();

    public Provider resolve(String name) {
        Provider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("未配置的模型提供方: " + name + "，可选: " + providers.keySet());
        }
        return provider;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(providers);
    }

    public static class Provider {

        private String baseUrl;
        private String apiKey;
        private String model;
        private Double temperature = 0.7;
        private Integer maxTokens = 4096;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(180);
        private Capabilities capabilities = new Capabilities();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Capabilities getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(Capabilities capabilities) {
            this.capabilities = capabilities == null ? new Capabilities() : capabilities;
        }
    }

     
    public static class Capabilities {

        private boolean functionCalling;
        private boolean structuredOutput;
        private boolean streaming = true;
        private int contextWindow = 64000;

        public boolean isFunctionCalling() {
            return functionCalling;
        }

        public void setFunctionCalling(boolean functionCalling) {
            this.functionCalling = functionCalling;
        }

        public boolean isStructuredOutput() {
            return structuredOutput;
        }

        public void setStructuredOutput(boolean structuredOutput) {
            this.structuredOutput = structuredOutput;
        }

        public boolean isStreaming() {
            return streaming;
        }

        public void setStreaming(boolean streaming) {
            this.streaming = streaming;
        }

        public int getContextWindow() {
            return contextWindow;
        }

        public void setContextWindow(int contextWindow) {
            this.contextWindow = contextWindow;
        }
    }
}
