package com.example.agentforge.config;

import com.example.agentforge.evaluation.EvaluationTask;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

 
@Component
@ConfigurationProperties(prefix = "agentforge.evaluation")
public class EvaluationProperties {

    private long latencyThresholdMs = 15000;
    private int maxRoundsThreshold = 6;
    private int passScore = 60;
    private List<EvaluationTask> tasks = new ArrayList<>();

    public long getLatencyThresholdMs() { return latencyThresholdMs; }
    public void setLatencyThresholdMs(long latencyThresholdMs) { this.latencyThresholdMs = latencyThresholdMs; }
    public int getMaxRoundsThreshold() { return maxRoundsThreshold; }
    public void setMaxRoundsThreshold(int maxRoundsThreshold) { this.maxRoundsThreshold = maxRoundsThreshold; }
    public int getPassScore() { return passScore; }
    public void setPassScore(int passScore) { this.passScore = passScore; }
    public List<EvaluationTask> getTasks() { return tasks; }
    public void setTasks(List<EvaluationTask> tasks) {
        this.tasks = tasks == null ? new ArrayList<>() : tasks;
    }
}
