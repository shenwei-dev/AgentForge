package com.example.agentforge.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

 
@Component
@ConfigurationProperties(prefix = "agentforge.tools")
public class ToolProperties {

     
    private Set<String> grantedPermissions = new LinkedHashSet<>(
            Set.of("compute", "system:time", "file:read", "utility"));

     
    private String workspaceDir = System.getProperty("user.dir");

    private long fileReadMaxBytes = 65536;
    private int fileSearchMaxResults = 50;
    private Duration defaultTimeout = Duration.ofSeconds(5);
    private int poolSize = 8;

    public Set<String> getGrantedPermissions() {
        return grantedPermissions;
    }

    public void setGrantedPermissions(Set<String> grantedPermissions) {
        this.grantedPermissions = grantedPermissions == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(grantedPermissions);
    }

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public void setWorkspaceDir(String workspaceDir) {
        this.workspaceDir = workspaceDir;
    }

    public long getFileReadMaxBytes() {
        return fileReadMaxBytes;
    }

    public void setFileReadMaxBytes(long fileReadMaxBytes) {
        this.fileReadMaxBytes = fileReadMaxBytes;
    }

    public int getFileSearchMaxResults() {
        return fileSearchMaxResults;
    }

    public void setFileSearchMaxResults(int fileSearchMaxResults) {
        this.fileSearchMaxResults = fileSearchMaxResults;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }
}
