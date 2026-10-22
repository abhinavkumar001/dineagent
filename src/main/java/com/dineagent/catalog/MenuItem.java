package com.dineagent.catalog;

import java.util.List;

/** Prices are whole rupees. spiceLevel: 0 none, 1 mild, 2 medium, 3 hot. category groups the menu. */
public record MenuItem(String id, String restaurantId, String name, String description, int price,
                       boolean veg, int spiceLevel, List<String> tags, String category) {}
