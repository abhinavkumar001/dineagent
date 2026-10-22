package com.dineagent.catalog;

import com.dineagent.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
class MenuSearchServiceTest {

    @Autowired private MenuSearchService search;

    private List<MenuSearchService.Result> find(String q, Boolean veg, Integer maxPrice, Integer maxMinutes, Integer minSpice) {
        return search.search(new MenuSearchService.Filters(q, veg, maxPrice, maxMinutes, minSpice, null, null, 10));
    }

    @Test
    void filtersAreAppliedExactly() {
        List<MenuSearchService.Result> r = find("", true, 300, null, 2);
        assertFalse(r.isEmpty());
        r.forEach(x -> {
            assertTrue(x.item().veg());
            assertTrue(x.item().price() <= 300);
            assertTrue(x.item().spiceLevel() >= 2);
        });
    }

    @Test
    void keywordQueryRanksMatchingDishesFirst() {
        List<MenuSearchService.Result> r = find("paneer", null, null, null, null);
        assertTrue(r.get(0).item().name().toLowerCase().contains("paneer"));
    }

    @Test
    void deliveryTimeFilterUsesTheRestaurant() {
        find("biryani", null, null, 30, null).forEach(x -> assertTrue(x.restaurant().deliveryMinutes() <= 30));
    }

    @Test
    void impossibleFiltersReturnNothing() {
        assertTrue(find("biryani", null, 50, null, null).isEmpty());
    }
}
