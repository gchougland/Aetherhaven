package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Chooses where a raid wave appears.
 *
 * <p>The search reads {@link RaidSpawnTerrain}, which only describes land that is already in memory, so looking for
 * a place never loads a chunk or wakes a sleeping one. It works outwards in stages and keeps the best place it has
 * seen, so awkward land gives a rougher spot instead of no raid:
 *
 * <ol>
 *   <li>good open ground just outside the border, straight out from the charter first and then to either side,
 *       on the offered side and then on the other three;
 *   <li>the same search farther out and wider;
 *   <li>rough ground outside the border: under trees, beside water, on a slope or on thin ground;
 *   <li>ground inside the border, as far out as the live land reaches, never on a building plot.
 * </ol>
 *
 * <p>Whatever the stage, a raider only ever stands on solid ground at the surface, with room for its body, out of
 * water, off every building plot and in a part of the world that is awake.
 */
final class RaidSpawnPositionFinder {
    static final int MAX_LATERAL_BLOCKS = 120;
    static final int WIDE_LATERAL_BLOCKS = 160;
    /** Minimum blocks outside the claim border along the approach direction. */
    static final int OUTSKIRTS_MIN_PADDING = 4;
    /** Maximum blocks outside the claim border for the preferred spawn ring. */
    static final int OUTSKIRTS_MAX_PADDING = 10;
    /** Each later raider of a wave may stand this much farther out. */
    static final int MOB_ALONG_SPACING = 2;
    /** How far past the border the wider search reaches. */
    static final int OUTSKIRTS_WIDE_EXTRA = 64;
    /** Raiders placed inside the border keep at least this far from the charter. */
    static final int MIN_CHARTER_DISTANCE = 24;
    /** A stand may sit this far above the opaque surface, on snow, a leaf pile or a slab. */
    static final int STAND_SCAN_ABOVE_SURFACE = 8;
    static final int OPEN_SKY_CLEARANCE_BLOCKS = 10;
    static final int LEVEL_FOOTING_REACH = 2;
    static final int LEVEL_FOOTING_DELTA = 2;
    /** How far from the first raider the rest of the wave may stand. */
    static final int WAVE_RADIUS = 14;
    /** Camp searches after the first slide sideways by up to this much, so each try looks at a new place. */
    static final int CAMP_SHIFT_BLOCKS = 96;
    private static final int MAX_FEET_Y = 317;

    private RaidSpawnPositionFinder() {}

    /** How good the place is. Later values were only used because nothing better was awake. */
    enum Tier {
        OUTSKIRTS,
        ROUGH_OUTSKIRTS,
        INSIDE_BORDER
    }

    record Result(@Nonnull Vector3d position, @Nonnull RaidApproachDirection direction, @Nonnull Tier tier) {}

    /** The charter and how far the claim reaches from it on each side. */
    record Site(int charterX, int charterY, int charterZ, int north, int south, int east, int west) {
        @Nonnull
        static Site of(@Nonnull TownRecord town) {
            TownTerritoryClaims.migrateIfNeeded(town);
            return new Site(
                town.getCharterX(),
                town.getCharterY(),
                town.getCharterZ(),
                border(town, RaidApproachDirection.NORTH),
                border(town, RaidApproachDirection.SOUTH),
                border(town, RaidApproachDirection.EAST),
                border(town, RaidApproachDirection.WEST)
            );
        }

        private static int border(@Nonnull TownRecord town, @Nonnull RaidApproachDirection direction) {
            return TownTerritoryClaims.charterToClaimBorderAlong(town, direction.axisX(), direction.axisZ());
        }

        int border(@Nonnull RaidApproachDirection direction) {
            return switch (direction) {
                case NORTH -> north;
                case SOUTH -> south;
                case EAST -> east;
                case WEST -> west;
            };
        }

        int x(@Nonnull RaidApproachDirection direction, int along, int lateral) {
            return charterX + direction.axisX() * along + (direction.axisX() != 0 ? 0 : lateral);
        }

        int z(@Nonnull RaidApproachDirection direction, int along, int lateral) {
            return charterZ + direction.axisZ() * along + (direction.axisX() != 0 ? lateral : 0);
        }
    }

