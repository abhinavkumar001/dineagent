package com.dineagent.agent.tools;

import com.dineagent.agent.AgentSession;
import com.dineagent.agent.Tool;
import com.dineagent.agent.Views;
import com.dineagent.cart.CartLine;
import com.dineagent.catalog.CatalogStore;
import com.dineagent.catalog.MenuItem;
import com.dineagent.llm.ToolSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.dineagent.agent.Args.*;

/** add_to_cart, remove_from_cart, view_cart and clear_cart. */
@Configuration
public class CartTools {

    @Bean
    public Tool addToCart(CatalogStore catalog) {
        return new Tool() {
            public ToolSpec spec() {
                return new ToolSpec("add_to_cart",
                        "Add a dish to the cart using the itemId from search_menu results. Never guess an itemId.",
                        Map.of("type", "OBJECT",
                                "properties", Map.of(
                                        "item_id", prop("STRING", "itemId from search_menu, e.g. i401"),
                                        "quantity", prop("INTEGER", "How many, default 1")),
                                "required", List.of("item_id")));
            }

            public Map<String, Object> execute(AgentSession session, Map<String, Object> args) {
                String id = str(args, "item_id");
                Optional<MenuItem> item = id == null ? Optional.empty() : catalog.item(id);
                if (item.isEmpty()) {
                    // Guardrail: the model can only add dishes that really exist.
                    return Map.of("ok", false, "error", "Unknown itemId '" + id + "'. Search the menu first and use an itemId from the results.",
                            "summary", "rejected unknown item " + id);
                }
                Integer q = integer(args, "quantity");
                int quantity = q == null || q < 1 ? 1 : q;
                MenuItem m = item.get();
                String problem = session.cart().add(new CartLine(m.id(), m.name(), m.restaurantId(),
                        catalog.restaurant(m.restaurantId()).name(), m.price(), quantity));
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", problem == null);
                if (problem != null) out.put("error", problem);
                out.put("cart", Views.cart(session.cart()));
                out.put("summary", problem == null ? "added " + quantity + " x " + m.name() : "not added: " + problem);
                return out;
            }
        };
    }

    @Bean
    public Tool removeFromCart() {
        return new Tool() {
            public ToolSpec spec() {
                return new ToolSpec("remove_from_cart", "Remove a dish from the cart by itemId.",
                        Map.of("type", "OBJECT", "properties", Map.of("item_id", prop("STRING", "itemId to remove")),
                                "required", List.of("item_id")));
            }

            public Map<String, Object> execute(AgentSession session, Map<String, Object> args) {
                boolean removed = session.cart().remove(str(args, "item_id"));
                return Map.of("ok", removed, "cart", Views.cart(session.cart()),
                        "summary", removed ? "removed " + str(args, "item_id") : "item not in cart");
            }
        };
    }

    @Bean
    public Tool viewCart() {
        return new Tool() {
            public ToolSpec spec() {
                return new ToolSpec("view_cart", "Show what is in the cart and the item total.",
                        Map.of("type", "OBJECT", "properties", Map.of()));
            }

            public Map<String, Object> execute(AgentSession session, Map<String, Object> args) {
                Map<String, Object> out = new LinkedHashMap<>(Views.cart(session.cart()));
                out.put("summary", session.cart().lines().size() + " line(s), ₹" + session.cart().total());
                return out;
            }
        };
    }

    @Bean
    public Tool clearCart() {
        return new Tool() {
            public ToolSpec spec() {
                return new ToolSpec("clear_cart", "Empty the cart, e.g. to switch to another restaurant.",
                        Map.of("type", "OBJECT", "properties", Map.of()));
            }

            public Map<String, Object> execute(AgentSession session, Map<String, Object> args) {
                session.cart().clear();
                return Map.of("ok", true, "summary", "cart cleared");
            }
        };
    }
}
