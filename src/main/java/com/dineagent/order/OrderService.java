package com.dineagent.order;

import com.dineagent.cart.Cart;
import com.dineagent.catalog.CatalogStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OrderService {

    public static final int FREE_DELIVERY_ABOVE = 199;
    public static final int DELIVERY_FEE = 30;

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1000);
    private final CatalogStore catalog;
    private final Clock clock;
    /** 1 = real time. 10 = a 30-minute delivery plays out in 3 minutes, handy for demos. */
    private final int timeScale;

    public OrderService(CatalogStore catalog, Clock clock, @Value("${app.order.time-scale:10}") int timeScale) {
        this.catalog = catalog;
        this.clock = clock;
        this.timeScale = Math.max(1, timeScale);
    }

    public static int deliveryFeeFor(int itemTotal) {
        return itemTotal == 0 || itemTotal >= FREE_DELIVERY_ABOVE ? 0 : DELIVERY_FEE;
    }

    /** Turns the cart into an order and empties the cart. */
    public Order place(String sessionId, Cart cart, String paymentMethod, String paymentId) {
        synchronized (cart) {
            if (cart.isEmpty()) {
                throw new IllegalStateException("Cart is empty");
            }
            int itemTotal = cart.total();
            int fee = deliveryFeeFor(itemTotal);
            var restaurant = catalog.restaurant(cart.restaurantId());
            Order order = new Order("DA" + sequence.incrementAndGet(), sessionId, restaurant.id(), restaurant.name(),
                    cart.lines(), itemTotal, fee, itemTotal + fee, clock.instant(), restaurant.deliveryMinutes(),
                    paymentMethod, paymentId);
            orders.put(order.id(), order);
            cart.clear();
            return order;
        }
    }

    public Optional<Order> find(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public List<Order> forSession(String sessionId) {
        return orders.values().stream()
                .filter(o -> o.sessionId().equals(sessionId))
                .sorted(Comparator.comparing(Order::placedAt).reversed())
                .toList();
    }

    /** Orders not yet delivered, for the real-time ticker. */
    public List<Order> active() {
        return orders.values().stream().filter(o -> !"DELIVERED".equals(status(o))).toList();
    }

    /** Orders that reached DELIVERED in the last ~10 seconds of real time (for one final live update). */
    public List<Order> recentlyDelivered() {
        return orders.values().stream()
                .filter(o -> {
                    long scaled = scaledElapsedSeconds(o);
                    long eta = o.etaMinutes() * 60L;
                    return scaled >= eta && scaled < eta + 10L * timeScale;
                }).toList();
    }

    public String status(Order o) {
        double p = progress(o);
        long elapsedSeconds = scaledElapsedSeconds(o);
        if (elapsedSeconds < 60) return "CONFIRMED";
        if (p < 0.45) return "PREPARING";
        if (p < 1.0) return "OUT_FOR_DELIVERY";
        return "DELIVERED";
    }

    /** 0.0 at placement, 1.0 when delivered. */
    public double progress(Order o) {
        return Math.min(1.0, scaledElapsedSeconds(o) / (o.etaMinutes() * 60.0));
    }

    public long minutesLeft(Order o) {
        long left = o.etaMinutes() * 60L - scaledElapsedSeconds(o);
        return Math.max(0, (long) Math.ceil(left / 60.0));
    }

    private long scaledElapsedSeconds(Order o) {
        return Duration.between(o.placedAt(), clock.instant()).toSeconds() * timeScale;
    }

    /** Flat map for the UI and the LLM. */
    public Map<String, Object> view(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.id());
        m.put("restaurant", o.restaurantName());
        m.put("restaurantId", o.restaurantId());
        m.put("items", o.lines().stream().map(l -> l.quantity() + " x " + l.name()).toList());
        m.put("itemTotal", o.itemTotal());
        m.put("deliveryFee", o.deliveryFee());
        m.put("total", o.total());
        m.put("status", status(o));
        m.put("minutesLeft", minutesLeft(o));
        m.put("progress", Math.round(progress(o) * 100) / 100.0);
        m.put("placedAt", o.placedAt().toString());
        m.put("etaMinutes", o.etaMinutes());
        m.put("paymentMethod", o.paymentMethod());
        m.put("paymentId", o.paymentId());
        return m;
    }
}
