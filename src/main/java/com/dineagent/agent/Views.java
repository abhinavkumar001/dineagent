package com.dineagent.agent;

import com.dineagent.cart.Cart;
import com.dineagent.cart.CartLine;
import com.dineagent.order.Order;
import com.dineagent.order.OrderService;

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
        int total = cart.total();
        int fee = total == 0 || total >= OrderService.FREE_DELIVERY_ABOVE ? 0 : OrderService.DELIVERY_FEE;
        m.put("restaurantId", lines.isEmpty() ? null : lines.get(0).restaurantId());
        m.put("itemTotal", total);
        m.put("deliveryFee", fee);
        m.put("toPay", total + fee);
        m.put("freeDeliveryGap", total == 0 ? 0 : Math.max(0, OrderService.FREE_DELIVERY_ABOVE - total));
        m.put("count", lines.stream().mapToInt(CartLine::quantity).sum());
        return m;
    }

    public static Map<String, Object> order(Order o, Clock clock) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.id());
        m.put("restaurant", o.restaurantName());
        m.put("items", o.lines().stream().map(l -> l.quantity() + " x " + l.name()).toList());
        m.put("placedAt", o.placedAt().toString());
        m.put("etaMinutes", o.etaMinutes());
        m.put("itemTotal", o.itemTotal());
        m.put("deliveryFee", o.deliveryFee());
        m.put("total", o.total());
        m.put("status", o.status(clock));
        m.put("minutesLeft", o.minutesLeft(clock));
        return m;
    }
}
