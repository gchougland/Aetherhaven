package com.hexvane.aetherhaven.startertown;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Deterministic candidate packing and terrain preflight for starter towns. */
public final class StarterTownLayoutPlanner {
    private static final int BUILDING_SETBACK = 4;
    private static final int MAX_TERRAIN_SPREAD = 8;

    private StarterTownLayoutPlanner() {}

    @Nonnull
    public static StarterTownLayoutPlan plan(
        @Nonnull World world,
        @Nonnull TownManager townManager,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull List<String> constructionIds,
        @Nonnull Vector3i origin,
        @Nonnull Rotation facing,
        @Nonnull String requestedLayout,
        long seed
    ) throws PlanException {
        if (constructionIds.isEmpty()) {
            throw new PlanException("The selected preset has no available building definitions.");
        }
        if ("line".equalsIgnoreCase(requestedLayout)) {
            return planLine(world, townManager, town, catalog, constructionIds, origin, facing, seed);
        }
        try {
            return planGenerated(world, townManager, town, catalog, constructionIds, origin, seed);
        } catch (PlanException generatedFailure) {
            try {
                return planLine(world, townManager, town, catalog, constructionIds, origin, facing, seed);
            } catch (PlanException lineFailure) {
                throw new PlanException(
                    generatedFailure.getMessage() + " Line fallback also failed: " + lineFailure.getMessage()
                );
            }
        }
    }

    @Nonnull
    private static StarterTownLayoutPlan planLine(
        @Nonnull World world,
        @Nonnull TownManager townManager,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull List<String> ids,
        @Nonnull Vector3i origin,
        @Nonnull Rotation facing,
        long seed
    ) throws PlanException {
        List<StarterTownLayoutPlan.Building> placed = new ArrayList<>();
        int[] direction = forward(facing);
        int distance = 18;
        for (String id : ids) {
            StarterTownLayoutPlan.Building accepted = null;
            for (int retry = 0; retry < 64 && accepted == null; retry++) {
                int d = distance + retry * 4;
                accepted = tryCandidate(
                    world,
                    townManager,
                    town,
                    catalog,
                    id,
                    origin.x + direction[0] * d,
                    origin.z + direction[1] * d,
                    facing,
                    placed
                );
            }
            if (accepted == null) {
                throw new PlanException("No valid line position found for " + id + ".");
            }
            placed.add(accepted);
            distance += StarterTownLayoutMath.lineAdvance(accepted.footprint());
        }
        return new StarterTownLayoutPlan("line", seed, placed);
    }

