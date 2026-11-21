package com.example.agentforge.runtime;

import java.util.Set;

 

public interface Agent {

    String id();

    String systemPrompt();

    Set<String> allowedTools();
}
