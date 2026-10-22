package com.dineagent.agent;

import com.dineagent.llm.ToolSpec;

import java.util.Map;

/** Something the agent can do. The LLM picks tools; Java code runs them and enforces the rules. */
public interface Tool {

    ToolSpec spec();

    Map<String, Object> execute(AgentSession session, Map<String, Object> args);

    default String name() {
        return spec().name();
    }
}
