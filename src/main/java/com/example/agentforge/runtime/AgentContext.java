package com.example.agentforge.runtime;

import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.TokenUsage;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

 

public final class AgentContext {

    public record Limits(
            int maxRounds,
            long timeoutMs,
            long tokenBudget,
            int maxModelRetries,
            int maxToolRetries,
            int maxParseRetries) {
    }

    private final String runId;
    private final String conversationId;
    private final String agentId;
    private final Clock clock;
    private final long startedAtMillis;
    private final long deadlineMillis;
    private final Limits limits;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    private volatile AgentState state = AgentState.PENDING;
    private int round;
    private long totalTokens;
    private int consecutiveToolFailures;
    private int parseFailures;
    private int modelRetries;

    public AgentContext(String runId, String conversationId, String agentId,
                        Clock clock, Limits limits, List<ChatMessage> initialMessages) {
        this.runId = runId;
        this.conversationId = conversationId;
        this.agentId = agentId;
        this.clock = clock;
        this.limits = limits;
        this.startedAtMillis = clock.millis();
        this.deadlineMillis = startedAtMillis + limits.timeoutMs();
        if (initialMessages != null) {
            this.messages.addAll(initialMessages);
        }
    }

    public String getRunId() {
        return runId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getAgentId() {
        return agentId;
    }

    public Clock getClock() {
        return clock;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public long getDeadlineMillis() {
        return deadlineMillis;
    }

    public Limits getLimits() {
        return limits;
    }

    public AgentState getState() {
        return state;
    }

    public void setState(AgentState state) {
        this.state = state;
    }

     
    public List<ChatMessage> getMessages() {
        return List.copyOf(messages);
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
    }

    public int getRound() {
        return round;
    }

    public void nextRound() {
        round++;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void addUsage(TokenUsage usage) {
        if (usage != null && usage.totalTokens() != null) {
            totalTokens += usage.totalTokens();
        }
    }

    public int getConsecutiveToolFailures() {
        return consecutiveToolFailures;
    }

    public void recordToolFailure() {
        consecutiveToolFailures++;
    }

    public void resetToolFailures() {
        consecutiveToolFailures = 0;
    }

    public int getParseFailures() {
        return parseFailures;
    }

    public int incrementParseFailures() {
        return ++parseFailures;
    }

    public int getModelRetries() {
        return modelRetries;
    }

    public void incrementModelRetries() {
        modelRetries++;
    }

     
    public boolean requestCancel() {
        return cancelRequested.compareAndSet(false, true);
    }

    public boolean isCancelled() {
        return cancelRequested.get();
    }

    public long elapsedMillis(Clock currentClock) {
        return Math.max(0, currentClock.millis() - startedAtMillis);
    }
}
