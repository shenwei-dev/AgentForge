package com.example.agentforge.model;

import com.example.agentforge.config.ModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

 
class ModelAdapterRegistryTest {

    @Test
    void switchToShouldChangeActiveAdapter() {
        ModelAdapterRegistry registry = registryWithTwoProviders();

        assertEquals("model-a", registry.current().model());
        assertSame(registry.current(), registry.current(), "适配器应被复用，避免重复创建 HttpClient");

        registry.switchTo("b");
        assertEquals("b", registry.active());
        assertEquals("model-b", registry.current().model());
        assertEquals(Set.of("a", "b"), registry.available());
    }

    @Test
    void getShouldRejectUnknownProvider() {
        ModelAdapterRegistry registry = registryWithTwoProviders();

        ModelException exception = assertThrows(ModelException.class, () -> registry.get("unknown"));
        assertEquals(true, exception.getMessage().contains("未配置的模型提供方"));
    }

    @Test
    void createShouldRejectMissingApiKey() {
        ModelProperties properties = new ModelProperties();
        properties.setActive("a");
        ModelProperties.Provider provider = provider("model-a", "");
        properties.setProviders(Map.of("a", provider));

        ModelAdapterRegistry registry = new ModelAdapterRegistry(properties, new ObjectMapper());
        ModelException exception = assertThrows(ModelException.class, registry::current);
        assertEquals(true, exception.getMessage().contains("缺少 API Key"));
    }

    private ModelAdapterRegistry registryWithTwoProviders() {
        ModelProperties properties = new ModelProperties();
        properties.setActive("a");
        Map<String, ModelProperties.Provider> providers = new LinkedHashMap<>();
        providers.put("a", provider("model-a", "key-a"));
        providers.put("b", provider("model-b", "key-b"));
        properties.setProviders(providers);
        return new ModelAdapterRegistry(properties, new ObjectMapper());
    }

    private ModelProperties.Provider provider(String model, String apiKey) {
        ModelProperties.Provider provider = new ModelProperties.Provider();
        provider.setBaseUrl("http://localhost:9999");
        provider.setApiKey(apiKey);
        provider.setModel(model);
        return provider;
    }
}