    /** A place to stand in one column. {@code good} means it met every preference, not only the safety rules. */
    record Stand(int x, int feetY, int z, boolean good) {
        @Nonnull
        Vector3d position() {
            return new Vector3d(x + 0.5, feetY, z + 0.5);
        }
    }

    private static final class Scan {
        @Nullable Stand good;
        @Nullable Stand rough;
    }

    /** Places for a whole raid wave in awake terrain: one per raider, or an empty list when there is nowhere. */
    @Nonnull
    static List<Result> planWave(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull RaidApproachDirection preferred,
        @Nonnull Random rng,
        int count
    ) {
        return planWave(WorldRaidSpawnTerrain.simulating(world), Site.of(town), preferred, rng, count);
    }

    /**
     * One spot outside the border for something that is pasted, not spawned (a rescue camp). The land only has to
     * be in memory. Never returns a spot inside the border.
     *
     * @param attempt later attempts look to one side of the first, so a rejected spot is not offered again
     */
    @Nullable
    static Result findOutsideTown(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull RaidApproachDirection preferred,
        @Nullable RaidApproachDirection lockedDirection,
        @Nonnull Random rng,
        int attempt
    ) {
        int shift = attempt <= 0 ? 0 : rng.nextInt(CAMP_SHIFT_BLOCKS * 2 + 1) - CAMP_SHIFT_BLOCKS;
        return find(WorldRaidSpawnTerrain.inMemory(world), Site.of(town), preferred, lockedDirection, rng, attempt, shift, false);
    }

    /**
     * All or nothing: the first raider is placed by the full search and the rest gather around it. When there is a
     * first place there is always a place for everyone, sharing a cell if the ground is that cramped.
     */
    @Nonnull
    static List<Result> planWave(
        @Nonnull RaidSpawnTerrain terrain,
        @Nonnull Site site,
        @Nonnull RaidApproachDirection preferred,
        @Nonnull Random rng,
        int count
    ) {
        if (count <= 0) {
            return List.of();
        }
        Result anchor = find(terrain, site, preferred, null, rng, 0, 0, true);
        if (anchor == null) {
            return List.of();
        }
        List<Result> wave = new ArrayList<>(count);
        wave.add(anchor);
        int anchorX = (int) Math.floor(anchor.position().x);
        int anchorZ = (int) Math.floor(anchor.position().z);
        Set<Long> used = new HashSet<>();
        used.add(cellKey(anchorX, anchorZ));
        boolean insideBorder = anchor.tier() == Tier.INSIDE_BORDER;
        for (int i = 1; i < count; i++) {
            Stand stand = beside(terrain, site, anchorX, anchorZ, insideBorder, used);
            if (stand == null) {
                wave.add(anchor);
                continue;
            }
            used.add(cellKey(stand.x(), stand.z()));
            Tier tier = insideBorder ? Tier.INSIDE_BORDER : stand.good() ? Tier.OUTSKIRTS : Tier.ROUGH_OUTSKIRTS;
            wave.add(new Result(stand.position(), anchor.direction(), tier));
        }
        return wave;
    }

    @Nullable
    static Result find(
        @Nonnull RaidSpawnTerrain terrain,
        @Nonnull Site site,
        @Nonnull RaidApproachDirection preferred,
        @Nullable RaidApproachDirection lockedDirection,
        @Nonnull Random rng,
        int mobIndex,
        int lateralShift,
        boolean allowInsideBorder
    ) {
        List<RaidApproachDirection> directions = orderDirections(preferred, lockedDirection, rng);
        Stand[] rough = new Stand[directions.size()];
        for (int i = 0; i < directions.size(); i++) {
            Scan scan = new Scan();
            scanOutskirts(terrain, site, directions.get(i), rng, mobIndex, lateralShift, scan);
            if (scan.good != null) {
                return new Result(scan.good.position(), directions.get(i), Tier.OUTSKIRTS);
            }
            rough[i] = scan.rough;
        }
        for (int i = 0; i < rough.length; i++) {
            if (rough[i] != null) {
                return new Result(rough[i].position(), directions.get(i), Tier.ROUGH_OUTSKIRTS);
            }
        }
        if (!allowInsideBorder) {
            return null;
        }
        for (RaidApproachDirection direction : directions) {
            Stand stand = scanInsideBorder(terrain, site, direction, rng, lateralShift);
            if (stand != null) {
                return new Result(stand.position(), direction, Tier.INSIDE_BORDER);
            }
        }
        return null;
    }

