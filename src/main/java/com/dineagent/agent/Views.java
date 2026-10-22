package com.dineagent.agent;

import com.dineagent.cart.Cart;
import com.dineagent.cart.CartLine;
import com.dineagent.order.OrderService;

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
        int fee = OrderService.deliveryFeeFor(total);
        m.put("restaurantId", lines.isEmpty() ? null : lines.get(0).restaurantId());
        m.put("itemTotal", total);
        m.put("deliveryFee", fee);
        m.put("toPay", total + fee);
        m.put("freeDeliveryGap", total == 0 ? 0 : Math.max(0, OrderService.FREE_DELIVERY_ABOVE - total));
        m.put("count", lines.stream().mapToInt(CartLine::quantity).sum());
        return m;
    }
}
