package com.dineagent.web;

import com.dineagent.agent.AgentSession;
import com.dineagent.agent.SessionStore;
import com.dineagent.agent.Views;
import com.dineagent.cart.CartLine;
import com.dineagent.catalog.CatalogStore;
import com.dineagent.catalog.MenuItem;
import com.dineagent.catalog.MenuSearchService;
import com.dineagent.catalog.Restaurant;
import com.dineagent.order.Order;
import com.dineagent.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * The normal (non-AI) food-app API: restaurants, menus, search, cart, checkout and tracking.
 * It uses the same session cart as the AI agent, so whatever the agent adds shows up in the UI and vice versa.
 */
@RestController
@RequestMapping("/api")
public class StoreController {

    public record AddItemRequest(@NotBlank String itemId, @Min(1) @Max(10) Integer quantity) {}
    public record QuantityRequest(@Min(0) @Max(10) int quantity) {}
    public record CheckoutRequest(@NotBlank String sessionId) {}

    private final CatalogStore catalog;
    private final MenuSearchService search;
    private final SessionStore sessions;
    private final OrderService orders;

    public StoreController(CatalogStore catalog, MenuSearchService search, SessionStore sessions, OrderService orders) {
        this.catalog = catalog;
        this.search = search;
        this.sessions = sessions;
        this.orders = orders;
    }

    // ------------------------------------------------------------------ browse

    @GetMapping("/restaurants")
    public List<Map<String, Object>> restaurants(@RequestParam(required = false) String cuisine,
                                                 @RequestParam(defaultValue = "false") boolean pureVeg,
                                                 @RequestParam(defaultValue = "relevance") String sort) {
        Comparator<Restaurant> order = switch (sort) {
            case "rating" -> Comparator.comparingDouble(Restaurant::rating).reversed();
            case "delivery" -> Comparator.comparingInt(Restaurant::deliveryMinutes);
            case "cost" -> Comparator.comparingInt(Restaurant::costForTwo);
            default -> Comparator.comparingDouble((Restaurant r) -> r.rating() * 10 - r.deliveryMinutes() / 5.0).reversed();
        };
        return catalog.restaurants().stream()
                .filter(r -> !pureVeg || r.pureVeg())
                .filter(r -> cuisine == null || cuisine.isBlank() || r.cuisine().equalsIgnoreCase(cuisine))
                .sorted(order)
                .map(this::restaurantCard)
                .toList();
    }

    @GetMapping("/restaurants/{id}")
    public ResponseEntity<Map<String, Object>> restaurant(@PathVariable String id) {
        Restaurant r = catalog.restaurant(id);
        if (r == null) return ResponseEntity.notFound().build();
        Map<String, List<MenuItem>> sections = new LinkedHashMap<>();
        catalog.items().stream().filter(i -> i.restaurantId().equals(id))
                .forEach(i -> sections.computeIfAbsent(i.category(), k -> new ArrayList<>()).add(i));
        Map<String, Object> body = restaurantCard(r);
        body.put("menu", sections.entrySet().stream()
                .map(e -> Map.of("category", e.getKey(), "items", e.getValue())).toList());
        return ResponseEntity.ok(body);
    }

    /** Dish search with hybrid (semantic + keyword) ranking and exact filters. */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(defaultValue = "") String q,
                                            @RequestParam(required = false) Boolean veg,
                                            @RequestParam(required = false) Integer maxPrice,
                                            @RequestParam(required = false) Integer maxMinutes,
                                            @RequestParam(required = false) Integer minSpice,
                                            @RequestParam(required = false) Integer maxSpice) {
        return search.search(new MenuSearchService.Filters(q, veg, maxPrice, maxMinutes, minSpice, maxSpice, null, 10))
                .stream().map(res -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("item", res.item());
                    m.put("restaurant", restaurantCard(res.restaurant()));
                    m.put("relevance", res.relevance());
                    return m;
                }).toList();
    }

    private Map<String, Object> restaurantCard(Restaurant r) {
        List<MenuItem> items = catalog.items().stream().filter(i -> i.restaurantId().equals(r.id())).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("name", r.name());
        m.put("cuisine", r.cuisine());
        m.put("area", r.area());
        m.put("rating", r.rating());
        m.put("deliveryMinutes", r.deliveryMinutes());
        m.put("pureVeg", r.pureVeg());
        m.put("costForTwo", r.costForTwo());
        m.put("offer", r.offer());
        m.put("popular", items.stream().limit(3).map(MenuItem::name).toList());
        return m;
    }

    // ------------------------------------------------------------------ cart

    @GetMapping("/cart/{sessionId}")
    public Map<String, Object> cart(@PathVariable String sessionId) {
        return Views.cart(sessions.getOrCreate(sessionId).cart());
    }

    @PostMapping("/cart/{sessionId}/items")
    public ResponseEntity<Map<String, Object>> addItem(@PathVariable String sessionId, @Valid @RequestBody AddItemRequest req) {
        AgentSession session = sessions.getOrCreate(sessionId);
        Optional<MenuItem> item = catalog.item(req.itemId());
        if (item.isEmpty()) return error(HttpStatus.NOT_FOUND, "Dish not found");
        MenuItem m = item.get();
        String problem = session.cart().add(new CartLine(m.id(), m.name(), m.restaurantId(),
                catalog.restaurant(m.restaurantId()).name(), m.price(), req.quantity() == null ? 1 : req.quantity()));
        if (problem != null) return error(HttpStatus.CONFLICT, problem);
        return ResponseEntity.ok(Views.cart(session.cart()));
    }

    @PutMapping("/cart/{sessionId}/items/{itemId}")
    public ResponseEntity<Map<String, Object>> setQuantity(@PathVariable String sessionId, @PathVariable String itemId,
                                                           @Valid @RequestBody QuantityRequest req) {
        AgentSession session = sessions.getOrCreate(sessionId);
        String problem = session.cart().setQuantity(itemId, req.quantity());
        if (problem != null) return error(HttpStatus.BAD_REQUEST, problem);
        return ResponseEntity.ok(Views.cart(session.cart()));
    }

    @DeleteMapping("/cart/{sessionId}")
    public Map<String, Object> clearCart(@PathVariable String sessionId) {
        AgentSession session = sessions.getOrCreate(sessionId);
        session.cart().clear();
        return Views.cart(session.cart());
    }

    // ------------------------------------------------------------------ orders

    /** Clicking "Place order" in the UI is the customer's explicit confirmation. */
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> placeOrder(@Valid @RequestBody CheckoutRequest req) {
        AgentSession session = sessions.getOrCreate(req.sessionId());
        if (session.cart().isEmpty()) return error(HttpStatus.BAD_REQUEST, "Your cart is empty");
        Order order = orders.place(session.id(), session.cart());
        session.setLastOrderId(order.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(Views.order(order, orders.clock()));
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> myOrders(@RequestParam String sessionId) {
        return orders.forSession(sessionId).stream().map(o -> Views.order(o, orders.clock())).toList();
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> order(@PathVariable String orderId, @RequestParam String sessionId) {
        return orders.find(orderId)
                .filter(o -> o.sessionId().equals(sessionId))
                .map(o -> ResponseEntity.ok(Views.order(o, orders.clock())))
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
