package com.example.agentforge.model;

import java.util.List;

 

public record ModelRequest(List<ChatMessage> messages, Double temperature, Integer maxTokens) {

    public ModelRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("模型请求的消息列表不能为空");
        }
        messages = List.copyOf(messages);
    }

    public static ModelRequest of(List<ChatMessage> messages) {
        return new ModelRequest(messages, null, null);
    }

    public static ModelRequest of(ChatMessage... messages) {
        return new ModelRequest(List.of(messages), null, null);
    }

    public ModelRequest withOverrides(Double temperature, Integer maxTokens) {
        return new ModelRequest(messages, temperature, maxTokens);
    }
}
