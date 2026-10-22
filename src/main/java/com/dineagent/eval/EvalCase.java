package com.dineagent.eval;

import java.util.List;

public record EvalCase(String id, List<String> turns, List<String> expectTools, List<String> forbidTools,
                       Constraints constraints, Boolean expectOrderPlaced, List<String> replyMentionsAny, boolean judge) {

    public record Constraints(Boolean veg, Integer maxPrice, Integer minSpice, Integer maxSpice, Integer maxDeliveryMinutes) {}

    public List<String> expectTools() { return expectTools == null ? List.of() : expectTools; }
    public List<String> forbidTools() { return forbidTools == null ? List.of() : forbidTools; }
    public List<String> replyMentionsAny() { return replyMentionsAny == null ? List.of() : replyMentionsAny; }
}
