package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Activates prefab embedded tourist portal blocks when a plot build completes. */
public final class TouristPortalExtractor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TouristPortalExtractor() {}

    public static void registerForCompletedBuild(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull PlotInstance plot
    ) {
        PlotFootprintRecord fp = plot.toFootprint();
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        int activated = 0;
        Set<UUID> reboundPortalIds = new HashSet<>();

        for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
            for (int y = fp.getMinY(); y <= fp.getMaxY(); y++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    // Multi-block portals occupy a filler voxel with the same block type; only the base cell is a portal.
                    if (!TouristPortalBlockUtil.isPortalBaseBlock(world, x, y, z)) {
                        continue;
                    }
                    Vector3i pos = new Vector3i(x, y, z);
                    TouristPortalBlock blockComp = TouristPortalBlockUtil.getBlockComponent(world, pos);
                    if (blockComp != null && blockComp.isConfigured() && !blockComp.isTemplatePlacement()) {
                        TouristPortalRecord bound = registry.getAtBlock(x, y, z);
                        if (bound != null
                            && town.getTownId().equals(bound.getTownId())
                            && plotId.equals(bound.getPlotId())) {
                            TouristPortalVisualService.applyColorVariantAtBlock(world, pos, town);
                            continue;
                        }
                    }
                    activated +=
                        activatePortal(world, plugin, registry, town, plotId, plot, def, pos, blockComp, reboundPortalIds);
                }
            }
        }

        TouristPortalPlotRelocation.finishPlotMove(world, plugin, store, town, plotId, reboundPortalIds);

        if (activated > 0) {
            TouristPortalPersistence.save(world, plugin, registry);
            LOGGER.atInfo().log("Activated %s tourist portal(s) for plot %s in town %s", activated, plotId, town.getTownId());
        }
    }

    private static int activatePortal(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TouristPortalRegistry registry,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull PlotInstance plot,
        @Nullable ConstructionDefinition def,
        @Nonnull Vector3i pos,
        @Nullable TouristPortalBlock blockComp,
        @Nonnull Set<UUID> reboundPortalIds
    ) {
        TouristPortalRecord existing = registry.getAtBlock(pos.x, pos.y, pos.z);
        if (existing != null) {
            registry.remove(existing.getPortalId());
        }

        TouristPortalRecord record =
            def != null ? TouristPortalPlotRelocation.takeDetached(plotId, pos, plot, def) : null;
        if (record == null) {
            record = new TouristPortalRecord();
            record.setPortalId(
                registry.allocatePortalId(pos, TouristPortalIdAllocation.preferredIdFromBlock(blockComp))
            );
        } else {
            // Move rebind keeps the prior id when free; mint if another portal already owns it.
            record.setPortalId(registry.allocatePortalId(pos, record.getPortalId().toString()));
        }
        record.setWorldName(world.getName());
        record.setBlockPosition(pos);
        record.setTownId(town.getTownId());
        record.setPlotId(plotId);

        registry.put(record);
        TouristPortalBlockUtil.syncConfigToBlock(world, pos, record);
        TouristPortalVisualService.applyColorVariantAtBlock(world, pos, town);
        reboundPortalIds.add(record.getPortalId());
        return 1;
    }
}
