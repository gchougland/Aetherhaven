package com.hexvane.aetherhaven.construction.assembly;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.mastery.BuilderMastery;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.shape.Box;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A site with someone standing in it: which pieces wait, in what order the others go, what the Builder's step still
 * places and is paid for, how long the site waits before a villager is moved, and that a player is never moved. The
 * real frontier, the real saved progress and the real overlap test; the world write is {@link Site#place}.
 */
@Tag("construction")
class SiteDeferralTest {
    private static final Box VILLAGER = new Box(-0.325, 0, -0.325, 0.325, 1.85, 0.325);
    private static final Box CUBE = Box.UNIT;

    /** A started project of {@code 5 x 3 x 4 = 60} solid blocks at the world's origin, lowest layer at y 64. */
    private static final class Site {
        final List<PendingBlock> pending = new ArrayList<>();
        final PlotInstance plot = new PlotInstance(
            UUID.randomUUID(), "plot_house", PlotInstanceState.ASSEMBLING, new PlotFootprintRecord(0, 0, 0, 8, 80, 8), 0, 64, 0, 0);
        final PlotAssemblyFrontierRuntime frontier;
        final List<SiteBodies.Body> bodies = new ArrayList<>();
        final List<Integer> order = new ArrayList<>();
        final Set<Integer> written = new HashSet<>();
        int doubles, intoBodies, moved;

        Site() {
            for (int y = 0; y < 3; y++) for (int x = 0; x < 5; x++) for (int z = 0; z < 4; z++)
                pending.add(new PendingBlock(x, 64 + y, z, 1, null, 0, 0, 0, 0, 0));
            frontier = PlotAssemblyFrontierRuntime.create(pending, plot, null);
        }
        int index(int x, int y, int z) {
            for (int i = 0; i < pending.size(); i++) if (pending.get(i).x() == x && pending.get(i).y() == y && pending.get(i).z() == z) return i;
            throw new IllegalArgumentException();
        }
        boolean done() { return plot.getAssemblyPlacedBlockCount() >= pending.size(); }
        /** What advancePlacementAtIndex does: refuse a piece with a body in it, else write it and record it. */
        PlotAssemblyService.PlacementAdvanceOutcome place(int index) {
            var piece = pending.get(index);
            var blocker = SiteBodies.blocker(bodies, piece.x(), piece.y(), piece.z(), CUBE);
            if (blocker != null) return PlotAssemblyService.PlacementAdvanceOutcome.waitingFor(blocker);
            if (!written.add(index)) doubles++;
            for (var body : bodies) if (body.overlapsBlock(piece.x(), piece.y(), piece.z(), CUBE)) intoBodies++;
            order.add(index);
            plot.addAssemblyPlacedIndex(index);
            frontier.onBlockPlaced(index, pending, plot);
            return PlotAssemblyService.PlacementAdvanceOutcome.committed();
        }
    }

    /**
     * One due step, as {@link PlotAssemblyService#tickPassive} runs it since pieces can wait: same block count, same
     * choice of the next piece, same wait, same pay.
     *
     * @return the pieces placed
     */
    private static int step(Site site, TownRecord town, boolean builderAtSite, long now) {
        int max = PlotAssemblyService.passiveBlocksPerStep(town, builderAtSite, 1);
        int placed = 0;
        IntOpenHashSet waiting = null;
        List<SiteBodies.Body> inTheWay = new ArrayList<>();
        while (placed < max && !site.done()) {
            int pick = site.frontier.smallestPlacementIndex(waiting);
            if (pick < 0) break;
            var outcome = site.place(pick);
            if (outcome.bodyInTheWay()) {
                if (waiting == null) waiting = new IntOpenHashSet();
                waiting.add(pick);
                if (!inTheWay.contains(outcome.blocker())) inTheWay.add(outcome.blocker());
                continue;
            }
            placed++;
        }
        if (builderAtSite) BuilderMastery.earn(town, placed);
        UUID plot = site.plot.getPlotId();
        if (placed > 0 || inTheWay.isEmpty()) {
            SiteRefuge.notWaiting(plot);
        } else if (SiteRefuge.waitedOut(plot, now)) {
            // Villagers are moved; a player is not.
            for (var body : inTheWay) if (!body.player() && site.bodies.remove(body)) site.moved++;
        }
        return placed;
    }

    private static TownRecord townWithBuilderAt(int level) {
        var town = new TownRecord();
        var progress = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
        while (progress.level() < level) assertTrue(progress.train());
        return town;
    }

    private static long paidBlocks(TownRecord town) {
        var progress = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
        return progress.xpInLevel() * BuilderMastery.BLOCKS_PER_XP + progress.workTowardXp();
    }

    @Test void aPieceWithSomeoneInItWaitsAndTheNextPiecesGoInTheirUsualOrder() {
        var site = new Site();
        var town = townWithBuilderAt(1);
        // A villager on the ground in the very first piece: index 0, and the piece above it at index 20.
        site.bodies.add(SiteBodies.Body.at(null, false, new Vector3d(0.5, 64, 0.5), VILLAGER));
        int first = site.index(0, 64, 0), above = site.index(0, 65, 0);
        assertEquals(0, first);
        for (int i = 0; i < 10; i++) assertEquals(1, step(site, town, true, 1_000 + i));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), site.order, "the smallest index that is free, every step");
        assertTrue(site.frontier.frontierContains(first), "the waiting piece stays on the frontier");
        // The rest of the site is built around them.
        long now = 2_000;
        while (site.plot.getAssemblyPlacedBlockCount() < 58) assertEquals(1, step(site, town, true, now++));
        assertFalse(site.written.contains(first));
        assertFalse(site.written.contains(above));
        assertTrue(site.written.contains(site.index(0, 66, 0)), "the piece above their head is not in their way");
        assertEquals(0, site.intoBodies);
        assertEquals(0, site.doubles);
    }

    @Test void theBuildersStepStillPlacesHisWholeCountAndHeIsPaidForExactlyWhatWentIn() {
        for (int level : new int[] {1, 5, 10}) {
            var site = new Site();
            var town = townWithBuilderAt(level);
            long paidBefore = paidBlocks(town);
            // Three villagers: in the first piece, on a piece of the second layer once it stands, and mid floor.
            site.bodies.add(SiteBodies.Body.at(null, false, new Vector3d(0.5, 64, 0.5), VILLAGER));
            site.bodies.add(SiteBodies.Body.at(null, false, new Vector3d(2.5, 64, 1.5), VILLAGER));
            site.bodies.add(SiteBodies.Body.at(null, false, new Vector3d(4.5, 64, 3.5), VILLAGER));
            int waitingPieces = 6; // each stands in two pieces
            List<Integer> sizes = new ArrayList<>();
            long now = 10_000;
            while (site.plot.getAssemblyPlacedBlockCount() < 60 - waitingPieces) {
                int placed = step(site, town, true, now);
                sizes.add(placed);
                now += 100;
            }
            // Every step but the last is full, however many pieces had to be passed over to fill it.
            for (int i = 0; i < sizes.size() - 1; i++) assertEquals(level, sizes.get(i), "level " + level + ", step " + i);
            assertEquals((54 + level - 1) / level, sizes.size(), "steps at level " + level);
            assertEquals(54, site.written.size());
            assertEquals(0, site.intoBodies, "no block went into a body");
            // Paid for the 54 that went in, not for the six that are waiting, and not twice for anything.
            assertEquals(level == 10 ? 0 : 54, paidBlocks(town) - paidBefore, "level " + level);
            // Now nothing can be placed. The site waits, moves the villagers after the wait, and finishes.
            assertEquals(0, step(site, town, true, now));
            assertEquals(0, site.moved, "not at once");
            assertEquals(0, step(site, town, true, now + SiteRefuge.WAIT_MS - 1));
            assertEquals(0, site.moved, "not before the wait is over");
            assertEquals(0, step(site, town, true, now + SiteRefuge.WAIT_MS));
            assertEquals(3, site.moved, "all three, once the wait is over");
            int rest = 0;
            long later = now + SiteRefuge.WAIT_MS + 100;
            while (!site.done()) rest += step(site, town, true, later++);
            assertEquals(6, rest);
            assertEquals(60, site.written.size(), "nothing skipped for good");
            assertEquals(0, site.doubles, "nothing placed twice");
            assertEquals(0, site.intoBodies);
            assertEquals(level == 10 ? 0 : 60, paidBlocks(town) - paidBefore);
        }
    }

    @Test void aPlayersCellWaitsForThemAndTheyAreNeverMoved() {
        var site = new Site();
        var town = townWithBuilderAt(4);
        var player = SiteBodies.Body.at(null, true, new Vector3d(2.5, 64, 2.5), VILLAGER);
        site.bodies.add(player);
        long now = 50_000;
        while (site.plot.getAssemblyPlacedBlockCount() < 58) step(site, town, true, now++);
        // Far longer than the wait: nothing is placed into the player and the player is still there.
        for (int i = 0; i < 20; i++) assertEquals(0, step(site, town, true, now + i * SiteRefuge.WAIT_MS));
        assertEquals(0, site.moved);
        assertTrue(site.bodies.contains(player));
        assertEquals(58, site.written.size());
        assertEquals(0, site.intoBodies);
        // They step out: the two pieces go in at the next step.
        site.bodies.clear();
        assertEquals(2, step(site, town, true, now + 21 * SiteRefuge.WAIT_MS));
        assertTrue(site.done());
    }

    @Test void theWaitIsBoundedStartsAgainAfterAMoveAndEndsWhenAnythingIsPlaced() {
        UUID plot = UUID.randomUUID();
        assertFalse(SiteRefuge.waitedOut(plot, 1_000), "the wait starts now");
        assertFalse(SiteRefuge.waitedOut(plot, 1_000 + SiteRefuge.WAIT_MS - 1));
        assertTrue(SiteRefuge.waitedOut(plot, 1_000 + SiteRefuge.WAIT_MS));
        // Asked once per wait, not on every tick after it.
        assertFalse(SiteRefuge.waitedOut(plot, 1_001 + SiteRefuge.WAIT_MS));
        assertTrue(SiteRefuge.waitedOut(plot, 1_000 + 2 * SiteRefuge.WAIT_MS));
        // Something was placed: the next time the site is stuck it waits the whole time again.
        SiteRefuge.notWaiting(plot);
        assertFalse(SiteRefuge.waitedOut(plot, 60_000));
        assertFalse(SiteRefuge.waitedOut(plot, 60_000 + SiteRefuge.WAIT_MS - 1));
        assertTrue(SiteRefuge.waitedOut(plot, 60_000 + SiteRefuge.WAIT_MS));
        SiteRefuge.notWaiting(plot);
        // Another site's wait is its own.
        UUID other = UUID.randomUUID();
        assertFalse(SiteRefuge.waitedOut(other, 100_000));
        assertFalse(SiteRefuge.waitedOut(plot, 100_000 + SiteRefuge.WAIT_MS));
        SiteRefuge.notWaiting(plot);
        SiteRefuge.notWaiting(other);
    }

    @Test void theFrontierGivesTheSmallestPieceThatIsNotWaiting() {
        var site = new Site();
        assertEquals(0, site.frontier.smallestPlacementIndex(null));
        assertEquals(0, site.frontier.smallestPlacementIndex(new IntOpenHashSet()));
        assertEquals(1, site.frontier.smallestPlacementIndex(new IntOpenHashSet(new int[] {0})));
        assertEquals(3, site.frontier.smallestPlacementIndex(new IntOpenHashSet(new int[] {0, 1, 2})));
        var everything = new IntOpenHashSet();
        for (int i = 0; i < 60; i++) everything.add(i);
        assertEquals(-1, site.frontier.smallestPlacementIndex(everything), "everything is waiting");
        assertEquals(0, site.frontier.smallestPlacementIndex(), "and nothing was taken off the frontier by asking");
    }

    @Test void theNearestSafeSpotIsFoundByRingsAndWithinLimits() {
        // Where they are, if it will do.
        assertArrayEquals(new int[] {10, 64, 10}, SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> true));
        // Their own cell will not do: a cell beside it on the same level, not the top of what buries them.
        int[] beside = SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> !(x == 10 && z == 10 && y == 64));
        assertNotNull(beside);
        assertEquals(64, beside[1]);
        assertEquals(1, Math.abs(beside[0] - 10) + Math.abs(beside[2] - 10));
        // Up or down in their own column only when that is nearer than any way sideways: one cell of height counts double.
        assertArrayEquals(new int[] {10, 65, 10}, SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> y == 65 || Math.abs(x - 10) + Math.abs(z - 10) >= 3));
        assertArrayEquals(new int[] {12, 64, 10}, SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> (y == 66 && x == 10 && z == 10) || (y == 64 && x == 12 && z == 10)));
        assertArrayEquals(new int[] {10, 63, 10}, SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> x == 10 && z == 10 && y < 64));
        // The first ring before the second, and on a ring the nearest column: straight beside before a corner.
        assertArrayEquals(new int[] {11, 64, 10}, SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> x >= 11 && y == 64));
        assertArrayEquals(new int[] {12, 64, 10}, SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> x >= 12 && y == 64));
        // A building of solid cells around them: a nearest cell that is not one of them, three out and straight across.
        int[] out = SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> y == 64 && (Math.abs(x - 10) > 2 || Math.abs(z - 10) > 2));
        assertNotNull(out);
        assertEquals(64, out[1]);
        assertEquals(3, Math.abs(out[0] - 10) + Math.abs(out[2] - 10), "three cells away, not at a corner of the ring");
        // Limits: not further out, not higher, not lower than asked.
        assertNull(SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> x == 19));
        assertArrayEquals(new int[] {18, 64, 10}, SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> x == 18 && z == 10 && y == 64));
        assertNull(SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> y == 68));
        assertArrayEquals(new int[] {10, 67, 10}, SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> y == 67));
        assertNull(SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> y == 57));
        assertArrayEquals(new int[] {10, 58, 10}, SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> y == 58));
        assertNull(SiteRefuge.nearest(10, 64, 10, 8, 3, 6, (x, y, z) -> false));
    }
}
