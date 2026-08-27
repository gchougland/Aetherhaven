package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Activates prefab-embedded shop spots when a plot build completes. */
public final class ShopSpotExtractor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ShopSpotExtractor() {}

    public static void registerForCompletedBuild(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull PlotInstance plot
    ) {
        PlotFootprintRecord fp = plot.toFootprint();
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        int activated = 0;
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        long epochDay = wtr != null ? wtr.getGameDateTime().toLocalDate().toEpochDay() : Long.MIN_VALUE;

        for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
            for (int y = fp.getMinY(); y <= fp.getMaxY(); y++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    if (!AetherhavenConstants.SHOP_SPOT_BLOCK_TYPE_ID.equals(ChunkSectionBlockUtil.blockType(world, x, y, z).getId())) {
                        continue;
                    }
                    Vector3i pos = new Vector3i(x, y, z);
                    ShopSpotBlock blockComp = ShopSpotBlockUtil.getBlockComponent(world, pos);
                    if (blockComp != null && blockComp.isConfigured() && !blockComp.isTemplatePlacement()) {
                        ShopSpotRecord bound = registry.getAtBlock(x, y, z);
                        if (bound != null
                            && town.getTownId().equals(bound.getTownId())
                            && plotId.equals(bound.getPlotId())) {
                            continue;
                        }
                    }
                    activated +=
                        activateSpot(
                            world,
                            plugin,
                            store,
                            registry,
                            town,
                            plotId,
                            plot,
                            def,
                            pos,
                            blockComp,
                            epochDay
                        );
                }
            }
        }

        ShopSpotPlotRelocation.finishPlotMove(plotId);

        if (activated > 0) {
            ShopSpotPersistence.save(world, plugin, registry);
            LOGGER.atInfo().log("Activated %s shop spot(s) for plot %s in town %s", activated, plotId, town.getTownId());
        }
    }

    private static int activateSpot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull PlotInstance plot,
        @Nullable ConstructionDefinition def,
        @Nonnull Vector3i pos,
        @Nullable ShopSpotBlock blockComp,
        long epochDay
    ) {
        ShopSpotRecord existing = registry.getAtBlock(pos.x, pos.y, pos.z);
        if (existing != null) {
            ShopSpotDisplayService.removeDisplay(world, store, plugin, registry, existing);
            registry.remove(existing.getSpotId());
        }

        ShopSpotRecord record =
            def != null ? ShopSpotPlotRelocation.takeDetached(plotId, pos, plot, def) : null;
        if (record == null) {
            record = new ShopSpotRecord();
            record.setSpotId(UUID.randomUUID());
            record.setDisplayYawRadians(ShopSpotDisplayRotation.yawFromBlockAt(world, pos));
            if (blockComp != null && blockComp.isConfigured()) {
                blockComp.applyToRecord(record);
            }
            if (!record.isPlayerControlled()) {
                ShopSpotDailyRerollService.initialRollIfNeeded(record, plugin, epochDay);
            }
        } else {
            record.setDisplayEntityUuid(null);
            record.setListingDisplaySignature(null);
            record.setDisplayYawRadians(ShopSpotDisplayRotation.yawFromBlockAt(world, pos));
        }
        record.setWorldName(world.getName());
        record.setBlockPosition(pos);
        record.setTownId(town.getTownId());
        record.setPlotId(plotId);

        registry.put(record);
        ShopSpotBlockUtil.syncConfigToBlock(world, pos, record);
        ShopSpotDisplayService.syncDisplay(world, store, plugin, registry, record, town);
        return 1;
    }
}
