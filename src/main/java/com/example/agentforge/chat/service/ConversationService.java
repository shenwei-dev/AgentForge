package com.example.agentforge.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agentforge.chat.dto.ConversationVO;
import com.example.agentforge.chat.dto.MessageVO;
import com.example.agentforge.chat.entity.ChatMessageEntity;
import com.example.agentforge.chat.entity.Conversation;
import com.example.agentforge.chat.mapper.ChatMessageMapper;
import com.example.agentforge.chat.mapper.ConversationMapper;
import com.example.agentforge.common.BusinessException;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

 

@Service
public class ConversationService {

    private static final int MAX_HISTORY = 20;
    private static final int MAX_MESSAGE_CHARS = 4000;
    private static final int TITLE_MAX_LENGTH = 30;
    private static final String DEFAULT_TITLE = "新会话";

    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ConversationService(ConversationMapper conversationMapper, ChatMessageMapper chatMessageMapper) {
        this.conversationMapper = conversationMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Transactional
    public ConversationVO create(String title) {
        return ConversationVO.from(insertConversation(title));
    }

     
    @Transactional
    public Conversation getOrCreate(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return insertConversation(null);
        }
        Conversation conversation = conversationMapper.selectById(conversationId.trim());
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在: " + conversationId);
        }
        return conversation;
    }

    private Conversation insertConversation(String title) {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID().toString());
        conversation.setTitle(StringUtils.hasText(title) ? title.trim() : DEFAULT_TITLE);
        conversation.setStatus("ACTIVE");
        conversationMapper.insert(conversation);
        return conversation;
    }

    public List<ConversationVO> list() {
        return conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                        .orderByDesc(Conversation::getLastMessageAt)
                        .orderByDesc(Conversation::getCreatedAt)
                        .last("LIMIT 50"))
                .stream()
                .map(ConversationVO::from)
                .toList();
    }

    public List<MessageVO> messages(String conversationId) {
        requireExists(conversationId);
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversationId)
                        .orderByAsc(ChatMessageEntity::getCreatedAt))
                .stream()
                .map(MessageVO::from)
                .toList();
    }

    @Transactional
    public void saveUserMessage(String conversationId, String runId, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setConversationId(conversationId);
        entity.setRunId(runId);
        entity.setRole("USER");
        entity.setContent(content);
        entity.setContentType("TEXT");
        chatMessageMapper.insert(entity);
    }

    @Transactional
    public ChatMessageEntity saveAssistantMessage(String conversationId, String runId, ModelResponse response) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setConversationId(conversationId);
        entity.setRunId(runId);
        entity.setRole("ASSISTANT");
        entity.setContent(response.content());
        entity.setContentType("MARKDOWN");
        entity.setModelName(response.provider() + "/" + response.model());
        entity.setLatencyMs(response.latencyMs());
        if (response.usage() != null) {
            entity.setPromptTokens(response.usage().promptTokens());
            entity.setCompletionTokens(response.usage().completionTokens());
        }
        chatMessageMapper.insert(entity);
        return entity;
    }

     
    @Transactional
    public ChatMessageEntity savePlanMessage(String conversationId, String runId, String planJson,
                                             String provider, String model) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setConversationId(conversationId);
        entity.setRunId(runId);
        entity.setRole("ASSISTANT");
        entity.setContent(planJson);
        entity.setContentType("JSON");
        entity.setModelName(provider + "/" + model);
        chatMessageMapper.insert(entity);
        return entity;
    }

     
    @Transactional
    public void touch(String conversationId, String firstUserMessage) {
        Conversation current = conversationMapper.selectById(conversationId);
        if (current == null) {
            return;
        }
        Conversation update = new Conversation();
        update.setId(conversationId);
        update.setLastMessageAt(LocalDateTime.now());
        if (DEFAULT_TITLE.equals(current.getTitle()) && StringUtils.hasText(firstUserMessage)) {
            String title = firstUserMessage.replaceAll("\\s+", " ").trim();
            update.setTitle(title.length() > TITLE_MAX_LENGTH
                    ? title.substring(0, TITLE_MAX_LENGTH)
                    : title);
        }
        conversationMapper.updateById(update);
    }

     
    public List<ChatMessage> loadRecentHistory(String conversationId) {
        
        List<ChatMessageEntity> rows = new ArrayList<>(chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversationId)
                        .orderByDesc(ChatMessageEntity::getCreatedAt)
                        .last("LIMIT " + MAX_HISTORY)));
        Collections.reverse(rows);

        List<ChatMessage> messages = new ArrayList<>();
        for (ChatMessageEntity row : rows) {
            String content = truncate(row.getContent());
            if ("USER".equals(row.getRole())) {
                messages.add(ChatMessage.user(content));
            } else if ("ASSISTANT".equals(row.getRole())) {
                messages.add(ChatMessage.assistant(content));
            }
        }
        return messages;
    }

     
    public List<ChatMessage> loadAllHistory(String conversationId) {
        List<ChatMessageEntity> rows = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversationId)
                        .orderByAsc(ChatMessageEntity::getCreatedAt));
        List<ChatMessage> messages = new ArrayList<>();
        for (ChatMessageEntity row : rows) {
            if ("SUMMARY".equals(row.getContentType())) {
                continue;
            }
            String content = truncate(row.getContent());
            if ("USER".equals(row.getRole())) {
                messages.add(ChatMessage.user(content));
            } else if ("ASSISTANT".equals(row.getRole())) {
                messages.add(ChatMessage.assistant(content));
            } else if ("SYSTEM".equals(row.getRole())) {
                messages.add(ChatMessage.system(content));
            }
        }
        return messages;
    }

     
    public String findLatestSummary(String conversationId) {
        List<ChatMessageEntity> rows = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversationId)
                        .eq(ChatMessageEntity::getContentType, "SUMMARY")
                        .orderByDesc(ChatMessageEntity::getCreatedAt)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0).getContent();
    }

     
    @Transactional
    public void saveSummaryMessage(String conversationId, String runId, String summary) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setConversationId(conversationId);
        entity.setRunId(runId);
        entity.setRole("SYSTEM");
        entity.setContent(summary);
        entity.setContentType("SUMMARY");
        chatMessageMapper.insert(entity);
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > MAX_MESSAGE_CHARS
                ? content.substring(0, MAX_MESSAGE_CHARS) + "\n[历史消息已截断]"
                : content;
    }

    private void requireExists(String conversationId) {
        if (conversationMapper.selectById(conversationId) == null) {
            throw new BusinessException(404, "会话不存在: " + conversationId);
        }
    }
}
