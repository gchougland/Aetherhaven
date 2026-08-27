package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PlotCreatorLocalCoords {
    private PlotCreatorLocalCoords() {}

    /**
     * Prefab-local coordinates relative to exported prefab buffer (0,0,0), which is anchored at the plot sign block
     * ({@link PlotCreatorDraft#getPlotAnchor()}).
     */
    @Nonnull
    public static Vector3i toWorldBlock(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        Vector3i sign = draft.getPlotAnchor();
        if (sign != null) {
            return new Vector3i(sign.x + local[0], sign.y + local[1], sign.z + local[2]);
        }
        Vector3i origin = draft.getPrefabOriginMin();
        if (origin == null) {
            origin = draft.boundsMin();
        }
        return new Vector3i(origin.x + local[0], origin.y + local[1], origin.z + local[2]);
    }

    @Nonnull
    public static int[] toLocal(@Nonnull PlotCreatorDraft draft, @Nonnull Vector3i worldPos) {
        Vector3i sign = draft.getPlotAnchor();
        if (sign != null) {
            return new int[] {worldPos.x - sign.x, worldPos.y - sign.y, worldPos.z - sign.z};
        }
        Vector3i origin = draft.getPrefabOriginMin();
        if (origin == null) {
            origin = draft.boundsMin();
        }
        return new int[] {worldPos.x - origin.x, worldPos.y - origin.y, worldPos.z - origin.z};
    }

    /**
     * Offset from the logical sign cell to prefab buffer origin. Plot creator prefabs relativize to the sign voxel, so
     * this matches {@link com.hexvane.aetherhaven.construction.ConstructionDefinition#resolvePrefabAnchorWorld}.
     */
    public static void recomputeAnchorOffset(@Nonnull PlotCreatorDraft draft) {
        if (draft.getPlotAnchor() == null) {
            return;
        }
        draft.setPlotAnchorOffset(
            new int[] {
                0,
                AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR,
                0
            }
        );
    }

    public static boolean isInsideBounds(@Nonnull PlotCreatorDraft draft, @Nonnull Vector3i worldPos) {
        return draft.isInsideBounds(worldPos);
    }

    /**
     * In-memory block id, or {@code null} when the column is not loaded. Must not use {@link World#getBlockType}:
     * {@code getChunk()} waits for unloaded columns, drains {@code world.execute} work inside {@code Store.tick},
     * and crashes with "Store is currently processing!".
     */
    @Nullable
    public static String blockTypeAt(@Nonnull World world, @Nonnull Vector3i pos) {
        if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, pos.x, pos.z) == null) {
            return null;
        }
        BlockType bt = ChunkSectionBlockUtil.blockType(world, pos.x, pos.y, pos.z);
        return bt != null ? bt.getId() : null;
    }
}
