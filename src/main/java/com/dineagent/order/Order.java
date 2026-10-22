package com.dineagent.order;

import com.dineagent.cart.CartLine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record Order(String id, String sessionId, String restaurantName, List<CartLine> lines,
                    int itemTotal, int deliveryFee, int total, Instant placedAt, int etaMinutes) {

    /** Status is derived from time since the order was placed, to simulate a live order. */
    public String status(Clock clock) {
        long elapsed = Duration.between(placedAt, clock.instant()).toSeconds();
        long eta = etaMinutes * 60L;
        if (elapsed < 60) return "CONFIRMED";
        if (elapsed < eta * 0.45) return "PREPARING";
        if (elapsed < eta) return "OUT_FOR_DELIVERY";
        return "DELIVERED";
    }

    public long minutesLeft(Clock clock) {
        long elapsed = Duration.between(placedAt, clock.instant()).toMinutes();
        return Math.max(0, etaMinutes - elapsed);
    }
}
