package com.dineagent.realtime;

import com.dineagent.agent.AgentService;
import com.dineagent.agent.ChatResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class RealtimeController {

    private final SessionEvents events;
    private final AgentService agent;
    private final ExecutorService chatPool = Executors.newFixedThreadPool(8);

    public RealtimeController(SessionEvents events, AgentService agent) {
        this.events = events;
        this.agent = agent;
    }

    /** Live cart and order updates for one customer session. */
    @GetMapping(path = "/events/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String sessionId) {
        return events.subscribe(sessionId);
    }

    /**
     * Chat with live progress: every tool call is pushed as a "step" event the moment it finishes,
     * then the full response arrives as "done". The UI shows "Searching menus…", "Adding to cart…" as it happens.
     */
    @GetMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String sessionId, @RequestParam String message) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String text = message.trim();
        if (text.isEmpty() || text.length() > 1000) {
            sendQuietly(emitter, "error", Map.of("error", "Message must be 1 to 1000 characters."));
            emitter.complete();
            return emitter;
        }
        chatPool.submit(() -> {
            try {
                ChatResponse response = agent.chat(sessionId, text, step -> sendQuietly(emitter, "step", step));
                sendQuietly(emitter, "done", response);
            } catch (RuntimeException e) {
                sendQuietly(emitter, "error", Map.of("error", "Something went wrong."));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    private static void sendQuietly(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException ignored) {
            // the browser went away; the agent still finishes and its result is kept in the session
        }
    }

    @PreDestroy
    void shutdown() {
        chatPool.shutdown();
    }
}
