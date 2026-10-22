package com.dineagent.llm;

import java.util.List;

/** A chat model that can answer in text or ask for tools to be called. */
public interface LlmClient {

    String name();

    boolean available();

    LlmReply next(String systemPrompt, List<ChatMessage> history, List<ToolSpec> tools);
}
