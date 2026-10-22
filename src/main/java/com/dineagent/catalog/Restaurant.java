package com.dineagent.catalog;

public record Restaurant(String id, String name, String cuisine, String area, double rating,
                         int deliveryMinutes, boolean pureVeg) {}