    @Nonnull
    private static StarterTownLayoutPlan planGenerated(
        @Nonnull World world,
        @Nonnull TownManager townManager,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull List<String> ids,
        @Nonnull Vector3i origin,
        long seed
    ) throws PlanException {
        List<StarterTownLayoutPlan.Building> placed = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            StarterTownLayoutPlan.Building accepted = null;
            for (int retry = 0; retry < 96 && accepted == null; retry++) {
                StarterTownLayoutMath.Candidate candidate =
                    StarterTownLayoutMath.generatedCandidate(seed, index, retry, origin.x, origin.z);
                int x = candidate.x();
                int z = candidate.z();
                Rotation yaw = facingToward(x, z, origin.x, origin.z);
                accepted = tryCandidate(world, townManager, town, catalog, id, x, z, yaw, placed);
            }
            if (accepted == null) {
                throw new PlanException("Procedural packing failed for " + id + ".");
            }
            placed.add(accepted);
        }
        return new StarterTownLayoutPlan("generated", seed, placed);
    }

    private static StarterTownLayoutPlan.Building tryCandidate(
        @Nonnull World world,
        @Nonnull TownManager townManager,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String constructionId,
        int anchorX,
        int anchorZ,
        @Nonnull Rotation yaw,
        @Nonnull List<StarterTownLayoutPlan.Building> placed
    ) {
        ConstructionDefinition def = catalog.get(constructionId);
        if (def == null || def.getPrefabPath() == null || def.getPrefabPath().isBlank()) {
            return null;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        if (buffer == null) {
            return null;
        }
        PlotFootprintRecord relative =
            PlotFootprintUtil.computeFootprint(new Vector3i(anchorX, 0, anchorZ), yaw, buffer, def);
        List<Integer> heights = new ArrayList<>();
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        for (int x = relative.getMinX(); x <= relative.getMaxX(); x++) {
            for (int z = relative.getMinZ(); z <= relative.getMaxZ(); z++) {
                if (ChunkSectionBlockUtil.loadBlockChunk(world, x, z) == null) {
                    return null;
                }
                int columnHeight = ChunkSectionBlockUtil.columnHeight(world, x, z);
                if (columnHeight < ChunkUtil.MIN_Y) {
                    return null;
                }
                int h = columnHeight + 1;
                heights.add(h);
                minHeight = Math.min(minHeight, h);
                maxHeight = Math.max(maxHeight, h);
            }
        }
        if (heights.isEmpty() || maxHeight - minHeight > MAX_TERRAIN_SPREAD) {
            return null;
        }
        int groundY;
        try {
            groundY = StarterTownLayoutMath.representativeGround(heights, MAX_TERRAIN_SPREAD);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int anchorY = groundY - relative.getMinY();
        Vector3i anchor = new Vector3i(anchorX, anchorY, anchorZ);
        PlotFootprintRecord footprint = PlotFootprintUtil.computeFootprint(anchor, yaw, buffer, def);
        if (!insideTerritory(townManager, town, footprint)
            || town.findOverlappingNonWallPlot(footprint, null) != null
            || overlapsCharter(town, footprint)
            || overlapsPlanned(footprint, placed)) {
            return null;
        }
        int centerX = (footprint.getMinX() + footprint.getMaxX()) / 2;
        int centerZ = (footprint.getMinZ() + footprint.getMaxZ()) / 2;
        int dx = town.getCharterX() - centerX;
        int dz = town.getCharterZ() - centerZ;
        Vector3i roadPoint;
        if (Math.abs(dx) > Math.abs(dz)) {
            roadPoint = new Vector3i(dx >= 0 ? footprint.getMaxX() + 2 : footprint.getMinX() - 2, groundY, centerZ);
        } else {
            roadPoint = new Vector3i(centerX, groundY, dz >= 0 ? footprint.getMaxZ() + 2 : footprint.getMinZ() - 2);
        }
        return new StarterTownLayoutPlan.Building(constructionId, anchor, yaw, footprint, roadPoint);
    }

    private static boolean insideTerritory(
        @Nonnull TownManager townManager,
        @Nonnull TownRecord town,
        @Nonnull PlotFootprintRecord footprint
    ) {
        return townManager.isInsideTerritory(town, footprint.getMinX(), footprint.getMinZ())
            && townManager.isInsideTerritory(town, footprint.getMinX(), footprint.getMaxZ())
            && townManager.isInsideTerritory(town, footprint.getMaxX(), footprint.getMinZ())
            && townManager.isInsideTerritory(town, footprint.getMaxX(), footprint.getMaxZ());
    }

    private static boolean overlapsCharter(
        @Nonnull TownRecord town,
        @Nonnull PlotFootprintRecord footprint
    ) {
        return town.getCharterX() >= footprint.getMinX() - BUILDING_SETBACK
            && town.getCharterX() <= footprint.getMaxX() + BUILDING_SETBACK
            && town.getCharterZ() >= footprint.getMinZ() - BUILDING_SETBACK
            && town.getCharterZ() <= footprint.getMaxZ() + BUILDING_SETBACK;
    }

    private static boolean overlapsPlanned(
        @Nonnull PlotFootprintRecord footprint,
        @Nonnull List<StarterTownLayoutPlan.Building> placed
    ) {
        for (StarterTownLayoutPlan.Building other : placed) {
            PlotFootprintRecord b = other.footprint();
            if (StarterTownLayoutMath.overlapsWithSetback(footprint, b, BUILDING_SETBACK)) {
                return true;
            }
        }
        return false;
    }

    private static int[] forward(@Nonnull Rotation yaw) {
        return switch (yaw) {
            case Ninety -> new int[] {1, 0};
            case OneEighty -> new int[] {0, -1};
            case TwoSeventy -> new int[] {-1, 0};
            default -> new int[] {0, 1};
        };
    }

    @Nonnull
    private static Rotation facingToward(int x, int z, int targetX, int targetZ) {
        int dx = targetX - x;
        int dz = targetZ - z;
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? Rotation.Ninety : Rotation.TwoSeventy;
        }
        return dz >= 0 ? Rotation.None : Rotation.OneEighty;
    }

    public static final class PlanException extends Exception {
        public PlanException(@Nonnull String message) {
            super(message);
        }
    }
}
