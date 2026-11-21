package com.example.agentforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

 
@Component
@ConfigurationProperties(prefix = "agentforge.recovery")
public class RecoveryProperties {

    private int maxNodeRetries = 2;
    private long retryDelayMs = 50;
    private long nodeTimeoutSeconds = 30;
    private boolean checkpointEveryLevel = true;
    private boolean resumeEnabled = true;

    public int getMaxNodeRetries() { return maxNodeRetries; }
    public void setMaxNodeRetries(int maxNodeRetries) { this.maxNodeRetries = maxNodeRetries; }
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    public long getNodeTimeoutSeconds() { return nodeTimeoutSeconds; }
    public void setNodeTimeoutSeconds(long nodeTimeoutSeconds) { this.nodeTimeoutSeconds = nodeTimeoutSeconds; }
    public boolean isCheckpointEveryLevel() { return checkpointEveryLevel; }
    public void setCheckpointEveryLevel(boolean checkpointEveryLevel) { this.checkpointEveryLevel = checkpointEveryLevel; }
    public boolean isResumeEnabled() { return resumeEnabled; }
    public void setResumeEnabled(boolean resumeEnabled) { this.resumeEnabled = resumeEnabled; }
}
