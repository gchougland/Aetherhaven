package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.questboard.RaidSpawnPositionFinder.Result;
import com.hexvane.aetherhaven.questboard.RaidSpawnPositionFinder.Site;
import com.hexvane.aetherhaven.questboard.RaidSpawnPositionFinder.Tier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Raid placement on made up land, old search against new.
 *
 * <p>{@link Land} is a fake terrain reader. It follows the game's own rule for which land is awake: a cube of 32
 * blocks is simulated when it lies within eight cubes of a player, measured in all three directions (the game's
 * {@code ChunkTracker.MAX_HOT_LOADED_RADIUS}). A creature added anywhere else is put straight back into storage
 * ({@code UpdateLocationSystems.updateEntityInChunk}), which the old search never checked.
 *
 * <p>{@link Legacy} is the search as it was before, line for line, reading the same land. It could look at any land
 * because it loaded and woke chunks as it went. Its result only counts as a started raid when the place it chose is
 * awake without that, which is the rule the game applies and the rule the owner set (no forced chunk loading).
 */
@Tag("raid")
class RaidSpawnPlacementTest {
    private static final int SEEDS = 40;
    private static final int WAVE = 8;
    private static final int GROUND = 64;

    // ---------------------------------------------------------------- scenarios

    @Test
    void openPlainsWithThePlayerAtTheCharter() {
        Rates rates = run("plains, default town, player at charter", seed -> plains(seed, 6, 0, 0));
        assertEquals(1.0, rates.started(), "every raid starts");
        assertTrue(rates.oldStarted() < 1.0, "the old search began 120 blocks to one side, at the edge of the awake land");
        assertEquals(1.0, rates.share(Tier.OUTSKIRTS), "open plains always give clear ground outside the border");
    }

    @Test
    void openPlainsWithThePlayerAtAQuestBoardOffCentre() {
        Rates rates = run("plains, default town, player 70 blocks south", seed -> plains(seed, 6, 0, 70));
        assertEquals(1.0, rates.started());
        assertTrue(rates.oldStarted() <= 0.6, "old: most sides are asleep and it never looked for one that is awake");
        assertTrue(rates.oldLeftRaidersBehind() > 0.0, "old: raiders put into sleeping land while the raid was reported failed");
    }

    @Test
    void rollingHills() {
        Rates rates = run("hills, default town, player at charter", seed -> {
            Land land = plains(seed, 6, 0, 0);
            land.hills = 22;
            return land;
        });
        assertEquals(1.0, rates.started());
        assertTrue(rates.started() > rates.oldStarted());
    }

    @Test
    void closedForest() {
        Rates rates = run("forest with a closed canopy, default town", seed -> {
            Land land = plains(seed, 6, 0, 0);
            land.canopy = true;
            return land;
        });
        assertEquals(0.0, rates.oldStarted(), "old: wanted ten blocks of open sky, so nothing under trees ever passed");
        assertEquals(1.0, rates.started(), "new: stands the raiders on the forest floor");
        assertEquals(1.0, rates.share(Tier.ROUGH_OUTSKIRTS));
    }

    @Test
    void islandTownWithSeaOnEverySide() {
        Rates rates = run("coast: island town, sea past the border", seed -> {
            Land land = plains(seed, 6, 0, 0);
            land.seaAllRound = true;
            return land;
        });
        assertEquals(0.0, rates.oldStarted(), "old: only ever looked outside the border, which is all water");
        assertEquals(1.0, rates.started(), "new: falls back to dry land just inside the border");
        assertEquals(1.0, rates.share(Tier.INSIDE_BORDER));
    }

    @Test
    void coastOnOneSideOnly() {
        Rates rates = run("coast: sea to the north only", seed -> {
            Land land = plains(seed, 6, 0, 0);
            land.seaNorth = true;
            return land;
        });
        assertEquals(1.0, rates.started());
        assertTrue(rates.started() > rates.oldStarted());
    }

    @Test
    void expandedTownWhoseBorderIsBeyondTheAwakeLand() {
        Rates rates = run("large town, border ten chunks out", seed -> plains(seed, 10, 0, 0));
        assertEquals(0.0, rates.oldStarted(), "old: every place it chose was asleep");
        assertEquals(1.0, rates.oldLeftRaidersBehind());
        assertEquals(1.0, rates.started(), "new: as far out as the awake land reaches");
        assertEquals(1.0, rates.share(Tier.INSIDE_BORDER));
    }

