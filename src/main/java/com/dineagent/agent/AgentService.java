package com.dineagent.agent;

import com.dineagent.llm.ChatMessage;
import com.dineagent.llm.LlmClient;
import com.dineagent.llm.LlmException;
import com.dineagent.llm.LlmReply;
import com.dineagent.llm.ToolCall;
import com.dineagent.llm.ToolResult;
import com.dineagent.order.OrderService;
import com.dineagent.realtime.SessionEvents;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The agent loop (ReAct style):
 *
 *   user message -> LLM -> wants tools? -> run them (in parallel) -> send results to LLM -> ... -> final answer
 *
 * Guardrails enforced here in code:
 *   - at most maxSteps LLM calls per user message (no infinite loops, bounded cost)
 *   - unknown tools are rejected; tool errors are returned to the LLM instead of crashing
 *   - one conversation is processed at a time (per-session lock)
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final LlmClient llm;
    private final ToolRegistry tools;
    private final SessionStore sessions;
    private final OrderService orders;
    private final SessionEvents events;
    private final int maxSteps;
    private final ExecutorService toolPool = Executors.newFixedThreadPool(4);

    public AgentService(LlmClient llm, ToolRegistry tools, SessionStore sessions, OrderService orders,
                        SessionEvents events, @Value("${app.agent.max-steps:6}") int maxSteps) {
        this.llm = llm;
        this.tools = tools;
        this.sessions = sessions;
        this.orders = orders;
        this.events = events;
        this.maxSteps = maxSteps;
    }

    public ChatResponse chat(String sessionId, String message) {
        return chat(sessionId, message, step -> { });
    }

    /** onStep is called the moment each tool call finishes, for live progress in the UI. */
    public ChatResponse chat(String sessionId, String message, Consumer<TraceStep> onStep) {
        ChatResponse response = runTurn(sessionId, message, onStep);
        // Keep every open tab in sync with what the agent did.
        events.cartChanged(response.sessionId());
        if (response.order() != null && response.trace().stream().anyMatch(t -> t.tool().equals("place_order") && !t.error())) {
            orders.find(String.valueOf(response.order().get("orderId"))).ifPresent(events::orderChanged);
        }
        return response;
    }

    private ChatResponse runTurn(String sessionId, String message, Consumer<TraceStep> onStep) {
        AgentSession session = sessions.getOrCreate(sessionId);
        synchronized (session) {
            long start = System.currentTimeMillis();
            List<TraceStep> trace = new ArrayList<>();
            List<Map<String, Object>> toolResults = new ArrayList<>();
            int calls = 0, inTokens = 0, outTokens = 0;
            String reply;

            session.addUser(message);

            if (!llm.available()) {
                reply = "The AI model isn't configured. Set the GEMINI_API_KEY environment variable and restart the app.";
                session.add(ChatMessage.model(reply, List.of(), null));
                return respond(session, reply, trace, 0, start, 0, 0, toolResults);
            }

            try {
                while (true) {
                    if (calls >= maxSteps) {
                        reply = "Sorry, I couldn't finish that in " + maxSteps + " steps. Could you try a simpler request?";
                        session.add(ChatMessage.model(reply, List.of(), null));
                        break;
                    }
                    LlmReply r = llm.next(Prompts.SYSTEM, session.history(), tools.specs());
                    calls++;
                    inTokens += r.inputTokens();
                    outTokens += r.outputTokens();

                    if (!r.wantsTools()) {
                        reply = r.text().isBlank() ? "Sorry, I didn't get that. Could you rephrase?" : r.text();
                        session.add(ChatMessage.model(reply, List.of(), r.raw()));
                        break;
                    }
                    session.add(ChatMessage.model(r.text(), r.toolCalls(), r.raw()));
                    List<ToolResult> results = runTools(session, r.toolCalls(), calls, trace, onStep);
                    results.forEach(tr -> toolResults.add(Map.of("tool", tr.name(), "result", tr.result())));
                    session.add(ChatMessage.tools(results));
                }
            } catch (LlmException e) {
                log.warn("LLM call failed: {}", e.getMessage());
                reply = "Sorry, the AI service had a problem (" + e.getMessage() + "). Please try again.";
                rollbackDanglingToolCall(session);
                session.add(ChatMessage.model(reply, List.of(), null));
            }
            return respond(session, reply, trace, calls, start, inTokens, outTokens, toolResults);
        }
    }

    /** Runs all tool calls from one LLM turn in parallel; results keep the original order. */
    private List<ToolResult> runTools(AgentSession session, List<ToolCall> calls, int step, List<TraceStep> trace,
                                      Consumer<TraceStep> onStep) {
        List<CompletableFuture<ToolResult>> futures = new ArrayList<>();
        for (ToolCall call : calls) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                long t0 = System.currentTimeMillis();
                Map<String, Object> result;
                boolean error = false;
                try {
                    result = tools.find(call.name())
                            .map(t -> t.execute(session, call.args() == null ? Map.of() : call.args()))
                            .orElseGet(() -> Map.of("error", "Unknown tool " + call.name(), "summary", "unknown tool"));
                    error = result.containsKey("error");
                } catch (RuntimeException e) {
                    log.warn("Tool {} failed", call.name(), e);
                    result = Map.of("error", "Tool failed: " + e.getMessage(), "summary", "tool error");
                    error = true;
                }
                TraceStep traceStep = new TraceStep(step, call.name(), call.args(), String.valueOf(result.get("summary")),
                        System.currentTimeMillis() - t0, error);
                synchronized (trace) {
                    trace.add(traceStep);
                }
                try {
                    onStep.accept(traceStep);
                } catch (RuntimeException ignored) {
                    // a slow or closed stream must never break the agent
                }
                return new ToolResult(call.name(), result);
            }, toolPool));
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    /** If the LLM failed right after asking for tools, drop that half-finished turn so history stays valid. */
    private void rollbackDanglingToolCall(AgentSession session) {
        List<ChatMessage> h = session.history();
        if (!h.isEmpty() && h.get(h.size() - 1).role() == ChatMessage.Role.MODEL && !h.get(h.size() - 1).toolCalls().isEmpty()) {
            h.remove(h.size() - 1);
        }
    }

    private ChatResponse respond(AgentSession session, String reply, List<TraceStep> trace, int calls, long start,
                                 int in, int out, List<Map<String, Object>> toolResults) {
        Map<String, Object> order = session.lastOrderId() == null ? null
                : orders.find(session.lastOrderId()).map(o -> orders.view(o)).orElse(null);
        return new ChatResponse(session.id(), reply, trace, Views.cart(session.cart()), order, llm.name(), calls,
                System.currentTimeMillis() - start, in, out, toolResults);
    }

    public Map<String, Object> state(String sessionId) {
        AgentSession session = sessions.getOrCreate(sessionId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", session.id());
        m.put("cart", Views.cart(session.cart()));
        m.put("order", session.lastOrderId() == null ? null
                : orders.find(session.lastOrderId()).map(o -> orders.view(o)).orElse(null));
        return m;
    }

    @PreDestroy
    void shutdown() {
        toolPool.shutdown();
    }
}
