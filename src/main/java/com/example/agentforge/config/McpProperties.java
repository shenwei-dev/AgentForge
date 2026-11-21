package com.example.agentforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

 
@Component
@ConfigurationProperties(prefix = "agentforge.mcp")
public class McpProperties {

    private boolean enabled = true;
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Duration startupTimeout = Duration.ofSeconds(5);
    private List<Server> servers = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public Duration getStartupTimeout() { return startupTimeout; }
    public void setStartupTimeout(Duration startupTimeout) { this.startupTimeout = startupTimeout; }
    public List<Server> getServers() { return servers; }
    public void setServers(List<Server> servers) { this.servers = servers == null ? new ArrayList<>() : servers; }

    public static class Server {

        private String name;
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new LinkedHashMap<>();
        private boolean enabled = true;
         
        private String permission = "mcp:external";
         
        private String toolPrefix = "mcp_";
         
        private List<String> allow = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public List<String> getArgs() { return args; }
        public void setArgs(List<String> args) { this.args = args == null ? new ArrayList<>() : args; }
        public Map<String, String> getEnv() { return env; }
        public void setEnv(Map<String, String> env) { this.env = env == null ? new LinkedHashMap<>() : env; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getPermission() { return permission; }
        public void setPermission(String permission) { this.permission = permission; }
        public String getToolPrefix() { return toolPrefix; }
        public void setToolPrefix(String toolPrefix) { this.toolPrefix = toolPrefix; }
        public List<String> getAllow() { return allow; }
        public void setAllow(List<String> allow) { this.allow = allow == null ? new ArrayList<>() : allow; }
    }
}
