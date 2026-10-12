package com.hexvane.aetherhaven.mastery;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.TownRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The core Builder's Mastery: what a block is worth, what a level does, and what an older save starts with. */
@Tag("town")
class BuilderMasteryTest {
    /**
     * Pieces of the two first buildings a town puts up (air left out). The House count is the one the game itself
     * reported in the server check; 20 of its pieces are furniture and the far halves of large blocks, which the
     * finishing pass sets, so the Builder places and is paid for 322.
     */
    private static final int HOUSE_BLOCKS = 342, HOUSE_BLOCKS_HE_PLACES = 322, BLACKSMITH_BLOCKS = 567;
    /** A game day is 48 real minutes. The House builds itself in half a day, the Blacksmith Shop in one. */
    private static final long DAY_MS = 2_880_000L;
    private static final long HOUSE_SLOT_MS = DAY_MS / 2 / HOUSE_BLOCKS, BLACKSMITH_SLOT_MS = DAY_MS / BLACKSMITH_BLOCKS;

    @Test void theBuilderHasMasteryLikeTheOtherCoreVillagers() {
        assertEquals("builder", BuilderMastery.KIND);
        assertTrue(CoreVillagerMasteryService.supports(BuilderMastery.KIND));
        assertNull(CoreVillagerMasteryService.toolFamily(BuilderMastery.KIND), "he has no tool to equip");
        var town = new TownRecord();
        assertEquals(1, CoreVillagerMasteryService.level(town, BuilderMastery.KIND));
        assertTrue(town.getCoreVillagerMastery().isEmpty(), "reading his level saves nothing");
    }

    @Test void heEarnsOneXpForEveryFiveBlocksAndTheRestIsKept() {
        assertEquals(5, BuilderMastery.BLOCKS_PER_XP);
        var town = new TownRecord();
        assertEquals(0, BuilderMastery.earn(town, 0));
        assertEquals(0, BuilderMastery.earn(town, -3));
        assertTrue(town.getCoreVillagerMastery().isEmpty(), "no work, no saved Mastery");
        // A level 1 step is one block: four steps pay nothing yet, the fifth pays the point.
        for (int step = 1; step <= 4; step++) assertEquals(0, BuilderMastery.earn(town, 1));
        var progress = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
        assertEquals(0, progress.xpInLevel());
        assertEquals(4, progress.workTowardXp());
        assertEquals(1, BuilderMastery.earn(town, 1));
        assertEquals(1, progress.xpInLevel());
        assertEquals(0, progress.workTowardXp());
        // Bigger steps: 7 blocks are 1 XP and 2 kept, then 3 more make the next point.
        assertEquals(1, BuilderMastery.earn(town, 7));
        assertEquals(2, progress.workTowardXp());
        assertEquals(1, BuilderMastery.earn(town, 3));
        assertEquals(3, progress.xpInLevel());
        // The kept blocks survive a save.
        BuilderMastery.earn(town, 4);
        var reloaded = new Gson().fromJson(new Gson().toJson(town), TownRecord.class);
        assertEquals(4, CoreVillagerMasteryService.progress(reloaded, BuilderMastery.KIND).workTowardXp());
        assertEquals(1, BuilderMastery.earn(reloaded, 1));
        assertEquals(4, CoreVillagerMasteryService.progress(reloaded, BuilderMastery.KIND).xpInLevel());
        // A whole House is 64 XP; a building of 500 to 600 blocks is 100 to 120, a day of the Farmer or the Miner.
        var fresh = new TownRecord();
        assertEquals(64, BuilderMastery.earn(fresh, HOUSE_BLOCKS_HE_PLACES));
        assertEquals(100, BuilderMastery.earn(new TownRecord(), 500));
        assertEquals(120, BuilderMastery.earn(new TownRecord(), 600));
        assertEquals(1, CoreVillagerMasteryService.level(fresh, BuilderMastery.KIND), "one House is not yet level 2");
    }

    @Test void levelsNeedTheSameXpAsEveryCoreVillager() {
        var town = new TownRecord();
        var progress = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
        long blocks = 0;
        long[] blocksAtLevel = new long[CoreVillagerMastery.MAX_LEVEL + 1];
        for (int level = 1; level < CoreVillagerMastery.MAX_LEVEL; level++) {
            assertEquals(level, progress.level());
            assertEquals(100 + 50L * (level - 1), progress.xpToNextLevel());
            long needed = progress.xpToNextLevel() * BuilderMastery.BLOCKS_PER_XP;
            assertEquals(progress.xpToNextLevel() - 1, BuilderMastery.earn(town, (int) needed - 1));
            assertEquals(level, progress.level(), "one block short of the next level");
            assertEquals(1, BuilderMastery.earn(town, 1));
            blocks += needed;
            blocksAtLevel[level + 1] = blocks;
        }
        assertEquals(10, progress.level());
        assertEquals(500, blocksAtLevel[2]);
        assertEquals(2_250, blocksAtLevel[4], "four blocks a step, the pace he had before Mastery");
        assertEquals(3_500, blocksAtLevel[5]);
        assertEquals(13_500, blocksAtLevel[10], "2,700 XP");
        // A master earns nothing more and keeps no loose blocks.
        assertEquals(0, BuilderMastery.earn(town, 1_000));
        assertEquals(0, progress.workTowardXp());
        assertEquals(0, progress.xpInLevel());
        // A catalyst level keeps earned XP and kept blocks.
        var trained = new TownRecord();
        BuilderMastery.earn(trained, 53);
        var other = CoreVillagerMasteryService.progress(trained, BuilderMastery.KIND);
        assertTrue(other.train());
        assertEquals(2, other.level());
        assertEquals(10, other.xpInLevel());
        assertEquals(3, other.workTowardXp());
    }

