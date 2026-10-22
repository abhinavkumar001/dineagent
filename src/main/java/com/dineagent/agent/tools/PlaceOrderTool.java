package com.dineagent.agent.tools;

import com.dineagent.agent.AgentSession;
import com.dineagent.agent.Tool;
import com.dineagent.agent.Views;
import com.dineagent.llm.ToolSpec;
import com.dineagent.order.Order;
import com.dineagent.order.OrderService;
import com.dineagent.payment.PaymentService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.dineagent.agent.Args.*;

/**
 * Places the order. This is the one action that spends the customer's money, so there is a
 * guardrail in code, not just in the prompt: the customer's latest message must actually say yes.
 * Even if the LLM is tricked or makes a mistake, it cannot order on its own.
 */
@Component
public class PlaceOrderTool implements Tool {

    private static final Pattern NO = Pattern.compile("\\b(no|nope|nah|don'?t|do not|not|cancel|wait|stop|hold on)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern YES = Pattern.compile(
            "\\b(yes|yeah|yep|yup|ya|haan|ha|ok|okay|sure|confirm|confirmed|go ahead|place (it|the order|my order|order)|order it|do it|proceed)\\b",
            Pattern.CASE_INSENSITIVE);

    private final OrderService orders;
    private final PaymentService payments;

    public PlaceOrderTool(OrderService orders, PaymentService payments) {
        this.orders = orders;
        this.payments = payments;
    }

    static boolean customerConfirmed(String message) {
        return message != null && YES.matcher(message).find() && !NO.matcher(message).find();
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec("place_order",
                "Place the order for the current cart. Only call after showing the cart and total to the customer "
                        + "and the customer clearly saying yes in their latest message.",
                Map.of("type", "OBJECT",
                        "properties", Map.of("confirmed", prop("BOOLEAN", "true only if the customer explicitly confirmed")),
                        "required", List.of("confirmed")));
    }

    @Override
    public Map<String, Object> execute(AgentSession session, Map<String, Object> args) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (session.cart().isEmpty()) {
            out.put("ok", false);
            out.put("error", "Cart is empty.");
            out.put("summary", "blocked: empty cart");
            return out;
        }
        if (!Boolean.TRUE.equals(bool(args, "confirmed")) || !customerConfirmed(session.lastUserMessage())) {
            out.put("ok", false);
            out.put("status", "CONFIRMATION_REQUIRED");
            out.put("cart", Views.cart(session.cart()));
            out.put("instruction", "Show the items and total, then ask the customer to reply 'yes' to place the order.");
            out.put("summary", "blocked: waiting for customer confirmation");
            return out;
        }
        if (payments.enabled()) {
            // The agent can never pay: online payment needs the customer to complete Razorpay Checkout themselves.
            int total = session.cart().total();
            out.put("ok", false);
            out.put("status", "PAYMENT_REQUIRED");
            out.put("toPay", total + OrderService.deliveryFeeFor(total));
            out.put("instruction", "Tell the customer to tap the Pay button to complete the order securely with Razorpay.");
            out.put("summary", "waiting for customer to pay");
            return out;
        }
        Order order = orders.place(session.id(), session.cart(), "PAY_ON_DELIVERY", null);
        session.setLastOrderId(order.id());
        out.put("ok", true);
        out.put("order", orders.view(order));
        out.put("summary", "order " + order.id() + " placed, ₹" + order.total());
        return out;
    }
}
