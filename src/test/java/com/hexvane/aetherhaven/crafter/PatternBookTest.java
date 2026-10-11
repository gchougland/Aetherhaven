package com.hexvane.aetherhaven.crafter;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.crafter.WorkshopParts.Kind;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class PatternBookTest {
    private static final Map<String, Integer> PAID = Map.of(Kind.WOOD.item(), 20);

    @Test void propsArePricedFromTheirGoldValueInStepsOfFive() {
        assertEquals(5, WorkshopParts.total(0));
        assertEquals(10, WorkshopParts.total(15));
        assertEquals(20, WorkshopParts.total(40));
        assertEquals(40, WorkshopParts.total(80));
        for (long price : List.of(1L, 15L, 22L, 33L, 47L, 80L, 250L)) {
            for (var weights : List.<Map<Kind, Integer>>of(Map.of(), Map.of(Kind.WOOD, 3), Map.of(Kind.WOOD, 6, Kind.METAL, 3),
                    Map.of(Kind.WOOD, 1, Kind.STONE, 1, Kind.METAL, 1, Kind.CLOTH, 1), Map.of(Kind.CLOTH, 9, Kind.WOOD, 1))) {
                var cost = WorkshopParts.cost(price, weights);
                assertEquals(WorkshopParts.total(price), cost.values().stream().mapToInt(Integer::intValue).sum(), price + " " + weights);
                assertTrue(cost.values().stream().allMatch(count -> count >= 5 && count % 5 == 0), price + " " + cost);
            }
        }
    }

    @Test void theSplitFollowsWhatAPropIsMadeOf() {
        assertEquals(Map.of(Kind.WOOD, 20), WorkshopParts.cost(40, Map.of()), "Unreadable props are plain woodwork");
        assertEquals(Map.of(Kind.WOOD, 20), WorkshopParts.cost(40, Map.of(Kind.WOOD, 3)));
        assertEquals(Map.of(Kind.WOOD, 15, Kind.METAL, 5), WorkshopParts.cost(40, Map.of(Kind.WOOD, 6, Kind.METAL, 2)));
        assertEquals(Map.of(Kind.WOOD, 20), WorkshopParts.cost(40, Map.of(Kind.WOOD, 19, Kind.METAL, 1)), "One nail does not make a metal job");
        assertEquals(Map.of(Kind.WOOD, 5), WorkshopParts.cost(8, Map.of(Kind.WOOD, 1, Kind.STONE, 1)), "A tiny prop needs one kind of part");
        var mixed = new HashSet<String>(List.of("Wood_Hardwood_Planks_Half", "Furniture_Village_Lantern", "Rock_Stone_Brick", "Deco_Rope", "Empty"));
        assertEquals(Map.of(Kind.WOOD, 1, Kind.METAL, 1, Kind.STONE, 1, Kind.CLOTH, 1), WorkshopParts.weights(mixed));
        assertEquals(Kind.WOOD, WorkshopParts.classify("Furniture_Village_Crate"));
        assertNull(WorkshopParts.classify("Empty")); assertNull(WorkshopParts.classify(null));
        assertEquals(Kind.METAL, Kind.ofItem("Ingredient_Scraps")); assertEquals(Kind.WOOD, Kind.ofItem("Ingredient_Stick")); assertNull(Kind.ofItem("Ingredient_Fibre"));
    }

    @Test void salvageReturnsAShareOfEveryPartAndNeverNothing() {
        var cost = WorkshopParts.cost(40, Map.of(Kind.WOOD, 6, Kind.METAL, 2));
        assertEquals(Map.of(Kind.WOOD, 7, Kind.METAL, 2), WorkshopParts.salvage(cost, 50));
        assertEquals(Map.of(Kind.WOOD, 11, Kind.METAL, 3), WorkshopParts.salvage(cost, 75));
        assertEquals(Map.of(Kind.WOOD, 1), WorkshopParts.salvage(Map.of(Kind.WOOD, 5), 0));
        for (int percent : List.of(50, 75)) for (var part : cost.entrySet())
            assertTrue(WorkshopParts.salvage(cost, percent).get(part.getKey()) < part.getValue(), "Salvage never pays for a new copy");
    }

    @Test void aPatternMustBeStudiedOnceBeforeItCanBeCommissioned() {
        var book = new PatternBook();
        assertNotNull(book.commissionIssue("fish_barrel")); assertFalse(book.commission("fish_barrel", PAID));
        assertTrue(book.learn("fish_barrel")); assertFalse(book.learn("fish_barrel")); assertFalse(book.learn(" "));
        assertNull(book.commissionIssue("fish_barrel")); assertTrue(book.commission("fish_barrel", PAID));
        assertTrue(book.commission("fish_barrel", PAID));
        assertNotNull(book.commissionIssue("fish_barrel"), "Two commissions fill a new bench");
        assertFalse(book.commission("fish_barrel", PAID)); assertEquals(2, book.queue().size());
        assertThrows(UnsupportedOperationException.class, () -> book.queue().clear());
    }

    @Test void benchTimeBuildsOneCommissionAtATimeAndCarriesOver() {
        var book = new PatternBook(); book.learn("a"); book.learn("b");
        book.commission("a", PAID); book.commission("b", PAID);
        assertEquals(0, book.work(30, prop -> 100)); assertEquals(0.3, book.progress(prop -> 100), 1e-9);
        assertEquals(0, book.work(Double.NaN, prop -> 100)); assertEquals(0, book.work(-5, prop -> 100));
        assertEquals(1, book.work(80, prop -> 100));
        assertEquals(Map.of("a", 1), book.shelf()); assertEquals("b", book.queue().getFirst().prop());
        assertEquals(10, book.queue().getFirst().worked(), 1e-9, "Spare time moves on to the next prop");
        var saved = new Gson().fromJson(new Gson().toJson(book), PatternBook.class);
        assertEquals(10, saved.queue().getFirst().worked(), 1e-9); assertEquals(PAID, saved.queue().getFirst().paid());
        assertEquals(1, saved.work(90, prop -> 100)); assertTrue(saved.queue().isEmpty());
        assertEquals(Map.of("a", 1, "b", 1), saved.shelf()); assertEquals(0, saved.work(500, prop -> 100));
        assertTrue(saved.take("a")); assertFalse(saved.take("a")); assertEquals(1, saved.shelved());
    }

    @Test void cancellingReturnsExactlyWhatWasPaid() {
        var book = new PatternBook(); book.learn("a");
        var paid = Map.of(Kind.WOOD.item(), 15, Kind.METAL.item(), 5);
        book.commission("a", paid); book.work(20, prop -> 100);
        assertNull(book.cancel(1)); assertNull(book.cancel(-1));
        assertEquals(paid, book.cancel(0)); assertTrue(book.queue().isEmpty()); assertTrue(book.shelf().isEmpty());
    }

    @Test void collectingPatternsUnlocksEachPerkInTurn() {
        var book = new PatternBook();
        assertEquals(2, book.queueLimit()); assertEquals(50, book.salvagePercent()); assertEquals(1.0, book.speed());
        assertEquals(PatternBook.Milestone.APPRENTICE_SHELF, book.next());
        for (int i = 0; i < 5; i++) book.learn("p" + i);
        assertEquals(3, book.queueLimit()); assertEquals(50, book.salvagePercent());
        for (int i = 5; i < 10; i++) book.learn("p" + i);
        assertEquals(75, book.salvagePercent()); assertEquals(PatternBook.Milestone.MASTER_BENCH, book.next());
        for (int i = 10; i < 20; i++) book.learn("p" + i);
        assertEquals(4, book.queueLimit()); assertEquals(1.0, book.speed());
        for (int i = 20; i < 30; i++) book.learn("p" + i);
        assertNull(book.next());
        book.commission("p0", PAID);
        assertEquals(1, book.work(75, prop -> 100), "A full book builds a quarter faster");
    }

    @Test void aFullShelfStopsNewCommissionsAndOldSavesOpenEmpty() {
        var book = new PatternBook(); book.learn("a");
        for (int i = 0; i < PatternBook.SHELF_LIMIT; i++) { assertTrue(book.commission("a", PAID)); book.work(100, prop -> 100); }
        assertEquals(PatternBook.SHELF_LIMIT, book.shelved());
        assertNotNull(book.commissionIssue("a")); assertTrue(book.take("a")); assertNull(book.commissionIssue("a"));
        var empty = new Gson().fromJson("{}", PatternBook.class);
        assertTrue(empty.known().isEmpty()); assertTrue(empty.queue().isEmpty()); assertTrue(empty.shelf().isEmpty());
        assertEquals(0, empty.work(10, prop -> 100)); assertFalse(empty.take("a")); assertNull(empty.cancel(0));
        var blank = new Gson().fromJson("{\"known\":null,\"queue\":null,\"shelf\":null}", PatternBook.class);
        assertTrue(blank.learn("a")); assertTrue(blank.commission("a", PAID)); assertEquals(1, blank.work(100, prop -> 100));
    }
}
