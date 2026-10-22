package com.dineagent.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record LlmReply(String text, List<ToolCall> toolCalls, JsonNode raw, int inputTokens, int outputTokens) {

    public boolean wantsTools() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