    @Test void blocksPlacedAtOnceAreHisLevelFromOneToTen() {
        for (int level = 1; level <= 10; level++) assertEquals(level, BuilderMastery.blocksPerStep(level));
        assertEquals(1, BuilderMastery.blocksPerStep(0));
        assertEquals(1, BuilderMastery.blocksPerStep(-4));
        assertEquals(10, BuilderMastery.blocksPerStep(11));
        var town = new TownRecord();
        assertEquals(1, BuilderMastery.blocksPerStep(town));
        var progress = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
        for (int level = 2; level <= 10; level++) {
            assertTrue(progress.train());
            assertEquals(level, BuilderMastery.blocksPerStep(town));
        }
        // Another villager's Mastery is not his.
        var farmers = new TownRecord();
        while (CoreVillagerMasteryService.progress(farmers, "farmer").train()) { /* to the top */ }
        assertEquals(1, BuilderMastery.blocksPerStep(farmers));
    }

    @Test void theTimeBetweenStepsDoesNotChangeWithLevelOnlyTheStepsNeeded() {
        assertEquals(4, BuilderMastery.STEP_DIVISOR, "as before Mastery");
        assertEquals(1_052, BuilderMastery.stepMs(HOUSE_SLOT_MS));
        assertEquals(1_269, BuilderMastery.stepMs(BLACKSMITH_SLOT_MS));
        assertEquals(1, BuilderMastery.stepMs(2), "never zero");
        // House: 342 steps at level 1, 86 at level 4 (what he did before), 69 at level 5, 35 at level 10.
        assertEquals(342, BuilderMastery.steps(HOUSE_BLOCKS, 1));
        assertEquals(86, BuilderMastery.steps(HOUSE_BLOCKS, 4));
        assertEquals(69, BuilderMastery.steps(HOUSE_BLOCKS, 5));
        assertEquals(35, BuilderMastery.steps(HOUSE_BLOCKS, 10));
        assertEquals(0, BuilderMastery.steps(0, 3));
        // Real seconds he stands at the site. Before Mastery: 90 for the House, 180 for the Blacksmith Shop.
        assertEquals(359, BuilderMastery.buildMs(HOUSE_BLOCKS, HOUSE_SLOT_MS, 1) / 1000);
        assertEquals(90, BuilderMastery.buildMs(HOUSE_BLOCKS, HOUSE_SLOT_MS, 4) / 1000);
        assertEquals(72, BuilderMastery.buildMs(HOUSE_BLOCKS, HOUSE_SLOT_MS, 5) / 1000);
        assertEquals(36, BuilderMastery.buildMs(HOUSE_BLOCKS, HOUSE_SLOT_MS, 10) / 1000);
        assertEquals(719, BuilderMastery.buildMs(BLACKSMITH_BLOCKS, BLACKSMITH_SLOT_MS, 1) / 1000);
        assertEquals(180, BuilderMastery.buildMs(BLACKSMITH_BLOCKS, BLACKSMITH_SLOT_MS, 4) / 1000);
        assertEquals(144, BuilderMastery.buildMs(BLACKSMITH_BLOCKS, BLACKSMITH_SLOT_MS, 5) / 1000);
        assertEquals(72, BuilderMastery.buildMs(BLACKSMITH_BLOCKS, BLACKSMITH_SLOT_MS, 10) / 1000);
    }

    @Test void anExistingSaveStartsTheBuilderAtLevelOneAndKeepsWhatWasSaved() {
        var gson = new Gson();
        // A town saved before any villager had Mastery.
        var oldest = gson.fromJson("{}", TownRecord.class);
        assertEquals(1, CoreVillagerMasteryService.level(oldest, BuilderMastery.KIND));
        assertEquals(1, BuilderMastery.blocksPerStep(oldest));
        // A 4.0 town whose other villagers have Mastery and whose Builder has none saved.
        var others = gson.fromJson(
            "{\"coreVillagerMastery\":{\"farmer\":{\"level\":6,\"xp\":40},\"miner\":{\"level\":3,\"xp\":0}}}", TownRecord.class);
        assertEquals(6, CoreVillagerMasteryService.level(others, "farmer"));
        assertEquals(1, CoreVillagerMasteryService.level(others, BuilderMastery.KIND));
        var builder = CoreVillagerMasteryService.progress(others, BuilderMastery.KIND);
        assertEquals(1, builder.level());
        assertEquals(0, builder.xpInLevel());
        assertEquals(0, builder.workTowardXp());
        assertEquals(40, CoreVillagerMasteryService.progress(others, "farmer").xpInLevel(), "nobody else is touched");
        // Mastery already saved for him is kept as it is, with or without the kept blocks field.
        var saved = gson.fromJson("{\"coreVillagerMastery\":{\"builder\":{\"level\":4,\"xp\":30}}}", TownRecord.class);
        assertEquals(4, CoreVillagerMasteryService.level(saved, BuilderMastery.KIND));
        assertEquals(30, CoreVillagerMasteryService.progress(saved, BuilderMastery.KIND).xpInLevel());
        assertEquals(0, CoreVillagerMasteryService.progress(saved, BuilderMastery.KIND).workTowardXp());
        assertEquals(4, BuilderMastery.blocksPerStep(saved));
        var round = gson.fromJson(gson.toJson(saved), TownRecord.class);
        assertEquals(4, CoreVillagerMasteryService.level(round, BuilderMastery.KIND));
        assertEquals(30, CoreVillagerMasteryService.progress(round, BuilderMastery.KIND).xpInLevel());
        // An older save of another villager has no kept blocks field either and reads as none.
        assertEquals(0, CoreVillagerMasteryService.progress(others, "miner").workTowardXp());
    }
}
