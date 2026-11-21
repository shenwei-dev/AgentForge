package com.example.agentforge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
        "com.example.agentforge.chat.mapper",
        "com.example.agentforge.tool.mapper",
        "com.example.agentforge.agent.mapper",
        "com.example.agentforge.workflow.mapper",
        "com.example.agentforge.checkpoint.mapper",
        "com.example.agentforge.trace.mapper"
})
public class AgentForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentForgeApplication.class, args);
    }

}
