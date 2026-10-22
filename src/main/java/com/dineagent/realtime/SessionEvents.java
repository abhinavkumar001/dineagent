package com.dineagent.realtime;

import com.dineagent.agent.SessionStore;
import com.dineagent.agent.Views;
import com.dineagent.order.Order;
import com.dineagent.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time updates with Server-Sent Events (SSE).
 * Each browser tab opens one long-lived GET /api/events/{sessionId}; the server pushes:
 *   cart  - whenever the cart changes (from the UI or from the AI agent)
 *   order - when an order is placed, and every few seconds while it is on its way
 * SSE is one-way (server -> browser), works over plain HTTP and reconnects automatically,
 * which is all live tracking needs; WebSockets would only be required for two-way messaging.
 */
@Service
public class SessionEvents {

    private static final Logger log = LoggerFactory.getLogger(SessionEvents.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final SessionStore sessions;
    private final OrderService orders;

    public SessionEvents(SessionStore sessions, OrderService orders) {
        this.sessions = sessions;
        this.orders = orders;
    }

    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = subscribers.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        // Send the current state straight away so a new tab is in sync.
        send(emitter, "cart", Views.cart(sessions.getOrCreate(sessionId).cart()));
        orders.forSession(sessionId).stream().limit(5).forEach(o -> send(emitter, "order", orders.view(o)));
        return emitter;
    }

    public void cartChanged(String sessionId) {
        publish(sessionId, "cart", Views.cart(sessions.getOrCreate(sessionId).cart()));
    }

    public void orderChanged(Order order) {
        publish(order.sessionId(), "order", orders.view(order));
    }

    /** Pushes live status, minutes left and progress for every order still on its way. */
    @Scheduled(fixedDelayString = "${app.realtime.order-tick-ms:3000}")
    public void tickActiveOrders() {
        for (Order o : orders.active()) {
            if (subscribers.containsKey(o.sessionId())) {
                orderChanged(o);
            }
        }
        // One last update so the UI sees DELIVERED.
        orders.recentlyDelivered().forEach(this::orderChanged);
    }

    /** Comments keep proxies from closing idle connections. */
    @Scheduled(fixedDelay = 20000)
    public void heartbeat() {
        subscribers.values().forEach(list -> list.forEach(e -> {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                list.remove(e);
            }
        }));
    }

    private void publish(String sessionId, String event, Object data) {
        List<SseEmitter> list = subscribers.get(sessionId);
        if (list == null) return;
        list.forEach(e -> {
            if (!send(e, event, data)) list.remove(e);
        });
    }

    private boolean send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping closed SSE connection: {}", e.getMessage());
            return false;
        }
    }
}
