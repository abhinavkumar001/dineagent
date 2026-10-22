package com.dineagent.payment;

import com.dineagent.agent.AgentSession;
import com.dineagent.agent.SessionStore;
import com.dineagent.cart.Cart;
import com.dineagent.order.Order;
import com.dineagent.order.OrderService;
import com.dineagent.realtime.SessionEvents;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Razorpay payment flow:
 *   1. createPayment  - server computes the amount from the cart (never trusts the browser) and creates a Razorpay order
 *   2. browser opens Razorpay Checkout; the customer pays (test mode: no real money)
 *   3. verify         - server checks HMAC_SHA256(order_id + "|" + payment_id, key_secret) == signature,
 *                       then places the food order. A forged "success" from the browser fails here.
 *   4. webhook        - backup path: if the customer paid but closed the tab before step 3,
 *                       Razorpay calls us directly and we still place the order.
 * Each payment can create at most one order (idempotent), whichever of 3 or 4 arrives first.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    static final class Pending {
        final String razorpayOrderId;
        final String sessionId;
        final int amountRupees;
        final String cartFingerprint;
        String foodOrderId;

        Pending(String razorpayOrderId, String sessionId, int amountRupees, String cartFingerprint) {
            this.razorpayOrderId = razorpayOrderId;
            this.sessionId = sessionId;
            this.amountRupees = amountRupees;
            this.cartFingerprint = cartFingerprint;
        }
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final RazorpayClient razorpay;
    private final SessionStore sessions;
    private final OrderService orders;
    private final SessionEvents events;
    private final ObjectMapper json;
    private final String webhookSecret;

    public PaymentService(RazorpayClient razorpay, SessionStore sessions, OrderService orders, SessionEvents events,
                          ObjectMapper json, @Value("${app.razorpay.webhook-secret:}") String webhookSecret) {
        this.razorpay = razorpay;
        this.sessions = sessions;
        this.orders = orders;
        this.events = events;
        this.json = json;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    public boolean enabled() {
        return razorpay.configured();
    }

    public Map<String, Object> createPayment(String sessionId) {
        if (!enabled()) throw new PaymentException("Online payments are not configured.");
        Cart cart = sessions.getOrCreate(sessionId).cart();
        int itemTotal;
        String fingerprint;
        synchronized (cart) {
            if (cart.isEmpty()) throw new PaymentException("Your cart is empty.");
            itemTotal = cart.total();
            fingerprint = fingerprint(cart);
        }
        int toPay = itemTotal + OrderService.deliveryFeeFor(itemTotal);
        String receipt = "rcpt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String razorpayOrderId = razorpay.createOrder(toPay * 100, receipt, Map.of("sessionId", sessionId));
        pending.put(razorpayOrderId, new Pending(razorpayOrderId, sessionId, toPay, fingerprint));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("keyId", razorpay.keyId());      // the key id is public; the secret never leaves the server
        out.put("razorpayOrderId", razorpayOrderId);
        out.put("amount", toPay * 100);
        out.put("currency", "INR");
        out.put("toPay", toPay);
        return out;
    }

    /** Called by the browser after Razorpay Checkout succeeds. */
    public Map<String, Object> verify(String sessionId, String razorpayOrderId, String paymentId, String signature) {
        Pending p = pending.get(razorpayOrderId);
        if (p == null || !p.sessionId.equals(sessionId)) throw new PaymentException("Unknown payment.");
        String expected = hmacSha256Hex(razorpay.keySecret(), razorpayOrderId + "|" + paymentId);
        if (!constantTimeEquals(expected, signature)) {
            log.warn("Invalid Razorpay signature for {}", razorpayOrderId);
            throw new PaymentException("Payment could not be verified.");
        }
        return orders.view(complete(p, paymentId));
    }

    /** Razorpay -> our server. Signature is HMAC_SHA256(raw body, webhook secret). */
    public void webhook(String rawBody, String signature) {
        if (webhookSecret.isEmpty()) throw new PaymentException("Webhook secret not configured.");
        if (signature == null || !constantTimeEquals(hmacSha256Hex(webhookSecret, rawBody), signature)) {
            throw new PaymentException("Invalid webhook signature.");
        }
        try {
            JsonNode root = json.readTree(rawBody);
            String event = root.path("event").asText();
            if (!event.equals("payment.captured") && !event.equals("order.paid")) return;
            JsonNode payment = root.path("payload").path("payment").path("entity");
            Pending p = pending.get(payment.path("order_id").asText());
            if (p != null) complete(p, payment.path("id").asText());
        } catch (IOException e) {
            throw new PaymentException("Bad webhook body.");
        }
    }

    /** Places the food order exactly once per payment. */
    private Order complete(Pending p, String paymentId) {
        synchronized (p) {
            if (p.foodOrderId != null) {
                return orders.find(p.foodOrderId).orElseThrow();
            }
            AgentSession session = sessions.getOrCreate(p.sessionId);
            Cart cart = session.cart();
            synchronized (cart) {
                if (cart.isEmpty() || !fingerprint(cart).equals(p.cartFingerprint)) {
                    // A real system would issue an automatic refund here via the Refunds API.
                    log.warn("Cart changed after payment {} started", p.razorpayOrderId);
                    throw new PaymentException("Your cart changed during payment. The amount will be refunded; please order again.");
                }
                Order order = orders.place(p.sessionId, cart, "RAZORPAY", paymentId);
                p.foodOrderId = order.id();
                session.setLastOrderId(order.id());
                events.orderChanged(order);
                events.cartChanged(p.sessionId);
                return order;
            }
        }
    }

    private static String fingerprint(Cart cart) {
        StringBuilder sb = new StringBuilder();
        cart.lines().forEach(l -> sb.append(l.itemId()).append('x').append(l.quantity()).append(';'));
        return sb.toString();
    }

    static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Compares without leaking timing information about where the strings differ. */
    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
