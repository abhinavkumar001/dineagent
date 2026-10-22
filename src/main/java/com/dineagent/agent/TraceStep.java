package com.dineagent.agent;

import java.util.Map;

/** One step of the agent's reasoning, shown in the UI so you can see what it did and why. */
public record TraceStep(int step, String tool, Map<String, Object> args, String summary, long millis, boolean error) {}
