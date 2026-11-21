package com.example.agentforge.chat.dto;

import com.example.agentforge.chat.entity.ChatMessageEntity;

import java.time.LocalDateTime;

 
public record MessageVO(
        String id,
        String conversationId,
        String runId,
        String role,
        String content,
        String contentType,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        Long latencyMs,
        LocalDateTime createdAt) {

    public static MessageVO from(ChatMessageEntity entity) {
        return new MessageVO(entity.getId(), entity.getConversationId(), entity.getRunId(),
                entity.getRole(), entity.getContent(), entity.getContentType(),
                entity.getModelName(), entity.getPromptTokens(), entity.getCompletionTokens(),
                entity.getLatencyMs(), entity.getCreatedAt());
    }
}
