package com.example.agentforge.runtime;

import org.springframework.stereotype.Component;

import java.util.Optional;

 

@Component
public class LoopGuard {

     
    public Optional<TerminationReason> checkBeforeRound(AgentContext ctx) {
        if (ctx.isCancelled()) {
            return Optional.of(TerminationReason.CANCELLED);
        }
        if (ctx.getClock().millis() > ctx.getDeadlineMillis()) {
            return Optional.of(TerminationReason.TIMEOUT);
        }
        if (ctx.getRound() >= ctx.getLimits().maxRounds()) {
            return Optional.of(TerminationReason.MAX_ROUNDS);
        }
        if (ctx.getTotalTokens() >= ctx.getLimits().tokenBudget()) {
            return Optional.of(TerminationReason.TOKEN_BUDGET_EXCEEDED);
        }
        return Optional.empty();
    }

     
    public Optional<TerminationReason> checkAfterToolFailure(AgentContext ctx) {
        return ctx.getConsecutiveToolFailures() >= ctx.getLimits().maxToolRetries()
                ? Optional.of(TerminationReason.TOOL_RETRY_EXHAUSTED)
                : Optional.empty();
    }

     
    public Optional<TerminationReason> checkAfterParseFailure(AgentContext ctx) {
        return ctx.getParseFailures() > ctx.getLimits().maxParseRetries()
                ? Optional.of(TerminationReason.PARSE_RETRY_EXHAUSTED)
                : Optional.empty();
    }
}
