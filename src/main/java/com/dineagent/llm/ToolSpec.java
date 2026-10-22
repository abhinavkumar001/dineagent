package com.dineagent.llm;

import java.util.Map;

/**
 * What the LLM is told about a tool: its name, when to use it, and its parameters
 * as a JSON-schema-style map.
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {}
