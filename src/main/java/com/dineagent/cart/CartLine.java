package com.dineagent.cart;

public record CartLine(String itemId, String name, String restaurantId, String restaurantName,
                       int unitPrice, int quantity) {

    public int lineTotal() {
        return unitPrice * quantity;
    }
}
