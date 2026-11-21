package com.example.agentforge.runtime;

import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.model.ModelException;
import com.example.agentforge.model.ModelRequest;
import com.example.agentforge.model.ModelResponse;
import com.example.agentforge.tool.ToolExecutor;
import com.example.agentforge.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

 

@Service
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final AgentDecisionParser parser;
    private final LoopGuard guard;

    public AgentRuntime(AgentDecisionParser parser, LoopGuard guard) {
        this.parser = parser;
        this.guard = guard;
    }

    public AgentResult run(Agent agent, AgentContext ctx, ModelAdapter adapter,
                           ToolExecutor tools, Consumer<ExecutionEvent> events) {
        ctx.setState(AgentState.RUNNING);
        events.accept(ExecutionEvent.agentStarted(ctx.getRunId(), ctx.getConversationId(),
                ctx.getAgentId(), adapter.provider()));
        try {
            while (true) {
                Optional<TerminationReason> stop = guard.checkBeforeRound(ctx);
                if (stop.isPresent()) {
                    return finish(ctx, stop.get(), null, null, events);
                }
                ctx.nextRound();
                events.accept(ExecutionEvent.roundStarted(ctx.getRunId(),
                        ctx.getConversationId(), ctx.getRound()));

                
                ModelResponse response;
                try {
                    ctx.setState(AgentState.WAITING_MODEL);
                    response = adapter.chat(ModelRequest.of(List.copyOf(ctx.getMessages())));
                } catch (ModelException e) {
                    if (!e.isRetryable() || ctx.getModelRetries() >= ctx.getLimits().maxModelRetries()) {
                        return finish(ctx, TerminationReason.UNRECOVERABLE_ERROR, null, e.getMessage(), events);
                    }
                    ctx.incrementModelRetries();
                    ctx.addMessage(ChatMessage.user("模型调用失败，请重新决策：" + e.getMessage()));
                    continue;
                }
                ctx.addUsage(response.usage());
                ctx.addMessage(ChatMessage.assistant(response.content()));

                
                AgentDecision decision;
                try {
                    decision = parser.parse(response.content());
                } catch (DecisionParseException e) {
                    ctx.incrementParseFailures();
                    Optional<TerminationReason> parseStop = guard.checkAfterParseFailure(ctx);
                    if (parseStop.isPresent()) {
                        return finish(ctx, parseStop.get(), null, e.getMessage(), events);
                    }
                    ctx.addMessage(ChatMessage.user("上一轮不是合法 JSON，请严格按协议重输：" + e.getMessage()));
                    continue;
                }

                
                if (decision.decision() == AgentDecision.Decision.FINAL) {
                    ctx.setState(AgentState.SUCCEEDED);
                    events.accept(ExecutionEvent.messageDelta(ctx.getRunId(),
                            ctx.getConversationId(), decision.answer()));
                    return finish(ctx, TerminationReason.FINAL_ANSWER, decision.answer(), null, events);
                }

                
                ctx.setState(AgentState.EXECUTING_TOOL);
                events.accept(ExecutionEvent.toolCallStarted(ctx.getRunId(),
                        ctx.getConversationId(), decision.toolName()));
                ToolResult result;
                try {
                    result = tools.availableTools().contains(decision.toolName())
                            ? tools.execute(decision.toolName(), decision.toolInput())
                            : ToolResult.failed(decision.toolName(), "工具未注册: " + decision.toolName());
                } catch (RuntimeException e) {
                    
                    log.warn("工具执行异常 tool={}", decision.toolName(), e);
                    result = ToolResult.failed(decision.toolName(), "工具执行异常: " + e.getMessage());
                }
                events.accept(ExecutionEvent.toolCallCompleted(ctx.getRunId(),
                        ctx.getConversationId(), decision.toolName(), result.success()));

                
                if (result.success()) {
                    ctx.resetToolFailures();
                    ctx.addMessage(ChatMessage.user("工具 " + decision.toolName() + " 返回：" + result.content()));
                } else {
                    ctx.recordToolFailure();
                    ctx.addMessage(ChatMessage.user("工具执行失败：" + result.errorMessage()
                            + "，可以换工具，也可以直接给出最终答案"));
                }
                Optional<TerminationReason> retryStop = guard.checkAfterToolFailure(ctx);
                if (retryStop.isPresent()) {
                    return finish(ctx, retryStop.get(), null, result.errorMessage(), events);
                }
            }
        } finally {
            events.accept(ExecutionEvent.agentCompleted(ctx.getRunId(), ctx.getConversationId()));
            log.debug("Agent 执行结束 runId={} state={} rounds={} tokens={}",
                    ctx.getRunId(), ctx.getState(), ctx.getRound(), ctx.getTotalTokens());
        }
    }

    private AgentResult finish(AgentContext ctx, TerminationReason reason, String answer,
                               String error, Consumer<ExecutionEvent> events) {
        boolean success = reason == TerminationReason.FINAL_ANSWER;
        if (!success) {
            ctx.setState(switch (reason) {
                case CANCELLED -> AgentState.CANCELLED;
                case TIMEOUT -> AgentState.TIMED_OUT;
                case TOKEN_BUDGET_EXCEEDED -> AgentState.BUDGET_EXCEEDED;
                default -> AgentState.FAILED;
            });
        }
        long latencyMs = ctx.elapsedMillis(ctx.getClock());
        if (success) {
            events.accept(ExecutionEvent.runCompleted(ctx.getRunId(), ctx.getConversationId(),
                    answer == null ? 0 : answer.length(), latencyMs,
                    (int) ctx.getTotalTokens(), null));
        } else {
            events.accept(ExecutionEvent.runFailed(ctx.getRunId(), ctx.getConversationId(),
                    error == null ? reason.name() : error));
        }
        return new AgentResult(ctx.getRunId(), ctx.getState(), reason, answer,
                ctx.getRound(), latencyMs, ctx.getTotalTokens(), error);
    }
}
