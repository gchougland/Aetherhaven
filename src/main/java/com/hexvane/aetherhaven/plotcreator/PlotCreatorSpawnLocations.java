package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.guild.marker.GuildHallAdventurerSpawnPositions;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** World / prefab-local positions for plot creator spawn substeps (adventurer, visitor, etc.). */
public final class PlotCreatorSpawnLocations {
    /** Blocks above the surface the player clicked (standing cell). */
    public static final int STAND_BLOCK_ABOVE_CLICK = 1;

    private PlotCreatorSpawnLocations() {}

    /** Prefab-local coords for the block one above {@code clickedBlock} (where an NPC stands). */
    @Nonnull
    public static int[] standLocalAboveClick(@Nonnull PlotCreatorDraft draft, @Nonnull Vector3i clickedBlock) {
        return PlotCreatorLocalCoords.toLocal(
            draft,
            new Vector3i(clickedBlock.x, clickedBlock.y + STAND_BLOCK_ABOVE_CLICK, clickedBlock.z)
        );
    }

    /** Center of the standing cell for debug overlays and removal proximity. */
    @Nonnull
    /** Matches {@link com.hexvane.aetherhaven.guild.marker.GuildHallAdventurerSpawnPositions} / innkeeper spawn Y. */
    public static Vector3d standCenterWorld(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        Vector3i block = PlotCreatorLocalCoords.toWorldBlock(draft, local);
        return new Vector3d(block.x + 0.5, block.y, block.z + 0.5);
    }

    /**
     * Removes an adventurer spawn when {@code clickedBlock} is the stand cell or the block below it, or within
     * {@code maxDist} of a saved stand center.
     */
    public static boolean tryRemoveAdventurerNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull World world,
        @Nonnull Vector3i clickedBlock,
        double maxDist
    ) {
        if (draft.getPlotAnchor() == null && draft.getPrefabOriginMin() == null) {
            return false;
        }
        double maxDistSq = maxDist * maxDist;
        var locals = draft.getAdventurerSpawns();
        int bestIdx = -1;
        double bestDistSq = maxDistSq;
        for (int i = 0; i < locals.size(); i++) {
            PlotCreatorAdventurerSpawnEntry entry = locals.get(i);
            Vector3i standBlock =
                PlotCreatorPrefabCoords.standWorldBlockFromPrefabLocal(
                    draft,
                    entry.getLocalX(),
                    entry.getLocalY(),
                    entry.getLocalZ()
                );
            if (PlotCreatorSpotPlacement.matchesStandSpawnClick(world, clickedBlock, standBlock)) {
                locals.remove(i);
                return true;
            }
            Vector3d center = standCenterWorld(draft, entry.localArray());
            double dx = center.x - (clickedBlock.x + 0.5);
            double dy = center.y - (clickedBlock.y + 0.5);
            double dz = center.z - (clickedBlock.z + 0.5);
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= bestDistSq) {
                bestDistSq = d2;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0) {
            locals.remove(bestIdx);
            return true;
        }
        return false;
    }

    /**
     * Removes a visitor spawn when {@code clickedBlock} is the stand cell or within {@code maxDist} of a saved stand
     * center.
     */
    public static boolean tryRemoveVisitorNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull World world,
        @Nonnull Vector3i clickedBlock,
        double maxDist
    ) {
        if (draft.getPlotAnchor() == null && draft.getPrefabOriginMin() == null) {
            return false;
        }
        double maxDistSq = maxDist * maxDist;
        var locals = draft.getVisitorSpawnLocals();
        int bestIdx = -1;
        double bestDistSq = maxDistSq;
        for (int i = 0; i < locals.size(); i++) {
            int[] local = locals.get(i);
            Vector3i standBlock = PlotCreatorLocalCoords.toWorldBlock(draft, local);
            if (PlotCreatorSpotPlacement.matchesStandSpawnClick(world, clickedBlock, standBlock)) {
                locals.remove(i);
                return true;
            }
            Vector3d center = standCenterWorld(draft, local);
            double dx = center.x - (clickedBlock.x + 0.5);
            double dy = center.y - (clickedBlock.y + 0.5);
            double dz = center.z - (clickedBlock.z + 0.5);
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= bestDistSq) {
                bestDistSq = d2;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0) {
            locals.remove(bestIdx);
            return true;
        }
        return false;
    }

    private static boolean sameBlock(@Nonnull Vector3i a, @Nonnull Vector3i b) {
        return a.x == b.x && a.y == b.y && a.z == b.z;
    }
}
