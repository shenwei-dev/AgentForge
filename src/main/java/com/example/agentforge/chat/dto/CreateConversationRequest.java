package com.example.agentforge.chat.dto;

import jakarta.validation.constraints.Size;

 
public record CreateConversationRequest(
        @Size(max = 200, message = "标题最长 200 个字符") String title) {
}
