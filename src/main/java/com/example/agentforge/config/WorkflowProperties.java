package com.example.agentforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

 
@Component
@ConfigurationProperties(prefix = "agentforge.workflow")
public class WorkflowProperties {

    private int poolSize = 4;
    private long demoDelayMs = 150;
    private long levelTimeoutSeconds = 120;

    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
    public long getDemoDelayMs() { return demoDelayMs; }
    public void setDemoDelayMs(long demoDelayMs) { this.demoDelayMs = demoDelayMs; }
    public long getLevelTimeoutSeconds() { return levelTimeoutSeconds; }
    public void setLevelTimeoutSeconds(long levelTimeoutSeconds) { this.levelTimeoutSeconds = levelTimeoutSeconds; }
}
