package com.example.agentforge.chat.service;

import com.example.agentforge.chat.entity.ChatMessageEntity;
import com.example.agentforge.chat.mapper.ChatMessageMapper;
import com.example.agentforge.chat.mapper.ConversationMapper;
import com.example.agentforge.common.BusinessException;
import com.example.agentforge.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

 
class ConversationServiceTest {

    @Test
    void loadRecentHistoryShouldFilterToolRoleAndRestoreChronologicalOrder() {
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        
        when(messageMapper.selectList(any())).thenReturn(List.of(
                entity("ASSISTANT", "回答2"),
                entity("USER", "问题2"),
                entity("ASSISTANT", "回答1"),
                entity("TOOL", "工具输出"),
                entity("USER", "问题1")));

        ConversationService service = new ConversationService(conversationMapper, messageMapper);
        List<ChatMessage> history = service.loadRecentHistory("c1");

        assertEquals(4, history.size(), "TOOL 角色不应进入模型上下文");
        assertEquals("user", history.get(0).role());
        assertEquals("问题1", history.get(0).content());
        assertEquals("assistant", history.get(3).role());
        assertEquals("回答2", history.get(3).content());
    }

    @Test
    void loadRecentHistoryShouldTruncateOverlongMessage() {
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        when(messageMapper.selectList(any()))
                .thenReturn(List.of(entity("USER", "x".repeat(5000))));

        ConversationService service = new ConversationService(mock(ConversationMapper.class), messageMapper);
        ChatMessage message = service.loadRecentHistory("c1").get(0);

        assertTrue(message.content().length() < 5000);
        assertTrue(message.content().contains("[历史消息已截断]"));
    }

    @Test
    void getOrCreateShouldRejectUnknownConversation() {
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        when(conversationMapper.selectById("missing")).thenReturn(null);

        ConversationService service = new ConversationService(conversationMapper, mock(ChatMessageMapper.class));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getOrCreate("missing"));

        assertEquals(404, exception.getCode());
    }

    private ChatMessageEntity entity(String role, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setRole(role);
        entity.setContent(content);
        return entity;
    }
}
