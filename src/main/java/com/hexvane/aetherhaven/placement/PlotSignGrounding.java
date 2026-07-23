package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.pathtool.PathGrounding;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Keeps plot sign XZ from the placement preview, snaps sign Y to terrain under the prefab footprint, prefers the block
 * above open water when the column is flooded, and falls back to the preview sign Y when no surface is found (e.g. fully
 * underground).
 */
public final class PlotSignGrounding {
    private static final int MAX_RAY_DOWN = 512;
    /** Scan above the preview height so a lowered anchor still finds the water surface. */
    private static final int FLUID_SCAN_UP = 48;

    private PlotSignGrounding() {}

    /**
     * @param anchorPreview session anchor (XZ and yaw from preview; preview Y is only used when raycast fails)
     * @return world cell for the plot sign block (one block above solid ground or water surface when found)
     */
    @Nonnull
    public static Vector3i resolveSignCell(
        @Nonnull World world,
        @Nonnull Vector3i anchorPreview,
        @Nonnull ConstructionDefinition def,
        @Nonnull Rotation prefabYaw,
        @Nonnull IPrefabBuffer buf
    ) {
        int sx = anchorPreview.x;
        int sz = anchorPreview.z;
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(anchorPreview, prefabYaw);
        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, prefabYaw, buf);
        int startY = Math.max(anchorPreview.y, fp.getMaxY());
        Integer support = PathGrounding.findSupportY(world, sx, sz, startY, MAX_RAY_DOWN, 1);
        Integer fluidSurface = findFluidSurfaceY(world, sx, sz, startY, MAX_RAY_DOWN, FLUID_SCAN_UP);
        int signY;
        if (fluidSurface != null && (support == null || fluidSurface > support)) {
            signY = Math.min(318, fluidSurface + 1);
        } else if (support != null) {
            signY = Math.min(318, support + 1);
        } else {
            signY = anchorPreview.y;
        }
        signY = Math.max(1, Math.min(318, signY));
        return new Vector3i(sx, signY, sz);
    }

    /**
     * Topmost fluid cell in the column, scanning from {@code startY + maxUp} downward. Returns the first fluid hit,
     * which is the water surface for a continuous column (ocean, lakes).
     */
    @Nullable
    private static Integer findFluidSurfaceY(
        @Nonnull World world,
        int blockX,
        int blockZ,
        int startY,
        int maxDown,
        int maxUp
    ) {
        int yTop = Math.min(319, startY + maxUp);
        int yEnd = Math.max(1, startY - maxDown);
        for (int y = yTop; y >= yEnd; y--) {
            if (!isColumnLoaded(world, blockX, y, blockZ)) {
                return null;
            }
            if (hasFluid(world, blockX, y, blockZ)) {
                return y;
            }
        }
        return null;
    }

    private static boolean hasFluid(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return false;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return false;
        }
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return false;
        }
        FluidSection fluidSection = chunkStore.getStore().getComponent(sectionRef, FluidSection.getComponentType());
        return fluidSection != null && fluidSection.getFluidId(x, y, z) != 0;
    }

    private static boolean isColumnLoaded(@Nonnull World world, int x, int y, int z) {
        return world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z)) != null;
    }
}
