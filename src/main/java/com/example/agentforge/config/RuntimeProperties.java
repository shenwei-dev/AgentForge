package com.example.agentforge.config;

import com.example.agentforge.runtime.AgentContext;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

 
@Component
@ConfigurationProperties(prefix = "agentforge.runtime")
public class RuntimeProperties {

    private int maxRounds = 8;
    private Duration timeout = Duration.ofSeconds(120);
    private long tokenBudget = 20000;
    private int maxModelRetries = 2;
    private int maxToolRetries = 2;
    private int maxParseRetries = 2;

    public AgentContext.Limits toLimits() {
        return new AgentContext.Limits(maxRounds, timeout.toMillis(), tokenBudget,
                maxModelRetries, maxToolRetries, maxParseRetries);
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public long getTokenBudget() {
        return tokenBudget;
    }

    public void setTokenBudget(long tokenBudget) {
        this.tokenBudget = tokenBudget;
    }

    public int getMaxModelRetries() {
        return maxModelRetries;
    }

    public void setMaxModelRetries(int maxModelRetries) {
        this.maxModelRetries = maxModelRetries;
    }

    public int getMaxToolRetries() {
        return maxToolRetries;
    }

    public void setMaxToolRetries(int maxToolRetries) {
        this.maxToolRetries = maxToolRetries;
    }

    public int getMaxParseRetries() {
        return maxParseRetries;
    }

    public void setMaxParseRetries(int maxParseRetries) {
        this.maxParseRetries = maxParseRetries;
    }
}
