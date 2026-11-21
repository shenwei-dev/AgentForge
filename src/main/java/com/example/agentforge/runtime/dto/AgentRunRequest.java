package com.example.agentforge.runtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

 
public record AgentRunRequest(
        @Size(max = 64, message = "会话 ID 最长 64 个字符") String conversationId,
        @NotBlank(message = "任务内容不能为空")
        @Size(max = 8000, message = "任务内容最长 8000 个字符") String message,
        @Size(max = 64, message = "模型提供方名称最长 64 个字符") String provider) {
}
