package com.example.agentforge.chat.controller;

import com.example.agentforge.chat.dto.ChatRequest;
import com.example.agentforge.chat.dto.ConversationVO;
import com.example.agentforge.chat.dto.CreateConversationRequest;
import com.example.agentforge.chat.dto.MessageVO;
import com.example.agentforge.chat.service.ChatService;
import com.example.agentforge.chat.service.ConversationService;
import com.example.agentforge.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

 

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ConversationService conversationService;
    private final ChatService chatService;

    public ChatController(ConversationService conversationService, ChatService chatService) {
        this.conversationService = conversationService;
        this.chatService = chatService;
    }

    @PostMapping("/conversations")
    public ApiResponse<ConversationVO> createConversation(
            @Valid @RequestBody(required = false) CreateConversationRequest request) {
        return ApiResponse.ok(conversationService.create(request == null ? null : request.title()));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationVO>> listConversations() {
        return ApiResponse.ok(conversationService.list());
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<MessageVO>> listMessages(@PathVariable String conversationId) {
        return ApiResponse.ok(conversationService.messages(conversationId));
    }

     
    @PostMapping("/completions")
    public ApiResponse<MessageVO> complete(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(chatService.complete(request));
    }

     
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request);
    }
}
