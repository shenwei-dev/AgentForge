package com.example.agentforge.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

 

public interface McpTransport extends AutoCloseable {

    JsonNode request(String method, Map<String, Object> params, Duration timeout) throws Exception;

    void notify(String method, Map<String, Object> params) throws IOException;

    @Override
    void close();
}
