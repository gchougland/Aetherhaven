package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

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
 * Resolves plot sign placement independently from the building preview anchor: sign XZ sits at the horizontal center of
 * the prefab footprint (from the preview building pose), and sign Y is terrain-snapped under that column.
 */
public final class PlotSignGrounding {
    private static final int MAX_RAY_DOWN = 512;
    /** Scan above the preview height so a lowered anchor still finds the water surface. */
    private static final int FLUID_SCAN_UP = 48;

    private PlotSignGrounding() {}

    /**
     * @param previewSignAnchor session anchor from the placement UI (drives building prefab origin via
     *     {@link ConstructionDefinition#resolvePrefabAnchorWorld}; preview Y is only used when raycast fails)
     * @return world cell for the plot sign block (footprint center XZ, terrain-snapped Y)
     */
    @Nonnull
    public static Vector3i resolveSignCell(
        @Nonnull World world,
        @Nonnull Vector3i previewSignAnchor,
        @Nonnull ConstructionDefinition def,
        @Nonnull Rotation prefabYaw,
        @Nonnull IPrefabBuffer buf
    ) {
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(previewSignAnchor, prefabYaw);
        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, prefabYaw, buf, def);
        int sx = fp.horizontalCenterX();
        int sz = fp.horizontalCenterZ();
        int startY = Math.max(previewSignAnchor.y, fp.getMaxY());
        int signY = resolveSignY(world, sx, sz, startY, previewSignAnchor.y);
        return new Vector3i(sx, signY, sz);
    }

    /**
     * Terrain-snaps sign Y at an existing column without changing XZ (wall preview height adjust).
     */
    public static int resolveSignYAtColumn(
        @Nonnull World world,
        int blockX,
        int blockZ,
        int startY,
        int fallbackY
    ) {
        return resolveSignY(world, blockX, blockZ, startY, fallbackY);
    }

    private static int resolveSignY(
        @Nonnull World world,
        int sx,
        int sz,
        int startY,
        int fallbackY
    ) {
        Integer support = PathGrounding.findSupportY(world, sx, sz, startY, MAX_RAY_DOWN, 1);
        Integer fluidSurface = findFluidSurfaceY(world, sx, sz, startY, MAX_RAY_DOWN, FLUID_SCAN_UP);
        int signY;
        if (fluidSurface != null && (support == null || fluidSurface > support)) {
            signY = Math.min(318, fluidSurface + 1);
        } else if (support != null) {
            signY = Math.min(318, support + 1);
        } else {
            signY = fallbackY;
        }
        return Math.max(1, Math.min(318, signY));
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
        return ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z)) != null;
    }
}
