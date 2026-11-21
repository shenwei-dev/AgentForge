package com.example.agentforge.memory;

import com.example.agentforge.model.ChatMessage;

import java.util.List;
import java.util.Map;

 

public interface MemoryManager {

     
    List<ChatMessage> buildContext(MemoryScope scope, String systemPrompt, String currentInput);

     
    void appendWorkingMemory(MemoryScope scope, String role, String content);

     
    void putTaskResult(String conversationId, String taskId, String result);

    Map<String, String> taskResults(String conversationId);

    void clear(MemoryScope scope);

    MemoryStats stats(String conversationId);

     
    MemoryStats compact(String conversationId);
}