    @Nonnull
    static List<RaidApproachDirection> orderDirections(
        @Nonnull RaidApproachDirection preferred,
        @Nullable RaidApproachDirection lockedDirection,
        @Nonnull Random rng
    ) {
        List<RaidApproachDirection> ordered = new ArrayList<>(4);
        if (lockedDirection != null) {
            ordered.add(lockedDirection);
        } else {
            ordered.add(preferred);
        }
        List<RaidApproachDirection> rest = new ArrayList<>();
        for (RaidApproachDirection direction : RaidApproachDirection.values()) {
            if (!ordered.contains(direction)) {
                rest.add(direction);
            }
        }
        for (int i = rest.size() - 1; i > 0; i--) {
            int swap = rng.nextInt(i + 1);
            RaidApproachDirection tmp = rest.get(i);
            rest.set(i, rest.get(swap));
            rest.set(swap, tmp);
        }
        ordered.addAll(rest);
        return ordered;
    }

    private static void scanOutskirts(
        @Nonnull RaidSpawnTerrain terrain,
        @Nonnull Site site,
        @Nonnull RaidApproachDirection direction,
        @Nonnull Random rng,
        int mobIndex,
        int lateralShift,
        @Nonnull Scan scan
    ) {
        int border = site.border(direction);
        int minAlong = border + OUTSKIRTS_MIN_PADDING;
        int preferredMax =
            minAlong
                + rng.nextInt(OUTSKIRTS_MAX_PADDING - OUTSKIRTS_MIN_PADDING + 1)
                + Math.max(0, mobIndex) * MOB_ALONG_SPACING;
        for (int along = minAlong; along <= preferredMax; along += 3) {
            if (sweepRow(terrain, site, direction, along, lateralShift, 8, MAX_LATERAL_BLOCKS, rng, false, scan)) {
                return;
            }
        }
        int farthest = border + OUTSKIRTS_WIDE_EXTRA;
        for (int along = minAlong + 1; along <= farthest; along += 4) {
            if (sweepRow(terrain, site, direction, along, lateralShift, 6, WIDE_LATERAL_BLOCKS, rng, false, scan)) {
                return;
            }
        }
    }

    /** Rows from the border inwards. The first place found is the farthest out that is awake. */
    @Nullable
    private static Stand scanInsideBorder(
        @Nonnull RaidSpawnTerrain terrain,
        @Nonnull Site site,
        @Nonnull RaidApproachDirection direction,
        @Nonnull Random rng,
        int lateralShift
    ) {
        Scan scan = new Scan();
        for (int along = site.border(direction) + OUTSKIRTS_MIN_PADDING - 2; along >= MIN_CHARTER_DISTANCE; along -= 6) {
            if (sweepRow(terrain, site, direction, along, lateralShift, 8, MAX_LATERAL_BLOCKS, rng, true, scan)) {
                return scan.good;
            }
            if (scan.rough != null) {
                return scan.rough;
            }
        }
        return null;
    }

    /**
     * Looks along one row, from the middle outwards to both sides.
     *
     * @return true when a good stand was found and the search can stop
     */
    private static boolean sweepRow(
        @Nonnull RaidSpawnTerrain terrain,
        @Nonnull Site site,
        @Nonnull RaidApproachDirection direction,
        int along,
        int lateralShift,
        int step,
        int reach,
        @Nonnull Random rng,
        boolean insideBorder,
        @Nonnull Scan scan
    ) {
        int probes = (reach / step) * 2 + 1;
        for (int i = 0; i < probes; i++) {
            int half = (i + 1) / 2;
            int lateral = lateralShift + (i % 2 == 1 ? half : -half) * step + rng.nextInt(3) - 1;
            int x = site.x(direction, along, lateral);
            int z = site.z(direction, along, lateral);
            if (!insideBorder && terrain.claimed(x, z)) {
                continue;
            }
            Stand stand = probe(terrain, x, z);
            if (stand == null) {
                continue;
            }
            if (stand.good()) {
                scan.good = stand;
                return true;
            }
            if (scan.rough == null) {
                scan.rough = stand;
            }
        }
        return false;
    }

