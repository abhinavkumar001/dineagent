package com.dineagent.order;

import com.dineagent.cart.CartLine;

import java.time.Instant;
import java.util.List;

/**
 * A placed order. paymentMethod is RAZORPAY or PAY_ON_DELIVERY; paymentId is Razorpay's payment id when paid online.
 * Live status is computed by OrderService from the time since placedAt.
 */
public record Order(String id, String sessionId, String restaurantId, String restaurantName, List<CartLine> lines,
                    int itemTotal, int deliveryFee, int total, Instant placedAt, int etaMinutes,
                    String paymentMethod, String paymentId) {}
