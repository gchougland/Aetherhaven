package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/** True when every chunk column overlapping a plot footprint is loaded in memory. */
public final class PlotFootprintChunkUtil {
    private PlotFootprintChunkUtil() {}

    /**
     * Loads every chunk column overlapping {@code footprint}. Must run on the world thread. Needed before festival
     * prefab entity clears: corner props (statues) live in edge chunks that are often unloaded when the swap runs.
     */
    public static void ensureFootprintChunksLoaded(@Nonnull World world, @Nonnull PlotFootprintRecord footprint) {
        int minChunkX = Math.floorDiv(footprint.getMinX(), 16);
        int maxChunkX = Math.floorDiv(footprint.getMaxX(), 16);
        int minChunkZ = Math.floorDiv(footprint.getMinZ(), 16);
        int maxChunkZ = Math.floorDiv(footprint.getMaxZ(), 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                int bx = cx * 16 + 8;
                int bz = cz * 16 + 8;
                world.getChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
            }
        }
    }

    public static boolean isFootprintFullyLoaded(@Nonnull World world, @Nonnull PlotFootprintRecord footprint) {
        int minX = footprint.getMinX();
        int maxX = footprint.getMaxX();
        int minZ = footprint.getMinZ();
        int maxZ = footprint.getMaxZ();
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                int bx = cx * 16 + 8;
                int bz = cz * 16 + 8;
                if (world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(bx, bz)) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isPlotFullyLoaded(@Nonnull World world, @Nonnull PlotInstance plot) {
        return isFootprintFullyLoaded(world, plot.toFootprint());
    }

    /** True when every footprint chunk is loaded and simulating ({@code ChunkFlag.TICKING}). */
    public static boolean isPlotFootprintTicking(@Nonnull World world, @Nonnull PlotInstance plot) {
        PlotFootprintRecord footprint = plot.toFootprint();
        int minChunkX = Math.floorDiv(footprint.getMinX(), 16);
        int maxChunkX = Math.floorDiv(footprint.getMaxX(), 16);
        int minChunkZ = Math.floorDiv(footprint.getMinZ(), 16);
        int maxChunkZ = Math.floorDiv(footprint.getMaxZ(), 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                int bx = cx * 16 + 8;
                int bz = cz * 16 + 8;
                if (world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(bx, bz)) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True when the chunk containing the plot sign block is loaded (enough for blueprinting repair). */
    public static boolean isPlotSignChunkLoaded(@Nonnull World world, @Nonnull PlotInstance plot) {
        int x = plot.getSignX();
        int z = plot.getSignZ();
        return world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z)) != null;
    }

    /** Full footprint for completed builds; plot sign chunk only while still blueprinting. */
    public static boolean isPlotRepairAreaLoaded(@Nonnull World world, @Nonnull PlotInstance plot) {
        if (plot.getState() == PlotInstanceState.BLUEPRINTING) {
            return isPlotSignChunkLoaded(world, plot);
        }
        return isPlotFullyLoaded(world, plot);
    }
}
