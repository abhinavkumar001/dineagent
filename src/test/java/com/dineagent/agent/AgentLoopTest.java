package com.dineagent.agent;

import com.dineagent.ScriptedLlm;
import com.dineagent.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The agent loop and its guardrails, driven by a scripted LLM. */
@SpringBootTest
@Import(TestConfig.class)
class AgentLoopTest {

    @Autowired private AgentService agent;
    @Autowired private ScriptedLlm llm;

    private String session;

    @BeforeEach
    void setUp() {
        llm.reset();
        session = UUID.randomUUID().toString();
    }

    @Test
    void searchThenAddRunsToolsAndUpdatesCart() {
        llm.callTool("search_menu", Map.of("query", "dosa", "veg", true))
           .callTool("add_to_cart", Map.of("item_id", "i401", "quantity", 2))
           .say("Added 2 Masala Dosa.");

        ChatResponse r = agent.chat(session, "Get me two masala dosas");

        assertEquals(List.of("search_menu", "add_to_cart"), r.trace().stream().map(TraceStep::tool).toList());
        assertEquals("Added 2 Masala Dosa.", r.reply());
        assertEquals(220, r.cart().get("itemTotal"));
        assertEquals(3, r.llmCalls());
    }

    @Test
    void orderIsBlockedUntilTheCustomerSaysYes() {
        llm.callTool("add_to_cart", Map.of("item_id", "i401"))
           .callTool("place_order", Map.of("confirmed", true)) // model tries to order on its own
           .say("Your cart has 1 Masala Dosa for ₹110. Shall I place it?");
        ChatResponse first = agent.chat(session, "add a masala dosa");

        Map<?, ?> blocked = (Map<?, ?>) first.toolResults().get(1).get("result");
        assertEquals("CONFIRMATION_REQUIRED", blocked.get("status"));
        assertNull(first.order());

        llm.callTool("place_order", Map.of("confirmed", true)).say("Order placed!");
        ChatResponse second = agent.chat(session, "Yes, place it");

        assertNotNull(second.order());
        assertEquals(110, second.order().get("itemTotal"));
        assertEquals(30, second.order().get("deliveryFee")); // under ₹199
        assertEquals(0, second.cart().get("itemTotal"));
    }

    @Test
    void unknownItemIdIsRejected() {
        llm.callTool("add_to_cart", Map.of("item_id", "i999")).say("That dish doesn't exist.");

        ChatResponse r = agent.chat(session, "add the special thali");

        assertTrue(r.trace().get(0).error());
        assertEquals(0, r.cart().get("itemTotal"));
    }

    @Test
    void cartHoldsOneRestaurantOnly() {
        llm.callTool("add_to_cart", Map.of("item_id", "i401"))
           .callTool("add_to_cart", Map.of("item_id", "i103")) // Butter Chicken, another restaurant
           .say("ok");

        ChatResponse r = agent.chat(session, "dosa and butter chicken");

        assertTrue(r.trace().get(1).error());
        assertEquals(110, r.cart().get("itemTotal"));
    }

    @Test
    void stepLimitStopsARunawayAgent() {
        llm.loopForever();

        ChatResponse r = agent.chat(session, "hello");

        assertEquals(6, r.llmCalls());
        assertTrue(r.reply().contains("couldn't finish"));
    }

    @Test
    void unknownToolDoesNotCrash() {
        llm.callTool("delete_database", Map.of()).say("I can't do that.");

        ChatResponse r = agent.chat(session, "do something weird");

        assertTrue(r.trace().get(0).error());
        assertEquals("I can't do that.", r.reply());
    }
}
