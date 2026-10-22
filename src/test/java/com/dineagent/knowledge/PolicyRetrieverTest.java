package com.dineagent.knowledge;

import com.dineagent.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
class PolicyRetrieverTest {

    @Autowired private PolicyRetriever retriever;

    @Test
    void findsTheRightPolicy() {
        assertEquals("delivery-fee", retriever.retrieve("Is there a delivery fee on my order?", 3).get(0).chunk().id());
        assertEquals("refund", retriever.retrieve("how many days for a refund", 3).get(0).chunk().id());
        assertEquals("cancel", retriever.retrieve("can I cancel my order", 3).get(0).chunk().id());
    }

    @Test
    void unrelatedQuestionsFindNothing() {
        assertTrue(retriever.retrieve("who won the cricket match", 3).isEmpty());
    }
}
