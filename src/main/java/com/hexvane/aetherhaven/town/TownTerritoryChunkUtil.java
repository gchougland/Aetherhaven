package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityChunkUtil;
import com.hexvane.aetherhaven.tourist.TouristPortalRegistry;
import com.hexvane.aetherhaven.tourist.TouristPortalRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import javax.annotation.Nonnull;

/** Chunk load checks for town-scoped simulation (charter anchor, plot footprints). */
public final class TownTerritoryChunkUtil {
    private TownTerritoryChunkUtil() {}

    /**
     * True when the chunk column containing the town charter block is in memory — the town prefab is present in the
     * simulation, not only in {@link TownRecord} data.
     */
    public static boolean isCharterChunkLoaded(@Nonnull World world, @Nonnull TownRecord town) {
        return EntityChunkUtil.isBlockChunkInMemory(world, town.getCharterX(), town.getCharterZ());
    }

    /**
     * True when any chunk that may hold town NPCs is in memory: charter, a tourist portal, or a completed inn plot
     * sign. Used before destructive reconcile that assumes missing refs mean dead entities.
     */
    public static boolean isAnyTownNpcChunkLoaded(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        if (isCharterChunkLoaded(world, town)) {
            return true;
        }
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        List<TouristPortalRecord> portals = registry.recordsForTown(town.getTownId());
        for (TouristPortalRecord portal : portals) {
            if (world.getName().equals(portal.getWorldName())
                && EntityChunkUtil.isBlockChunkInMemory(
                    world,
                    portal.getBlockPosition().x,
                    portal.getBlockPosition().z
                )) {
                return true;
            }
        }
        for (PlotInstance plot : town.getPlotInstances()) {
            if (!PlotInstanceState.COMPLETE.name().equals(plot.getState())) {
                continue;
            }
            if (AetherhavenConstants.CONSTRUCTION_PLOT_INN.equals(plot.getConstructionId())
                && EntityChunkUtil.isBlockChunkInMemory(world, plot.getSignX(), plot.getSignZ())) {
                return true;
            }
        }
        return false;
    }
}
