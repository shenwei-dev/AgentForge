package com.example.agentforge.model;

import com.example.agentforge.config.ModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

 

@Service
public class ModelAdapterRegistry {

    private final ModelProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, ModelAdapter> adapters = new ConcurrentHashMap<>();
    private volatile String active;

    public ModelAdapterRegistry(ModelProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.active = properties.getActive();
    }

    public ModelAdapter current() {
        return get(active);
    }

    public ModelAdapter get(String providerName) {
        String name = StringUtils.hasText(providerName) ? providerName : active;
        return adapters.computeIfAbsent(name, this::create);
    }

    private ModelAdapter create(String name) {
        ModelProperties.Provider config = properties.getProviders().get(name);
        if (config == null) {
            throw new ModelException("未配置的模型提供方: " + name
                    + "，可选: " + properties.getProviders().keySet());
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new ModelException("模型提供方 " + name + " 缺少 API Key，请设置对应环境变量");
        }
        if (!StringUtils.hasText(config.getBaseUrl()) || !StringUtils.hasText(config.getModel())) {
            throw new ModelException("模型提供方 " + name + " 的 base-url / model 未配置");
        }
        return new OpenAiCompatibleAdapter(name, config, objectMapper);
    }

     
    public synchronized void switchTo(String name) {
        get(name);
        this.active = name;
    }

    public String active() {
        return active;
    }

    public Set<String> available() {
        return Collections.unmodifiableSet(properties.getProviders().keySet());
    }

     
    public synchronized void refresh() {
        adapters.clear();
    }
}
