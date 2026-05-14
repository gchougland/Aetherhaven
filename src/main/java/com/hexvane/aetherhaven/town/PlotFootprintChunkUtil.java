package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/** True when every chunk column overlapping a plot footprint is loaded in memory. */
public final class PlotFootprintChunkUtil {
    private PlotFootprintChunkUtil() {}

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
}
