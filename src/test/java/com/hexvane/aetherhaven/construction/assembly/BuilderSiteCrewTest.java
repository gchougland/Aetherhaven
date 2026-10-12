package com.hexvane.aetherhaven.construction.assembly;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.mastery.BuilderMastery;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkMastery;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hexvane.aetherhaven.workplace.BuildingOrders;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The core Builder and a yard's townsfolk builders on one site: how many blocks a step places, who is paid for which
 * block, and that the site's saved progress counts every block once. Saved state and the real frontier only; the
 * world write is replaced by {@link Site#place}, and the server check places real blocks.
 */
@Tag("construction")
class BuilderSiteCrewTest {
    private static final String WORLD = "builder-crew-test";
    private static final String ANNA = "anna", BORIS = "boris";

    /** A started project of {@code 5 x 3 x 4 = 60} blocks, with the frontier the game uses. */
    private static final class Site {
        final List<PendingBlock> pending = new ArrayList<>();
        final PlotInstance plot = new PlotInstance(
            UUID.randomUUID(), "plot_house", PlotInstanceState.ASSEMBLING, new PlotFootprintRecord(0, 0, 0, 8, 8, 8), 0, 0, 0, 0);
        final PlotAssemblyFrontierRuntime frontier;
        final Set<Integer> written = new HashSet<>();
        int doubles;

        Site() {
            for (int y = 0; y < 3; y++) for (int x = 0; x < 5; x++) for (int z = 0; z < 4; z++)
                pending.add(new PendingBlock(x, y, z, 1, null, 0, 0, 0, 0, 0));
            frontier = PlotAssemblyFrontierRuntime.create(pending, plot, null);
        }
        int size() { return pending.size(); }
        int placed() { return plot.getAssemblyPlacedBlockCount(); }
        boolean done() { return placed() >= size(); }
        /** What advancePlacementAtIndex does to saved progress once the block is in the world. */
        boolean place(int index) {
            if (index < 0 || !frontier.frontierContains(index)) return false;
            if (!written.add(index)) doubles++;
            plot.addAssemblyPlacedIndex(index);
            frontier.onBlockPlaced(index, pending, plot);
            return true;
        }
    }

    /**
     * One due passive step, as {@link PlotAssemblyService#tickPassive} runs it: the block count comes from the same
     * method, and the Builder is paid only when he is at the site.
     */
    private static int step(Site site, TownRecord town, UUID plot, long now) {
        boolean builderAtSite = AssemblyPassiveBoostRegistry.boostFor(WORLD, plot, now) > 1;
        int max = PlotAssemblyService.passiveBlocksPerStep(town, builderAtSite, 1);
        int placed = 0;
        while (placed < max && !site.done() && site.place(site.frontier.smallestPlacementIndex())) placed++;
        if (builderAtSite) BuilderMastery.earn(town, placed);
        return placed;
    }

    /** A townsfolk builder finishes one piece: effort on a frontier cell, then the placement the yard credits once. */
    private static boolean townsfolkPlaces(Site site, BuildingOrders yard, String worker, TownsfolkProgress progress, long now) {
        var it = site.frontier.frontierIterator();
        while (it.hasNext()) {
            int index = it.nextInt();
            var block = site.pending.get(index);
            var cell = new BuildingOrders.Cell(yard.list().getFirst().id(), index, block.x(), block.y(), block.z(), "Rock_Stone", 0);
            if (!yard.focus(worker, cell, now)) continue;
            double duration = BuildingOrders.duration(1, 1);
            for (double done = 0; done < duration; done += .5) yard.attend(worker, cell, .5, true, 1, now);
            boolean committed = yard.ready(worker, cell, duration) && site.place(index) && yard.committed(worker, cell);
            TownsfolkMastery.completed(progress, TownsfolkMastery.Work.BUILDING_BLOCK, committed);
            return committed;
        }
        return false;
    }

    private static TownRecord townWithBuilderAt(int level) {
        var town = new TownRecord();
        var progress = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
        while (progress.level() < level) assertTrue(progress.train());
        return town;
    }

    private static long builderXp(TownRecord town) {
        var progress = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
        long total = progress.xpInLevel();
        for (int level = 1; level < progress.level(); level++) total += 100 + 50L * (level - 1);
        return total;
    }

    @Test void aStepPlacesAsManyBlocksAsTheBuildersLevel() {
        int[][] expected = {{1, 60}, {4, 15}, {5, 12}, {10, 6}};
        for (int[] row : expected) {
            var site = new Site();
            var town = townWithBuilderAt(row[0]);
            long before = builderXp(town);
            UUID plot = site.plot.getPlotId();
            int steps = 0;
            long now = 1_000;
            while (!site.done()) {
                AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, BuilderMastery.STEP_DIVISOR, now);
                int placed = step(site, town, plot, now);
                assertEquals(Math.min(row[0], site.size() - (site.placed() - placed)), placed, "level " + row[0]);
                steps++;
                now += 1_000;
            }
            assertEquals(row[1], steps, "steps at level " + row[0]);
            assertEquals(60, site.placed());
            assertEquals(0, site.doubles);
            // 60 blocks are 12 XP at any level, unless the top level stops the count.
            assertEquals(row[0] == 10 ? 0 : 12, builderXp(town) - before, "XP at level " + row[0]);
            AssemblyPassiveBoostRegistry.clearForWorld(WORLD);
        }
    }

    @Test void aLevelGainedDuringABuildCountsFromTheNextStep() {
        var site = new Site();
        var town = new TownRecord();
        var progress = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
        assertEquals(99, progress.earnXp(99));
        UUID plot = site.plot.getPlotId();
        List<Integer> sizes = new ArrayList<>();
        for (long now = 1_000; sizes.size() < 7; now += 1_000) {
            AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, BuilderMastery.STEP_DIVISOR, now);
            sizes.add(step(site, town, plot, now));
        }
        // Five single blocks make the last XP of level 1; from then on two at a time.
        assertEquals(List.of(1, 1, 1, 1, 1, 2, 2), sizes);
        assertEquals(2, progress.level());
        AssemblyPassiveBoostRegistry.clearForWorld(WORLD);
    }

    @Test void withoutTheBuilderTheSiteKeepsOneBlockAStepAndHeEarnsNothing() {
        var site = new Site();
        var town = townWithBuilderAt(7);
        long before = builderXp(town);
        UUID plot = site.plot.getPlotId();
        // He never came.
        for (long now = 1_000; now <= 10_000; now += 1_000) assertEquals(1, step(site, town, plot, now));
        assertEquals(10, site.placed());
        assertEquals(before, builderXp(town));
        // He works for two steps, then leaves without saying so (his chunk unloaded): his entry runs out.
        AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, BuilderMastery.STEP_DIVISOR, 20_000);
        assertEquals(7, step(site, town, plot, 20_000));
        assertEquals(7, step(site, town, plot, 20_000 + AssemblyPassiveBoostRegistry.STALE_MS));
        long earned = builderXp(town) - before;
        assertEquals(2, earned, "14 blocks");
        assertEquals(1, step(site, town, plot, 20_001 + AssemblyPassiveBoostRegistry.STALE_MS));
        assertEquals(1, step(site, town, plot, 60_000));
        assertEquals(earned, builderXp(town) - before, "blocks the site placed by itself are not his");
        AssemblyPassiveBoostRegistry.clearForWorld(WORLD);
    }

    @Test void theBuilderAndTownsfolkBuildersOnOneSiteAddUpAndEachIsPaidForTheirOwnBlocks() {
        var site = new Site();
        var town = townWithBuilderAt(3);
        long before = builderXp(town);
        UUID plot = site.plot.getPlotId();
        var yard = new BuildingOrders();
        assertNull(yard.add(plot, UUID.randomUUID()));
        var anna = new TownsfolkProgress();
        var boris = new TownsfolkProgress();
        int rounds = 0, byBuilder = 0, byTownsfolk = 0;
        long now = 1_000;
        while (!site.done()) {
            AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, BuilderMastery.STEP_DIVISOR, now);
            byBuilder += step(site, town, plot, now);
            if (!site.done() && townsfolkPlaces(site, yard, ANNA, anna, now)) byTownsfolk++;
            if (!site.done() && townsfolkPlaces(site, yard, BORIS, boris, now)) byTownsfolk++;
            rounds++;
            now += 1_000;
        }
        // Three from the Builder and one from each townsfolk every round: 60 blocks in 12 rounds, not 20 or 30.
        assertEquals(12, rounds);
        assertEquals(36, byBuilder);
        assertEquals(24, byTownsfolk);
        assertEquals(60, site.placed(), "every block counted once in the saved progress");
        assertEquals(60, site.written.size());
        assertEquals(0, site.doubles, "no block placed twice");
        // The yard and the townsfolk are credited for their 24, the Builder for his 36 (7 XP and one block kept).
        assertEquals(24, yard.contributions());
        assertEquals(12L * TownsfolkMastery.Work.BUILDING_BLOCK.xp, anna.masteryXp());
        assertEquals(12L * TownsfolkMastery.Work.BUILDING_BLOCK.xp, boris.masteryXp());
        assertEquals(7, builderXp(town) - before);
        assertEquals(1, CoreVillagerMasteryService.progress(town, BuilderMastery.KIND).workTowardXp());
        AssemblyPassiveBoostRegistry.clearForWorld(WORLD);
    }

    @Test void twoEntriesForOneSiteDoNotStackAndTheEntryIsRenewedOrRunsOut() {
        UUID plot = UUID.randomUUID(), other = UUID.randomUUID();
        assertEquals(1, AssemblyPassiveBoostRegistry.boostFor(WORLD, plot, 0));
        assertFalse(AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, 4, 1_000), "he has just arrived");
        assertTrue(AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, 4, 1_010), "a renewal");
        assertEquals(4, AssemblyPassiveBoostRegistry.boostFor(WORLD, plot, 1_020), "a second Builder does not double the pace");
        assertEquals(1, AssemblyPassiveBoostRegistry.boostFor(WORLD, other, 1_020), "only the site he stands at");
        // Renewed each tick it lasts as long as he stays.
        for (long now = 1_050; now <= 20_000; now += 50) assertTrue(AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, 4, now));
        assertEquals(4, AssemblyPassiveBoostRegistry.boostFor(WORLD, plot, 20_000 + AssemblyPassiveBoostRegistry.STALE_MS));
        assertEquals(1, AssemblyPassiveBoostRegistry.boostFor(WORLD, plot, 20_001 + AssemblyPassiveBoostRegistry.STALE_MS));
        // Back after being kept away too long: that counts as arriving again, so the caller hurries the next step.
        assertFalse(AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, 4, 30_000));
        assertTrue(AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, 4, 30_000 + AssemblyPassiveBoostRegistry.STALE_MS));
        // He says he is leaving: gone at once.
        assertFalse(AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, 0, 33_001));
        assertEquals(1, AssemblyPassiveBoostRegistry.boostFor(WORLD, plot, 33_002));
        assertFalse(AssemblyPassiveBoostRegistry.setBoost(WORLD, plot, 4, 33_003));
        AssemblyPassiveBoostRegistry.clearForWorld(WORLD);
    }
}