    @Test
    void nowhereAwakeMeansNoRaidAndNothingSpawned() {
        Rates rates = run("nobody near town", seed -> {
            Land land = plains(seed, 6, 0, 0);
            land.playerX = 5000;
            land.playerZ = 5000;
            return land;
        });
        assertEquals(0.0, rates.started(), "there is no awake land, and none is loaded to make some");
        assertEquals(0.0, rates.oldStarted());
        assertEquals(1.0, rates.oldLeftRaidersBehind(), "old: still chose places, all asleep");
    }

    // ---------------------------------------------------------------- single rules

    @Test
    void aWaveIsEveryRaiderOrNone() {
        Land land = plains(3, 6, 0, 0);
        List<Result> wave = RaidSpawnPositionFinder.planWave(land, land.site(), RaidApproachDirection.EAST, new Random(1), 22);
        assertEquals(22, wave.size());
        Set<String> cells = new HashSet<>();
        for (Result r : wave) {
            assertEquals(wave.get(0).direction(), r.direction(), "one wave, one side");
            cells.add((int) Math.floor(r.position().x) + "," + (int) Math.floor(r.position().z));
        }
        assertEquals(22, cells.size(), "open ground gives every raider a cell of its own");
        assertTrue(RaidSpawnPositionFinder.planWave(land, land.site(), RaidApproachDirection.EAST, new Random(1), 0).isEmpty());
    }

    @Test
    void theSearchKeepsToTheOfferedSideWhenItCan() {
        Land land = plains(5, 6, 0, 0);
        for (RaidApproachDirection side : RaidApproachDirection.values()) {
            List<Result> wave = RaidSpawnPositionFinder.planWave(land, land.site(), side, new Random(9), 4);
            assertEquals(side, wave.get(0).direction());
            assertEquals(Tier.OUTSKIRTS, wave.get(0).tier());
        }
    }

    @Test
    void raidersNeverStandOnABuildingPlotEvenInsideTheBorder() {
        Land land = plains(7, 10, 0, 0);
        // Pave the whole awake part of town with plots except one lane.
        land.plots.clear();
        land.plots.add(new int[] {-400, -400, 400, -9});
        land.plots.add(new int[] {-400, 9, 400, 400});
        List<Result> wave = RaidSpawnPositionFinder.planWave(land, land.site(), RaidApproachDirection.NORTH, new Random(2), 6);
        assertFalse(wave.isEmpty(), "the lane between the plots is still open");
        for (Result r : wave) {
            assertFalse(land.building((int) Math.floor(r.position().x), (int) Math.floor(r.position().z)));
        }
        land.plots.add(new int[] {-400, -8, 400, 8});
        assertTrue(RaidSpawnPositionFinder.planWave(land, land.site(), RaidApproachDirection.NORTH, new Random(2), 6).isEmpty(),
            "with every awake cell on a plot there is no raid");
    }

    @Test
    void aCampSiteIsNeverInsideTheBorderAndLaterTriesLookElsewhere() {
        Land land = plains(11, 6, 0, 0);
        Set<String> spots = new HashSet<>();
        for (int attempt = 0; attempt < 12; attempt++) {
            Random rng = new Random(100 + attempt);
            int shift = attempt == 0 ? 0 : rng.nextInt(RaidSpawnPositionFinder.CAMP_SHIFT_BLOCKS * 2 + 1) - RaidSpawnPositionFinder.CAMP_SHIFT_BLOCKS;
            Result spot = RaidSpawnPositionFinder.find(land, land.site(), RaidApproachDirection.SOUTH, null, rng, attempt, shift, false);
            assertNotNull(spot);
            assertFalse(land.claimed((int) Math.floor(spot.position().x), (int) Math.floor(spot.position().z)));
            spots.add((int) Math.floor(spot.position().x) / 8 + "," + (int) Math.floor(spot.position().z) / 8);
        }
        assertTrue(spots.size() >= 6, "twelve tries must not test the same ground twelve times, saw " + spots.size());

        Land island = plains(11, 6, 0, 0);
        island.seaAllRound = true;
        assertNull(RaidSpawnPositionFinder.find(island, island.site(), RaidApproachDirection.SOUTH, null, new Random(1), 0, 0, false),
            "a camp is pasted over the land, so it is never put inside the town");
    }

