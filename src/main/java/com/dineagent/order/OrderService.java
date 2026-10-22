package com.dineagent.order;

import com.dineagent.cart.Cart;
import com.dineagent.catalog.CatalogStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
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

    public OrderService(CatalogStore catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Turns the cart into an order and empties the cart. */
    public Order place(String sessionId, Cart cart) {
        synchronized (cart) {
            if (cart.isEmpty()) {
                throw new IllegalStateException("Cart is empty");
            }
            int itemTotal = cart.total();
            int fee = itemTotal >= FREE_DELIVERY_ABOVE ? 0 : DELIVERY_FEE;
            var restaurant = catalog.restaurant(cart.restaurantId());
            Order order = new Order("DA" + sequence.incrementAndGet(), sessionId, restaurant.name(), cart.lines(),
                    itemTotal, fee, itemTotal + fee, clock.instant(), restaurant.deliveryMinutes());
            orders.put(order.id(), order);
            cart.clear();
            return order;
        }
    }

    public Optional<Order> find(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public Clock clock() {
        return clock;
    }
}
