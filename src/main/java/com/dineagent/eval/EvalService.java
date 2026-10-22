package com.dineagent.eval;

import com.dineagent.agent.AgentService;
import com.dineagent.agent.ChatResponse;
import com.dineagent.agent.Prompts;
import com.dineagent.agent.SessionStore;
import com.dineagent.agent.TraceStep;
import com.dineagent.llm.LlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Offline evaluation of the agent on a fixed test set (data/eval-cases.json).
 * Measures what matters for an agent, not just "does the text look nice":
 *   - task success rate        all checks in the case passed
 *   - tool selection accuracy  called the tools it should, and not the ones it shouldn't
 *   - constraint satisfaction  share of recommended dishes that respect veg/price/spice/time limits
 *   - guardrail pass rate      never placed an order without the customer's confirmation
 *   - groundedness             LLM-as-judge score, 1-5
 *   - latency (avg, p95), LLM calls per turn, tokens
 * Re-run after every prompt or model change to catch regressions.
 */
@Service
public class EvalService {

    private final AgentService agent;
    private final SessionStore sessions;
    private final GroundednessJudge judge;
    private final LlmClient llm;
    private final List<EvalCase> cases;

    public EvalService(AgentService agent, SessionStore sessions, GroundednessJudge judge, LlmClient llm, ObjectMapper json) {
        this.agent = agent;
        this.sessions = sessions;
        this.judge = judge;
        this.llm = llm;
        try (InputStream in = new ClassPathResource("data/eval-cases.json").getInputStream()) {
            this.cases = json.readValue(in, new TypeReference<List<EvalCase>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load data/eval-cases.json", e);
        }
    }

    public List<EvalCase> cases() {
        return cases;
    }

    public EvalReport run(boolean useJudge, Set<String> onlyIds) {
        List<EvalReport.CaseResult> results = new ArrayList<>();
        List<Long> turnLatencies = new ArrayList<>();
        long tokens = 0;
        int llmCalls = 0, turns = 0;
        int toolSelectionOk = 0, dishesChecked = 0, dishesOk = 0, guardrailCases = 0, guardrailOk = 0;

        for (EvalCase c : cases) {
            if (onlyIds != null && !onlyIds.isEmpty() && !onlyIds.contains(c.id())) continue;

            String sessionId = sessions.fresh().id();
            List<String> toolsCalled = new ArrayList<>();
            List<Map<String, Object>> allToolResults = new ArrayList<>();
            List<Map<String, Object>> lastTurnResults = List.of();
            List<String> failures = new ArrayList<>();
            ChatResponse last = null;
            long caseLatency = 0;

            for (String turn : c.turns()) {
                last = agent.chat(sessionId, turn);
                turns++;
                llmCalls += last.llmCalls();
                tokens += last.inputTokens() + last.outputTokens();
                turnLatencies.add(last.latencyMs());
                caseLatency += last.latencyMs();
                last.trace().stream().map(TraceStep::tool).forEach(toolsCalled::add);
                allToolResults.addAll(last.toolResults());
                lastTurnResults = last.toolResults();
            }

            // 1. tool selection
            boolean toolsOk = true;
            for (String t : c.expectTools()) {
                if (!toolsCalled.contains(t)) { failures.add("expected tool " + t + " was not called"); toolsOk = false; }
            }
            for (String t : c.forbidTools()) {
                if (toolsCalled.contains(t)) { failures.add("forbidden tool " + t + " was called"); toolsOk = false; }
            }
            if (toolsOk) toolSelectionOk++;

            // 2. constraints on every dish the search returned
            int checked = 0, violating = 0;
            if (c.constraints() != null) {
                for (Map<String, Object> dish : dishes(allToolResults)) {
                    checked++;
                    String why = violation(dish, c.constraints());
                    if (why != null) { violating++; failures.add(dish.get("name") + " breaks constraint: " + why); }
                }
                dishesChecked += checked;
                dishesOk += checked - violating;
            }

            // 3. order guardrail
            boolean placed = allToolResults.stream().anyMatch(r -> "place_order".equals(r.get("tool"))
                    && Boolean.TRUE.equals(((Map<?, ?>) r.get("result")).get("ok")));
            if (c.expectOrderPlaced() != null) {
                if (!c.expectOrderPlaced()) {
                    guardrailCases++;
                    if (!placed) guardrailOk++;
                }
                if (placed != c.expectOrderPlaced()) {
                    failures.add(c.expectOrderPlaced() ? "order was not placed" : "order placed without confirmation");
                }
            }

            // 4. reply content
            String reply = last == null ? "" : last.reply();
            if (!c.replyMentionsAny().isEmpty()) {
                String lower = reply.toLowerCase(Locale.ROOT);
                if (c.replyMentionsAny().stream().noneMatch(w -> lower.contains(w.toLowerCase(Locale.ROOT)))) {
                    failures.add("reply mentions none of " + c.replyMentionsAny());
                }
            }

            // 5. groundedness (LLM-as-judge)
            Integer groundedness = null;
            String reason = null;
            if (useJudge && c.judge() && last != null) {
                GroundednessJudge.Verdict v = judge.judge(c.turns().get(c.turns().size() - 1), lastTurnResults, reply);
                if (v != null) {
                    groundedness = v.score();
                    reason = v.reason();
                    if (v.score() <= 2) failures.add("judge: answer not grounded (" + v.reason() + ")");
                }
            }

            results.add(new EvalReport.CaseResult(c.id(), failures.isEmpty(), failures, toolsCalled, checked, violating,
                    groundedness, reason, reply, caseLatency, Map.of("turns", c.turns())));
        }

        int n = results.size();
        List<Long> sorted = new ArrayList<>(turnLatencies);
        Collections.sort(sorted);
        long p95 = sorted.isEmpty() ? 0 : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
        OptionalDouble grounded = results.stream().filter(r -> r.groundedness() != null).mapToInt(EvalReport.CaseResult::groundedness).average();

        return new EvalReport(llm.name(), Prompts.VERSION, n,
                ratio(results.stream().filter(EvalReport.CaseResult::passed).count(), n),
                ratio(toolSelectionOk, n),
                dishesChecked == 0 ? 1.0 : ratio(dishesOk, dishesChecked),
                guardrailCases == 0 ? 1.0 : ratio(guardrailOk, guardrailCases),
                grounded.isPresent() ? Math.round(grounded.getAsDouble() * 100) / 100.0 : null,
                turnLatencies.stream().mapToLong(Long::longValue).average().orElse(0),
                p95,
                turns == 0 ? 0 : Math.round(100.0 * llmCalls / turns) / 100.0,
                tokens, results);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dishes(List<Map<String, Object>> toolResults) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : toolResults) {
            if (!"search_menu".equals(r.get("tool"))) continue;
            Object list = ((Map<String, Object>) r.get("result")).get("dishes");
            if (list instanceof List<?> l) l.forEach(d -> out.add((Map<String, Object>) d));
        }
        return out;
    }

    private static String violation(Map<String, Object> d, EvalCase.Constraints c) {
        if (c.veg() != null && !c.veg().equals(d.get("veg"))) return "veg=" + d.get("veg");
        int price = ((Number) d.get("price")).intValue();
        int spice = ((Number) d.get("spiceLevel")).intValue();
        int minutes = ((Number) d.get("deliveryMinutes")).intValue();
        if (c.maxPrice() != null && price > c.maxPrice()) return "price " + price;
        if (c.minSpice() != null && spice < c.minSpice()) return "spice " + spice;
        if (c.maxSpice() != null && spice > c.maxSpice()) return "spice " + spice;
        if (c.maxDeliveryMinutes() != null && minutes > c.maxDeliveryMinutes()) return "delivery " + minutes + " min";
        return null;
    }

    private static double ratio(long a, long b) {
        return b == 0 ? 0 : Math.round(1000.0 * a / b) / 1000.0;
    }
}
