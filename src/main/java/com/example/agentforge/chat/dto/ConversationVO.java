package com.example.agentforge.chat.dto;

import com.example.agentforge.chat.entity.Conversation;

import java.time.LocalDateTime;

 
public record ConversationVO(
        String id,
        String title,
        String status,
        String modelProvider,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt) {

    public static ConversationVO from(Conversation entity) {
        return new ConversationVO(entity.getId(), entity.getTitle(), entity.getStatus(),
                entity.getModelProvider(), entity.getLastMessageAt(), entity.getCreatedAt());
    }
}
