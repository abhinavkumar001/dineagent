package com.dineagent.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * One turn in the conversation the LLM sees.
 *  USER  - text from the customer
 *  MODEL - the LLM's reply: text, or tool calls (raw keeps Gemini's exact parts so they can be sent back unchanged)
 *  TOOL  - results of the tool calls
 */
public record ChatMessage(Role role, String text, List<ToolCall> toolCalls, List<ToolResult> toolResults, JsonNode raw) {

    public enum Role { USER, MODEL, TOOL }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text, List.of(), List.of(), null);
    }

    public static ChatMessage model(String text, List<ToolCall> calls, JsonNode raw) {
        return new ChatMessage(Role.MODEL, text, calls, List.of(), raw);
    }

    public static ChatMessage tools(List<ToolResult> results) {
        return new ChatMessage(Role.TOOL, null, List.of(), results, null);
    }
}
