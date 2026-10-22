package com.dineagent.agent.tools;

import com.dineagent.agent.AgentSession;
import com.dineagent.agent.Tool;
import com.dineagent.catalog.MenuSearchService;
import com.dineagent.llm.ToolSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dineagent.agent.Args.*;

@Component
public class SearchMenuTool implements Tool {

    private final MenuSearchService search;

    public SearchMenuTool(MenuSearchService search) {
        this.search = search;
    }

    @Override
    public ToolSpec spec() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", prop("STRING", "What the customer wants in their own words, e.g. 'light dinner', 'paneer', 'biryani'. Leave empty to list top-rated dishes."));
        props.put("veg", prop("BOOLEAN", "true = vegetarian only, false = non-vegetarian only. Omit if the customer didn't say."));
        props.put("max_price", prop("INTEGER", "Highest price per dish in rupees."));
        props.put("max_delivery_minutes", prop("INTEGER", "Latest acceptable delivery time in minutes."));
        props.put("min_spice", prop("INTEGER", "Minimum spice level 0-3. Use 2 for 'spicy', 3 for 'very spicy'."));
        props.put("max_spice", prop("INTEGER", "Maximum spice level 0-3. Use 1 for 'not too spicy', 0 for 'no spice'."));
        props.put("cuisine", prop("STRING", "Cuisine such as North Indian, South Indian, Chinese, Italian, Biryani, Healthy, Desserts."));
        props.put("limit", prop("INTEGER", "How many dishes to return, 1-10. Default 5."));
        return new ToolSpec("search_menu",
                "Search dishes across all restaurants. Filters are applied exactly. Always use this before suggesting any dish.",
                Map.of("type", "OBJECT", "properties", props));
    }

    @Override
    public Map<String, Object> execute(AgentSession session, Map<String, Object> args) {
        List<MenuSearchService.Result> results = search.search(new MenuSearchService.Filters(
                str(args, "query"), bool(args, "veg"), integer(args, "max_price"), integer(args, "max_delivery_minutes"),
                integer(args, "min_spice"), integer(args, "max_spice"), str(args, "cuisine"), integer(args, "limit")));

        List<Map<String, Object>> dishes = results.stream().map(r -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("itemId", r.item().id());
            d.put("name", r.item().name());
            d.put("restaurant", r.restaurant().name());
            d.put("price", r.item().price());
            d.put("veg", r.item().veg());
            d.put("spiceLevel", r.item().spiceLevel());
            d.put("deliveryMinutes", r.restaurant().deliveryMinutes());
            d.put("rating", r.restaurant().rating());
            d.put("description", r.item().description());
            return d;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", dishes.size());
        out.put("dishes", dishes);
        if (dishes.isEmpty()) {
            out.put("note", "No dish matches all filters. Tell the customer and suggest relaxing one filter.");
        }
        out.put("summary", dishes.size() + " dish(es) found");
        return out;
    }
}
