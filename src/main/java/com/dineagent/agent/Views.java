package com.dineagent.agent;

import com.dineagent.cart.Cart;
import com.dineagent.cart.CartLine;
import com.dineagent.order.Order;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plain maps sent to the LLM and the UI. Small and flat, so the model reads them reliably. */
public final class Views {

    private Views() {
    }

    public static Map<String, Object> cart(Cart cart) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<CartLine> lines = cart.lines();
        m.put("restaurant", lines.isEmpty() ? null : lines.get(0).restaurantName());
        m.put("items", lines.stream().map(l -> Map.of(
                "itemId", l.itemId(), "name", l.name(), "quantity", l.quantity(),
                "unitPrice", l.unitPrice(), "lineTotal", l.lineTotal())).toList());
        m.put("itemTotal", cart.total());
        return m;
    }

    public static Map<String, Object> order(Order o, Clock clock) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.id());
        m.put("restaurant", o.restaurantName());
        m.put("items", o.lines().stream().map(l -> l.quantity() + " x " + l.name()).toList());
        m.put("itemTotal", o.itemTotal());
        m.put("deliveryFee", o.deliveryFee());
        m.put("total", o.total());
        m.put("status", o.status(clock));
        m.put("minutesLeft", o.minutesLeft(clock));
        return m;
    }
}
