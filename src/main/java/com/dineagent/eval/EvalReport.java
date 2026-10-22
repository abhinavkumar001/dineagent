package com.dineagent.eval;

import java.util.List;
import java.util.Map;

public record EvalReport(String model, String promptVersion, int cases, double taskSuccessRate,
                         double toolSelectionAccuracy, double constraintSatisfaction, double guardrailPassRate,
                         Double avgGroundedness, double avgLatencyMs, long p95LatencyMs, double avgLlmCallsPerTurn,
                         long totalTokens, List<CaseResult> results) {

    public record CaseResult(String id, boolean passed, List<String> failures, List<String> toolsCalled,
                             int dishesChecked, int dishesViolating, Integer groundedness, String judgeReason,
                             String finalReply, long latencyMs, Map<String, Object> details) {}
}
