package com.example.agentforge.model;

import java.util.function.Consumer;

 

public interface ModelAdapter {

    String provider();

    String model();

    ModelCapabilities capabilities();

    ModelResponse chat(ModelRequest request);

    void chatStream(ModelRequest request, Consumer<StreamChunk> chunkConsumer);
}
