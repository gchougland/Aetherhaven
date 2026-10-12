package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.mastery.CoreVillagerMastery;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The Rancher's Mastery pace beside the other core producers, and how quickly barn animals warm to care. */
@Tag("town")
class RancherMasteryPaceTest {
    /**
     * What a Farmer earns in a game day on the starting farm: about fifty XP from crops alone, up to about a hundred and
     * sixty while Life Essence still fits. One XP for each item put away, as for the Miner and the Logger.
     */
    private static final int FARMER_DAY_LOW = 50, FARMER_DAY_HIGH = 160;
    private static final List<String> COMMON = List.of("Chicken", "Pig", "Rabbit", "Sheep", "Goat");

    /** One loaded game day of a single fed animal in the starting pen, counted the way the barn counts it. */
    private static long[] day(String species) {
        var stock = new PlotProductionState(); var herd = stock.herd();
        assertNull(herd.buy(species, 10, () -> true));
        UUID animal = herd.animals().getLast().id();
        assertTrue(herd.assign(0, animal, 1));
        assertTrue(herd.supply(BarnAnimalCatalog.get(species).food(), () -> true));
        assertTrue(herd.feedAnimal(animal, 0, 1));
        long items = 0, xp = 0;
        for (long minute = 0; minute <= HerdProductionState.DAY_MINUTES; minute++) {
            long before = stock.getAmounts().values().stream().mapToLong(Long::longValue).sum();
            herd.advance(minute, 1, Set.of(animal), stock, 1000, 1.0);
            long made = stock.getAmounts().values().stream().mapToLong(Long::longValue).sum() - before;
            items += made; xp += CoreVillagerMasteryService.ranchXp(made);
        }
        return new long[] {items, xp};
    }

    @Test void eachBarnItemIsWorthFiveXpAndOneUpdateStaysBounded() {
        assertEquals(5, CoreVillagerMasteryService.RANCH_XP_PER_ITEM);
        assertEquals(0, CoreVillagerMasteryService.ranchXp(0));
        assertEquals(0, CoreVillagerMasteryService.ranchXp(-3));
        assertEquals(5, CoreVillagerMasteryService.ranchXp(1));
        assertEquals(15, CoreVillagerMasteryService.ranchXp(3));
        assertEquals(500, CoreVillagerMasteryService.ranchXp(100));
        assertEquals(500, CoreVillagerMasteryService.ranchXp(1_000_000), "A burst of stored output cannot skip levels wholesale");
    }

    @Test void oneChickenMakesThirteenItemsADayWhichIsNowSixtyFiveXp() {
        long[] chicken = day("Chicken");
        assertEquals(13, chicken[0], "Four cycles of meat, hide and egg, and one manure");
        assertEquals(65, chicken[1]);
        var progress = new CoreVillagerMastery();
        progress.earnXp(chicken[1]);
        assertEquals(1, progress.level()); assertEquals(65, progress.xpInLevel());
        progress.earnXp(chicken[1]);
        assertEquals(2, progress.level(), "A second day of one chicken reaches Mastery 2");
    }

    @Test void aStartingBarnLevelsInTheSameBandAsAStartingFarm() {
        long items = 0, xp = 0;
        for (String species : COMMON) {
            assertEquals(BarnAnimalCatalog.Rarity.COMMON, BarnAnimalCatalog.get(species).rarity());
            long[] made = day(species); items += made[0]; xp += made[1];
        }
        double itemsPerDay = items / (double) COMMON.size(), xpPerDay = xp / (double) COMMON.size();
        assertTrue(itemsPerDay < FARMER_DAY_LOW / 2.0, "One XP an item left a pen far behind a farm: " + itemsPerDay);
        assertTrue(xpPerDay >= FARMER_DAY_LOW && xpPerDay <= FARMER_DAY_HIGH, "One pen: " + xpPerDay);
        assertTrue(2 * xpPerDay >= FARMER_DAY_LOW && 2 * xpPerDay <= FARMER_DAY_HIGH, "Two pens: " + 2 * xpPerDay);
        // Mastery 4 opens the Uncommon animals: 100 + 150 + 200 XP.
        double toFour = 450;
        assertTrue(toFour / itemsPerDay > 30, "It used to take more than a month of game days");
        assertTrue(toFour / xpPerDay < 10, "It now takes about a week");
    }

    @Test void pettingGivesTenAffectionSoFullHeartsTakeSeventeenDaysOfCare() {
        assertEquals(10, HerdProductionState.PET_POINTS);
        var herd = new HerdProductionState();
        assertNull(herd.buy("Rabbit", 1, () -> true));
        UUID rabbit = herd.animals().getLast().id();
        assertTrue(herd.assign(0, rabbit, 1));
        assertTrue(herd.pet(rabbit, 0, 1));
        assertEquals(10, herd.animal(rabbit).friendship());
        assertFalse(herd.pet(rabbit, 0, 1), "Still once a day");
        assertEquals(10, herd.animal(rabbit).friendship());
        String food = BarnAnimalCatalog.get("Rabbit").food();
        int day = 0;
        while (herd.animal(rabbit).hearts() < HerdProductionState.MAX_HEARTS) {
            day++;
            assertTrue(herd.supply(food, () -> true)); assertTrue(herd.feedAnimal(rabbit, day, 1));
            assertTrue(herd.pet(rabbit, day, 1));
            assertTrue(day < 100);
        }
        // Day 0 gave 10; each later day gives 20 for food and 10 for petting.
        assertEquals(17, day);
        assertEquals(HerdProductionState.MAX_HEARTS * HerdProductionState.POINTS_PER_HEART, herd.animal(rabbit).friendship());
    }
}