    /** A free place near the first raider of the wave: the nearest good one, else the nearest rough one. */
    @Nullable
    private static Stand beside(
        @Nonnull RaidSpawnTerrain terrain,
        @Nonnull Site site,
        int anchorX,
        int anchorZ,
        boolean insideBorder,
        @Nonnull Set<Long> used
    ) {
        Stand rough = null;
        for (int radius = 2; radius <= WAVE_RADIUS; radius += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = anchorX + dx;
                    int z = anchorZ + dz;
                    if (used.contains(cellKey(x, z)) || (!insideBorder && terrain.claimed(x, z)) || tooCloseToCharter(site, x, z)) {
                        continue;
                    }
                    Stand stand = probe(terrain, x, z);
                    if (stand == null) {
                        continue;
                    }
                    if (stand.good()) {
                        return stand;
                    }
                    if (rough == null) {
                        rough = stand;
                    }
                }
            }
        }
        return rough;
    }

    private static boolean tooCloseToCharter(@Nonnull Site site, int x, int z) {
        return Math.max(Math.abs(x - site.charterX()), Math.abs(z - site.charterZ())) < MIN_CHARTER_DISTANCE;
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** The lowest safe place to stand above the surface of one column, or null when the column has none. */
    @Nullable
    static Stand probe(@Nonnull RaidSpawnTerrain terrain, int x, int z) {
        if (!terrain.live(x, z) || terrain.building(x, z)) {
            return null;
        }
        int surface = terrain.surface(x, z);
        if (surface == RaidSpawnTerrain.NO_SURFACE || surface < 1) {
            return null;
        }
        int top = Math.min(MAX_FEET_Y, surface + STAND_SCAN_ABOVE_SURFACE);
        for (int feetY = surface + 1; feetY <= top; feetY++) {
            if (isSafeStand(terrain, x, feetY, z)) {
                return new Stand(x, feetY, z, isGoodStand(terrain, x, feetY, z, surface));
            }
        }
        return null;
    }

    /** The rules no stage gives up: solid footing, room for a body, no water, and awake. */
    static boolean isSafeStand(@Nonnull RaidSpawnTerrain terrain, int x, int feetY, int z) {
        return terrain.solid(x, feetY - 1, z)
            && terrain.open(x, feetY, z)
            && terrain.open(x, feetY + 1, z)
            && !terrain.fluid(x, feetY, z)
            && !terrain.fluid(x, feetY + 1, z)
            && terrain.liveCell(x, feetY, z);
    }

    /** The preferences: deep ground, open sky, no water beside it, and level land to walk off on. */
    private static boolean isGoodStand(@Nonnull RaidSpawnTerrain terrain, int x, int feetY, int z, int surface) {
        if (!terrain.solid(x, feetY - 2, z)) {
            return false;
        }
        for (int y = feetY + 2; y <= feetY + OPEN_SKY_CLEARANCE_BLOCKS; y++) {
            if (!terrain.open(x, y, z)) {
                return false;
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = x + dx;
                int nz = z + dz;
                if (!terrain.live(nx, nz) || terrain.fluid(nx, feetY, nz) || terrain.fluid(nx, feetY + 1, nz)) {
                    return false;
                }
            }
        }
        return levelWith(terrain, x + LEVEL_FOOTING_REACH, z, surface)
            && levelWith(terrain, x - LEVEL_FOOTING_REACH, z, surface)
            && levelWith(terrain, x, z + LEVEL_FOOTING_REACH, surface)
            && levelWith(terrain, x, z - LEVEL_FOOTING_REACH, surface);
    }

    private static boolean levelWith(@Nonnull RaidSpawnTerrain terrain, int x, int z, int surface) {
        if (!terrain.live(x, z)) {
            return false;
        }
        int other = terrain.surface(x, z);
        return other != RaidSpawnTerrain.NO_SURFACE && Math.abs(other - surface) <= LEVEL_FOOTING_DELTA;
    }
}
