package com.dineagent.catalog;

/** offer may be null. costForTwo is an approximate price for two people, in rupees. */
public record Restaurant(String id, String name, String cuisine, String area, double rating,
                         int deliveryMinutes, boolean pureVeg, int costForTwo, String offer) {}
