package com.dineagent.llm;

import java.util.Map;

/** The LLM asking to run one tool with these arguments. */
public record ToolCall(String name, Map<String, Object> args) {}
