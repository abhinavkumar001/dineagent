package com.dineagent.cart;

import java.util.ArrayList;
import java.util.List;

/** One customer's cart. Like real food apps, it can only hold items from one restaurant. */
public class Cart {

    public static final int MAX_QUANTITY = 10;
    public static final int MAX_TOTAL = 5_000;

    private final List<CartLine> lines = new ArrayList<>();

    public synchronized List<CartLine> lines() {
        return List.copyOf(lines);
    }

    public synchronized int total() {
        return lines.stream().mapToInt(CartLine::lineTotal).sum();
    }

    public synchronized String restaurantId() {
        return lines.isEmpty() ? null : lines.get(0).restaurantId();
    }

    /** Returns null on success, or a reason the item can't be added. */
    public synchronized String add(CartLine line) {
        if (restaurantId() != null && !restaurantId().equals(line.restaurantId())) {
            return "Cart already has items from " + lines.get(0).restaurantName()
                    + ". A cart can hold one restaurant at a time; clear it first.";
        }
        int existing = lines.stream().filter(l -> l.itemId().equals(line.itemId())).mapToInt(CartLine::quantity).sum();
        int newQuantity = existing + line.quantity();
        if (newQuantity > MAX_QUANTITY) {
            return "At most " + MAX_QUANTITY + " of one item per order.";
        }
        if (total() + line.lineTotal() > MAX_TOTAL) {
            return "Cart total can't exceed ₹" + MAX_TOTAL + " in one order.";
        }
        lines.removeIf(l -> l.itemId().equals(line.itemId()));
        lines.add(new CartLine(line.itemId(), line.name(), line.restaurantId(), line.restaurantName(),
                line.unitPrice(), newQuantity));
        return null;
    }

    public synchronized boolean remove(String itemId) {
        return lines.removeIf(l -> l.itemId().equals(itemId));
    }

    public synchronized void clear() {
        lines.clear();
    }

    public synchronized boolean isEmpty() {
        return lines.isEmpty();
    }
}
