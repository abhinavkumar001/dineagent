package com.dineagent.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Restaurants and menus loaded from data/catalog.json. Read-only, so a map is enough. */
@Component
public class CatalogStore {

    public record CatalogFile(List<Restaurant> restaurants, List<MenuItem> items) {}

    private final Map<String, Restaurant> restaurants = new LinkedHashMap<>();
    private final Map<String, MenuItem> items = new LinkedHashMap<>();

    public CatalogStore(ObjectMapper json) {
        try (InputStream in = new ClassPathResource("data/catalog.json").getInputStream()) {
            CatalogFile file = json.readValue(in, CatalogFile.class);
            file.restaurants().forEach(r -> restaurants.put(r.id(), r));
            file.items().forEach(i -> items.put(i.id(), i));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load data/catalog.json", e);
        }
    }

    public List<MenuItem> items() {
        return List.copyOf(items.values());
    }

    public List<Restaurant> restaurants() {
        return List.copyOf(restaurants.values());
    }

    public Optional<MenuItem> item(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public Restaurant restaurant(String id) {
        return restaurants.get(id);
    }
}
