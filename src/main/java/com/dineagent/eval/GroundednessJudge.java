package com.dineagent.eval;

import com.dineagent.llm.ChatMessage;
import com.dineagent.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * LLM-as-judge: a second LLM call grades whether the agent's answer is supported by the tool results
 * (no invented dishes, prices or policies). Score 1 (made up) to 5 (fully grounded).
 */
@Component
public class GroundednessJudge {

    public record Verdict(int score, String reason) {}

    private static final String PROMPT = """
            You are a strict evaluator of a food-ordering assistant.
            You get the customer's message, the data the assistant's tools returned, and the assistant's reply.
            Score how well the reply is GROUNDED in the tool data:
              5 = every dish, price, time and policy fact in the reply appears in the tool data
              3 = mostly grounded, one small unsupported detail
              1 = invents dishes, prices or policies not in the tool data
            If the reply says something is unavailable and the tool data agrees, that is grounded.
            Respond with JSON only, no markdown: {"score": <1-5>, "reason": "<one sentence>"}
            """;

    private final LlmClient llm;
    private final ObjectMapper json;

    public GroundednessJudge(LlmClient llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    public Verdict judge(String userMessage, List<Map<String, Object>> toolResults, String reply) {
        try {
            String material = "CUSTOMER MESSAGE:\n" + userMessage
                    + "\n\nTOOL DATA:\n" + json.writeValueAsString(toolResults)
                    + "\n\nASSISTANT REPLY:\n" + reply;
            String text = llm.next(PROMPT, List.of(ChatMessage.user(material)), List.of()).text();
            String cleaned = text.replaceAll("(?s)```(json)?", "").trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            JsonNode node = json.readTree(cleaned.substring(start, end + 1));
            int score = Math.max(1, Math.min(5, node.path("score").asInt(0)));
            return new Verdict(score, node.path("reason").asText(""));
        } catch (Exception e) {
            return null; // judging is best-effort; a failed judge call doesn't fail the case
        }
    }
}
