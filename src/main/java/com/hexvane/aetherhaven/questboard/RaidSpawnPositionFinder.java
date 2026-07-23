package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Finds dry land for raid wave spawns, rotating through cardinal directions when the chosen side is blocked. */
final class RaidSpawnPositionFinder {
    private static final int MAX_LATERAL_BLOCKS = 120;
    /** Minimum blocks outside the claim border along the approach direction. */
    private static final int OUTSKIRTS_MIN_PADDING = 4;
    /** Maximum blocks outside the claim border for the preferred spawn ring. */
    private static final int OUTSKIRTS_MAX_PADDING = 10;
    /** Spacing between mobs in the same wave along the approach axis. */
    private static final int MOB_ALONG_SPACING = 2;
    /** When the preferred ring fails, search this many blocks farther out before giving up on a direction. */
    private static final int OUTSKIRTS_FALLBACK_EXTRA = 24;

    private RaidSpawnPositionFinder() {}

    record Result(@Nonnull Vector3d position, @Nonnull RaidApproachDirection direction) {}

    @Nullable
    static Result findForMob(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull RaidApproachDirection preferred,
        @Nullable RaidApproachDirection lockedDirection,
        @Nonnull Random rng,
        int mobIndex
    ) {
        List<RaidApproachDirection> directions = orderDirections(preferred, lockedDirection, rng);
        for (RaidApproachDirection direction : directions) {
            Vector3d position = searchDirection(world, town, direction, rng, mobIndex);
            if (position != null) {
                return new Result(position, direction);
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

    @Nullable
    private static Vector3d searchDirection(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull RaidApproachDirection direction,
        @Nonnull Random rng,
        int mobIndex
    ) {
        int cx = town.getCharterX();
        int cz = town.getCharterZ();
        int charterY = town.getCharterY();
        TownTerritoryClaims.migrateIfNeeded(town);
        int borderAlong =
            TownTerritoryClaims.charterToClaimBorderAlong(town, direction.axisX(), direction.axisZ());
        int minAlong = borderAlong + OUTSKIRTS_MIN_PADDING;
        int maxAlongPreferred =
            borderAlong
                + OUTSKIRTS_MIN_PADDING
                + rng.nextInt(OUTSKIRTS_MAX_PADDING - OUTSKIRTS_MIN_PADDING + 1)
                + mobIndex * MOB_ALONG_SPACING;
        int maxAlongSearch = maxAlongPreferred + OUTSKIRTS_FALLBACK_EXTRA;

        for (int attempt = 0; attempt < 120; attempt++) {
            int lateral = ((attempt % 41) * 8) - MAX_LATERAL_BLOCKS + rng.nextInt(5);
            int along = minAlong + (attempt / 41) * 3;
            if (along > maxAlongPreferred) {
                continue;
            }
            Vector3d pos = probeAt(world, cx, cz, charterY, direction, along, lateral);
            if (pos != null) {
                return pos;
            }
        }

        for (int alongStep = 0; alongStep < 12; alongStep++) {
            int along = minAlong + alongStep * 2;
            if (along > maxAlongSearch) {
                break;
            }
            for (int lateral = -MAX_LATERAL_BLOCKS; lateral <= MAX_LATERAL_BLOCKS; lateral += 4) {
                Vector3d pos = probeAt(world, cx, cz, charterY, direction, along, lateral);
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
                Vector3d pos = probeAt(world, cx, cz, charterY, direction, along, lateral);
                if (pos != null) {
                    return pos;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Vector3d probeAt(
        @Nonnull World world,
        int cx,
        int cz,
        int charterY,
        @Nonnull RaidApproachDirection direction,
        int along,
        int lateral
    ) {
        int bx = cx + direction.axisX() * along;
        int bz = cz + direction.axisZ() * along;
        if (direction.axisX() != 0) {
            bz += lateral;
        } else {
            bx += lateral;
        }
        return RaidSpawnGroundUtil.findSpawnPosition(world, bx, bz, charterY);
    }
}
