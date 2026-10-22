package com.dineagent.agent;

import com.dineagent.cart.Cart;
import com.dineagent.llm.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/** Everything the agent remembers about one customer conversation. */
public class AgentSession {

    private static final int MAX_HISTORY = 40;

    private final String id;
    private final Cart cart = new Cart();
    private final List<ChatMessage> history = new ArrayList<>();
    private String lastUserMessage = "";
    private String lastOrderId;

    public AgentSession(String id) {
        this.id = id;
    }

    public String id() { return id; }
    public Cart cart() { return cart; }
    public List<ChatMessage> history() { return history; }
    public String lastUserMessage() { return lastUserMessage; }
    public String lastOrderId() { return lastOrderId; }
    public void setLastOrderId(String orderId) { this.lastOrderId = orderId; }

    public void addUser(String text) {
        lastUserMessage = text;
        history.add(ChatMessage.user(text));
        trim();
    }

    public void add(ChatMessage message) {
        history.add(message);
    }

    /** Keeps the context window small, always starting at a user message so tool calls stay paired with results. */
    private void trim() {
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            while (!history.isEmpty() && history.get(0).role() != ChatMessage.Role.USER) {
                history.remove(0);
            }
        }
    }
}
