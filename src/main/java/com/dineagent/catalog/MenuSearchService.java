package com.dineagent.catalog;

import com.dineagent.ai.EmbeddingClient;
import com.dineagent.ai.HybridRetriever;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Menu search used by the agent's search_menu tool:
 *   1. hard filters (veg, max price, delivery time, spice, cuisine) are applied exactly, never left to the LLM
 *   2. the remaining dishes are ranked by hybrid retrieval on the free-text query
 */
@Service
public class MenuSearchService {

    public record Filters(String query, Boolean veg, Integer maxPrice, Integer maxDeliveryMinutes,
                          Integer minSpice, Integer maxSpice, String cuisine, Integer limit) {}

    public record Result(MenuItem item, Restaurant restaurant, double relevance) {}

    private final CatalogStore catalog;
    private final List<MenuItem> indexed;
    private final HybridRetriever retriever;

    public MenuSearchService(CatalogStore catalog, EmbeddingClient embeddings) {
        this.catalog = catalog;
        this.indexed = catalog.items();
        this.retriever = new HybridRetriever(indexed.stream().map(this::document).toList(), embeddings);
    }

    public boolean semanticEnabled() {
        return retriever.semanticEnabled();
    }

    public List<Result> search(Filters f) {
        int limit = f.limit() == null ? 5 : Math.max(1, Math.min(f.limit(), 10));
        boolean hasQuery = f.query() != null && !f.query().isBlank();

        List<Result> results = new ArrayList<>();
        if (hasQuery) {
            for (HybridRetriever.Hit hit : retriever.rank(f.query())) {
                MenuItem item = indexed.get(hit.index());
                if (passes(item, f)) results.add(new Result(item, catalog.restaurant(item.restaurantId()), round(hit.score())));
            }
            // Keep only dishes that are actually related, unless nothing matched at all.
            double best = results.isEmpty() ? 0 : results.get(0).relevance();
            if (best > 0) {
                results.removeIf(r -> r.relevance() < best * 0.35);
            }
        } else {
            indexed.stream().filter(i -> passes(i, f))
                    .map(i -> new Result(i, catalog.restaurant(i.restaurantId()), 0))
                    .sorted(Comparator.comparingDouble((Result r) -> r.restaurant().rating()).reversed())
                    .forEach(results::add);
        }
        return results.subList(0, Math.min(limit, results.size()));
    }

    private boolean passes(MenuItem i, Filters f) {
        Restaurant r = catalog.restaurant(i.restaurantId());
        if (f.veg() != null && f.veg() && !i.veg()) return false;
        if (f.veg() != null && !f.veg() && i.veg()) return false;
        if (f.maxPrice() != null && i.price() > f.maxPrice()) return false;
        if (f.maxDeliveryMinutes() != null && r.deliveryMinutes() > f.maxDeliveryMinutes()) return false;
        if (f.minSpice() != null && i.spiceLevel() < f.minSpice()) return false;
        if (f.maxSpice() != null && i.spiceLevel() > f.maxSpice()) return false;
        if (f.cuisine() != null && !f.cuisine().isBlank()
                && !r.cuisine().toLowerCase(Locale.ROOT).contains(f.cuisine().toLowerCase(Locale.ROOT))) return false;
        return true;
    }

    /** The text that gets embedded and indexed for each dish. */
    private String document(MenuItem i) {
        Restaurant r = catalog.restaurant(i.restaurantId());
        String spice = switch (i.spiceLevel()) { case 0 -> "not spicy"; case 1 -> "mild"; case 2 -> "medium spicy"; default -> "very spicy hot"; };
        return i.name() + ". " + i.description() + ". " + String.join(", ", i.tags()) + ". "
                + (i.veg() ? "vegetarian veg" : "non-vegetarian non-veg") + ", " + spice + ". "
                + r.cuisine() + " food from " + r.name() + ".";
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
