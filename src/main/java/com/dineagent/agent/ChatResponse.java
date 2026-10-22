package com.dineagent.agent;

import java.util.List;
import java.util.Map;

public record ChatResponse(String sessionId, String reply, List<TraceStep> trace, Map<String, Object> cart,
                           Map<String, Object> order, String model, int llmCalls, long latencyMs,
                           int inputTokens, int outputTokens, List<Map<String, Object>> toolResults) {}