    @Test
    void aColumnThatIsAwakeIsNotEnoughTheCellItselfMustBe() {
        // The player is deep underground: the columns above are flagged awake, the surface cubes are not.
        Land land = plains(13, 6, 0, 0);
        land.playerY = GROUND - 8 * 32 - 40;
        assertTrue(land.live(0, 40), "the column counts as awake");
        assertFalse(land.liveCell(0, GROUND + 1, 40), "the surface of it does not");
        assertTrue(RaidSpawnPositionFinder.planWave(land, land.site(), RaidApproachDirection.NORTH, new Random(4), 5).isEmpty());
    }

    // ---------------------------------------------------------------- harness

    private interface Scenario {
        Land build(long seed);
    }

    /** Shares of the trials, each from 0 to 1. */
    private record Rates(double started, double oldStarted, double oldFound, double oldLeftRaidersBehind, int[] tiers, int trials) {
        double share(Tier tier) {
            return tiers[tier.ordinal()] / (double) Math.max(1, (int) Math.round(started * trials));
        }
    }

    private static Rates run(String name, Scenario scenario) {
        int trials = 0, started = 0, oldStarted = 0, oldFound = 0, oldBehind = 0;
        int[] tiers = new int[Tier.values().length];
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (RaidApproachDirection side : RaidApproachDirection.values()) {
                trials++;
                Land land = scenario.build(seed);
                Site site = land.site();

                List<Result> wave = RaidSpawnPositionFinder.planWave(land, site, side, new Random(seed * 31 + side.ordinal()), WAVE);
                if (!wave.isEmpty()) {
                    assertEquals(WAVE, wave.size(), "a wave is whole");
                    started++;
                    tiers[wave.get(0).tier().ordinal()]++;
                    for (Result r : wave) {
                        assertSafe(land, r);
                    }
                }

                // The old search placed each raider on its own. The raid was reported as started when at least one
                // of them stayed in the world.
                Random oldRng = new Random(seed * 31 + side.ordinal());
                RaidApproachDirection locked = null;
                int awake = 0, asleep = 0;
                for (int mob = 0; mob < WAVE; mob++) {
                    Legacy.Found found = Legacy.findForMob(land, site, side, locked, oldRng, mob);
                    if (found == null) {
                        continue;
                    }
                    locked = found.direction();
                    Vector3d p = found.position();
                    if (land.liveCell((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z))) {
                        awake++;
                    } else {
                        asleep++;
                    }
                }
                if (awake + asleep > 0) {
                    oldFound++;
                }
                if (awake > 0) {
                    oldStarted++;
                } else if (asleep > 0) {
                    oldBehind++;
                }
            }
        }
        Rates rates = new Rates(started / (double) trials, oldStarted / (double) trials, oldFound / (double) trials,
            oldBehind / (double) trials, tiers, trials);
        System.out.printf(Locale.ROOT,
            "RAID PLACEMENT | %-46s | old started %3.0f%% (chose a place %3.0f%%, raiders left asleep with the raid failed %3.0f%%)"
                + " | new started %3.0f%% [clear %d, rough %d, inside border %d of %d]%n",
            name, rates.oldStarted() * 100, rates.oldFound() * 100, rates.oldLeftRaidersBehind() * 100, rates.started() * 100,
            tiers[Tier.OUTSKIRTS.ordinal()], tiers[Tier.ROUGH_OUTSKIRTS.ordinal()], tiers[Tier.INSIDE_BORDER.ordinal()], trials);
        assertTrue(rates.started() >= rates.oldStarted(), name + ": the new search must never do worse");
        return rates;
    }

    /** What must hold for every raider, whatever stage placed it. */
    private static void assertSafe(Land land, Result r) {
        int x = (int) Math.floor(r.position().x), y = (int) Math.floor(r.position().y), z = (int) Math.floor(r.position().z);
        String at = " at " + x + "," + y + "," + z + " (" + r.tier() + ")";
        assertTrue(land.live(x, z) && land.liveCell(x, y, z), "awake land only" + at);
        assertTrue(land.solid(x, y - 1, z), "solid footing" + at);
        assertTrue(land.open(x, y, z) && land.open(x, y + 1, z), "room for a body" + at);
        assertFalse(land.fluid(x, y, z) || land.fluid(x, y + 1, z), "not in water" + at);
        assertTrue(y > land.surface(x, z), "at the surface, not under it" + at);
        assertFalse(land.building(x, z), "not on a building plot" + at);
        if (r.tier() != Tier.INSIDE_BORDER) {
            assertFalse(land.claimed(x, z), "outside the border" + at);
        } else {
            assertTrue(Math.max(Math.abs(x - land.charterX), Math.abs(z - land.charterZ)) >= RaidSpawnPositionFinder.MIN_CHARTER_DISTANCE,
                "not on top of the charter" + at);
        }
    }

    private static Land plains(long seed, int claimRadiusChunks, int playerDx, int playerDz) {
        Land land = new Land(seed, claimRadiusChunks);
        land.playerX = land.charterX + playerDx;
        land.playerZ = land.charterZ + playerDz;
        land.playerY = GROUND + 1;
        return land;
    }

    /** Made up land around a town whose charter sits somewhere inside its chunk, as real ones do. */
    private static final class Land implements RaidSpawnTerrain {
        static final int CUBE = 32;
        static final int AWAKE_RADIUS = 8;

        final long seed;
        final int charterX, charterZ, claimRadiusChunks;
        final List<int[]> plots = new ArrayList<>();
        int playerX, playerY, playerZ;
        /** Height of the hills, 0 for flat land. */
        int hills;
        /** A roof of leaves nine blocks up over everything. Leaves are solid and let light through. */
        boolean canopy;
        /** Sea on every side, starting two blocks past the border. */
        boolean seaAllRound;
        /** Sea to the north only, starting two blocks past the border. */
        boolean seaNorth;

        Land(long seed, int claimRadiusChunks) {
            this.seed = seed;
            this.claimRadiusChunks = claimRadiusChunks;
            Random r = new Random(seed * 7919);
            charterX = r.nextInt(CUBE);
            charterZ = r.nextInt(CUBE);
            for (int i = 0; i < 12; i++) {
                int px = charterX + r.nextInt(240) - 120, pz = charterZ + r.nextInt(240) - 120;
                plots.add(new int[] {px, pz, px + 14, pz + 14});
            }
        }

        private int claimMin(int charter) {
            return (Math.floorDiv(charter, CUBE) - claimRadiusChunks) * CUBE;
        }

        private int claimMax(int charter) {
            return (Math.floorDiv(charter, CUBE) + claimRadiusChunks) * CUBE - 1;
        }

        Site site() {
            return new Site(charterX, GROUND + 1, charterZ,
                charterZ - claimMin(charterZ), claimMax(charterZ) - charterZ, claimMax(charterX) - charterX, charterX - claimMin(charterX));
        }

        private boolean sea(int x, int z) {
            boolean north = z < claimMin(charterZ) - 2;
            if (seaNorth && north) {
                return true;
            }
            return seaAllRound
                && (north || z > claimMax(charterZ) + 2 || x < claimMin(charterX) - 2 || x > claimMax(charterX) + 2);
        }

        /** Top solid block of the ground. The sea bed lies six blocks under the water. */
        private int ground(int x, int z) {
            if (sea(x, z)) {
                return GROUND - 7;
            }
            if (hills == 0) {
                return GROUND;
            }
            double wave = Math.sin((x + seed * 13) / 9.0) + Math.cos((z - seed * 5) / 7.0) + Math.sin((x + z) / 23.0);
            return GROUND + (int) Math.round(wave / 3.0 * hills);
        }

        private boolean leaves(int x, int y, int z) {
            int floor = ground(x, z);
            return canopy && !sea(x, z) && y >= floor + 9 && y <= floor + 11;
        }

        @Override
        public boolean live(int x, int z) {
            int dx = Math.floorDiv(x, CUBE) - Math.floorDiv(playerX, CUBE), dz = Math.floorDiv(z, CUBE) - Math.floorDiv(playerZ, CUBE);
            return dx * dx + dz * dz <= AWAKE_RADIUS * AWAKE_RADIUS;
        }

        @Override
        public boolean liveCell(int x, int y, int z) {
            int dx = Math.floorDiv(x, CUBE) - Math.floorDiv(playerX, CUBE);
            int dy = Math.floorDiv(y, CUBE) - Math.floorDiv(playerY, CUBE);
            int dz = Math.floorDiv(z, CUBE) - Math.floorDiv(playerZ, CUBE);
            return dx * dx + dy * dy + dz * dz <= AWAKE_RADIUS * AWAKE_RADIUS;
        }

        @Override
        public int surface(int x, int z) {
            return ground(x, z);
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return y >= 0 && y < 320 && (y <= ground(x, z) || leaves(x, y, z));
        }

        @Override
        public boolean open(int x, int y, int z) {
            return y >= 0 && y < 320 && !solid(x, y, z);
        }

        @Override
        public boolean fluid(int x, int y, int z) {
            return sea(x, z) && y > ground(x, z) && y <= GROUND - 1;
        }

        @Override
        public boolean building(int x, int z) {
            for (int[] p : plots) {
                if (x >= p[0] && x <= p[2] && z >= p[1] && z <= p[3]) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean claimed(int x, int z) {
            return x >= claimMin(charterX) && x <= claimMax(charterX) && z >= claimMin(charterZ) && z <= claimMax(charterZ);
        }
    }

    /**
     * The search as it was before this change (RaidSpawnPositionFinder.searchDirection and
     * RaidSpawnGroundUtil.findSpawnPosition), kept here so the two can be measured on the same land. It reads any
     * column, awake or not, because the original loaded and woke chunks to do so.
     */
    private static final class Legacy {
        record Found(Vector3d position, RaidApproachDirection direction) {}

        static Found findForMob(Land land, Site site, RaidApproachDirection preferred, RaidApproachDirection locked, Random rng, int mobIndex) {
            for (RaidApproachDirection direction : RaidSpawnPositionFinder.orderDirections(preferred, locked, rng)) {
                Vector3d position = searchDirection(land, site, direction, rng, mobIndex);
                if (position != null) {
                    return new Found(position, direction);
                }
            }
            return null;
        }

        private static Vector3d searchDirection(Land land, Site site, RaidApproachDirection direction, Random rng, int mobIndex) {
            int borderAlong = site.border(direction);
            int minAlong = borderAlong + 4;
            int maxAlongPreferred = borderAlong + 4 + rng.nextInt(10 - 4 + 1) + mobIndex * 2;
            int maxAlongSearch = maxAlongPreferred + 24;
            for (int attempt = 0; attempt < 120; attempt++) {
                int lateral = ((attempt % 41) * 8) - 120 + rng.nextInt(5);
                int along = minAlong + (attempt / 41) * 3;
                if (along > maxAlongPreferred) {
                    continue;
                }
                Vector3d pos = probeAt(land, site, direction, along, lateral);
                if (pos != null) {
                    return pos;
                }
            }
            for (int alongStep = 0; alongStep < 12; alongStep++) {
                int along = minAlong + alongStep * 2;
                if (along > maxAlongSearch) {
                    break;
                }
                for (int lateral = -120; lateral <= 120; lateral += 4) {
                    Vector3d pos = probeAt(land, site, direction, along, lateral);
                    if (pos != null) {
                        return pos;
                    }
                }
            }
            for (int ring = 0; ring < 20; ring++) {
                int along = maxAlongPreferred + ring * 3;
                if (along > maxAlongSearch) {
                    break;
                }
                for (int lateral = -along; lateral <= along; lateral += 6) {
                    Vector3d pos = probeAt(land, site, direction, along, lateral);
                    if (pos != null) {
                        return pos;
                    }
                }
            }
            return null;
        }

        private static Vector3d probeAt(Land land, Site site, RaidApproachDirection direction, int along, int lateral) {
            int bx = site.x(direction, along, lateral), bz = site.z(direction, along, lateral);
            int surface = land.surface(bx, bz);
            if (surface < 1) {
                return null;
            }
            for (int feetY = Math.min(319, surface + 8); feetY >= Math.max(1, surface + 2); feetY--) {
                if (stand(land, bx, feetY, bz)) {
                    return new Vector3d(bx + 0.5, feetY, bz + 0.5);
                }
            }
            if (stand(land, bx, surface + 1, bz)) {
                return new Vector3d(bx + 0.5, surface + 1, bz + 0.5);
            }
            // The original went on to try the surface block itself and below; its own height check refused those.
            return null;
        }

        private static boolean stand(Land land, int x, int feetY, int z) {
            if (!land.open(x, feetY, z) || !land.open(x, feetY + 1, z) || !land.solid(x, feetY - 1, z)) {
                return false;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (land.fluid(x + dx, feetY, z + dz) || land.fluid(x + dx, feetY + 1, z + dz)) {
                        return false;
                    }
                }
            }
            if (!land.solid(x, feetY - 2, z)) {
                return false;
            }
            for (int y = feetY + 2; y <= Math.min(319, feetY + 10); y++) {
                if (!land.open(x, y, z)) {
                    return false;
                }
            }
            return true;
        }
    }
}
