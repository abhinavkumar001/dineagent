package com.dineagent.agent.tools;

import com.dineagent.agent.AgentSession;
import com.dineagent.agent.Tool;
import com.dineagent.agent.Views;
import com.dineagent.llm.ToolSpec;
import com.dineagent.order.OrderService;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.dineagent.agent.Args.*;

@Component
public class TrackOrderTool implements Tool {

    private final OrderService orders;

    public TrackOrderTool(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec("track_order", "Get the status and remaining time of an order. Defaults to the customer's latest order.",
                Map.of("type", "OBJECT", "properties", Map.of("order_id", prop("STRING", "Order id like DA1001, optional"))));
    }

    @Override
    public Map<String, Object> execute(AgentSession session, Map<String, Object> args) {
        String id = str(args, "order_id");
        if (id == null || id.isBlank()) id = session.lastOrderId();
        if (id == null) {
            return Map.of("ok", false, "error", "This customer has not placed an order yet.", "summary", "no order");
        }
        return orders.find(id)
                .filter(o -> o.sessionId().equals(session.id())) // customers can only track their own orders
                .<Map<String, Object>>map(o -> Map.of("ok", true, "order", Views.order(o, orders.clock()),
                        "summary", o.id() + " is " + o.status(orders.clock())))
                .orElse(Map.of("ok", false, "error", "No order " + id + " for this customer.", "summary", "order not found"));
    }
}
