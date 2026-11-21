package com.example.agentforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

 
@Component
@ConfigurationProperties(prefix = "agentforge.memory")
public class MemoryProperties {

     
    private int charBudget = 12000;
    private int maxMessages = 40;
    private boolean summaryEnabled = true;
     
    private int summaryThreshold = 4;
    private int summaryMaxChars = 600;
    private int maxReusableResults = 5;
    private int reusableResultChars = 500;
    private int workingMemoryMax = 50;

    public int getCharBudget() { return charBudget; }
    public void setCharBudget(int charBudget) { this.charBudget = charBudget; }
    public int getMaxMessages() { return maxMessages; }
    public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
    public boolean isSummaryEnabled() { return summaryEnabled; }
    public void setSummaryEnabled(boolean summaryEnabled) { this.summaryEnabled = summaryEnabled; }
    public int getSummaryThreshold() { return summaryThreshold; }
    public void setSummaryThreshold(int summaryThreshold) { this.summaryThreshold = summaryThreshold; }
    public int getSummaryMaxChars() { return summaryMaxChars; }
    public void setSummaryMaxChars(int summaryMaxChars) { this.summaryMaxChars = summaryMaxChars; }
    public int getMaxReusableResults() { return maxReusableResults; }
    public void setMaxReusableResults(int maxReusableResults) { this.maxReusableResults = maxReusableResults; }
    public int getReusableResultChars() { return reusableResultChars; }
    public void setReusableResultChars(int reusableResultChars) { this.reusableResultChars = reusableResultChars; }
    public int getWorkingMemoryMax() { return workingMemoryMax; }
    public void setWorkingMemoryMax(int workingMemoryMax) { this.workingMemoryMax = workingMemoryMax; }
}
