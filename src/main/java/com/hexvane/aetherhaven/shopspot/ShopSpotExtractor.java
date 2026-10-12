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
import java.util.ArrayList;
import java.util.List;
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

        // A rebuilt or moved building can come back without one of its stalls. Whatever a player was selling there
        // is handed back (or held for them), never dropped with the row.
        List<ShopSpotRecord> gone =
            new ArrayList<>(ShopSpotRebuild.orphans(registry, plotId, r -> stallStillStands(world, r)));
        gone.addAll(ShopSpotPlotRelocation.drainDetached(plotId));
        for (ShopSpotRecord record : gone) {
            ShopSpotDisplayService.removeDisplayImmediate(world, store, plugin, registry, record);
            ShopSpotListingReturns.retire(world, store, registry, record);
        }

        if (activated > 0 || !gone.isEmpty()) {
            ShopSpotPersistence.save(world, plugin, registry);
            LOGGER.atInfo().log(
                "Activated %s shop spot(s) and retired %s for plot %s in town %s",
                activated,
                gone.size(),
                plotId,
                town.getTownId()
            );
        }
    }

    /** Unloaded terrain counts as standing: only a block we can read may retire a stall. */
    private static boolean stallStillStands(@Nonnull World world, @Nonnull ShopSpotRecord record) {
        if (!ChunkSectionBlockUtil.isChunkInMemory(world, record.getBlockX(), record.getBlockZ())) {
            return true;
        }
        return ShopSpotBlockUtil.isShopSpotBlock(
            ChunkSectionBlockUtil.blockType(world, record.getBlockX(), record.getBlockY(), record.getBlockZ())
        );
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
        boolean templateConfigured = blockComp != null && blockComp.isConfigured();
        // Same building pasted again where it stood: the saved row, and anything listed on it, stays.
        boolean keep =
            ShopSpotRebuild.keepsRow(
                existing,
                town.getTownId(),
                plotId,
                templateConfigured,
                templateConfigured && blockComp.isPlayerControlled()
            );
        if (existing != null && !keep) {
            ShopSpotDisplayService.removeDisplayImmediate(world, store, plugin, registry, existing);
            ShopSpotListingReturns.retire(world, store, registry, existing);
        }

        ShopSpotRecord record = keep ? existing : null;
        if (record != null) {
            if (templateConfigured && !record.isPlayerControlled()) {
                record.setLootTableId(blockComp.getLootTableId().trim());
            }
            record.setDisplayYawRadians(ShopSpotDisplayRotation.yawFromBlockAt(world, pos));
        } else if (def != null) {
            record = ShopSpotPlotRelocation.takeDetached(plotId, pos, plot, def);
            if (record != null) {
                record.setDisplayEntityUuid(null);
                record.setListingDisplaySignature(null);
                record.setDisplayYawRadians(ShopSpotDisplayRotation.yawFromBlockAt(world, pos));
            }
        }
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
