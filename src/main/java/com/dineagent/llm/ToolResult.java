package com.dineagent.llm;

import java.util.Map;

/** What a tool returned, sent back to the LLM. */
public record ToolResult(String name, Map<String, Object> result) {}
