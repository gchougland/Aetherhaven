package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityChunkUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Keeps {@link TouristPortalRegistry} aligned with configured portal blocks (multi-town travel). */
public final class TouristPortalRegistrySync {
    private TouristPortalRegistrySync() {}

    /** Refreshes registry entries for loaded tourist-portal plots before listing or standing detection. */
    public static void refreshTravelNetwork(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        boolean changed = false;
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            for (PlotInstance plot : town.getPlotInstances()) {
                if (plot.getState() != PlotInstanceState.COMPLETE) {
                    continue;
                }
                PlotFootprintRecord fp = plot.toFootprint();
                if (!footprintAnyChunkLoaded(world, fp)) {
                    continue;
                }
                changed |= scanPlotForPortals(world, plugin, registry, town, plot);
            }
        }
        if (changed) {
            TouristPortalPersistence.save(world, plugin, registry);
        }
    }

    @Nullable
    public static TouristPortalRecord resolveAtBlock(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3i basePos
    ) {
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        TouristPortalRecord record = registry.getAtBlock(basePos.x, basePos.y, basePos.z);
        if (record != null) {
            return record;
        }
        TouristPortalRecord materialized = materializeFromBlock(world, plugin, registry, basePos);
        if (materialized != null) {
            TouristPortalPersistence.save(world, plugin, registry);
        }
        return materialized;
    }

    private static boolean footprintAnyChunkLoaded(@Nonnull World world, @Nonnull PlotFootprintRecord fp) {
        if (EntityChunkUtil.isBlockChunkInMemory(world, fp.getMinX(), fp.getMinZ())) {
            return true;
        }
        if (EntityChunkUtil.isBlockChunkInMemory(world, fp.getMaxX(), fp.getMinZ())) {
            return true;
        }
        if (EntityChunkUtil.isBlockChunkInMemory(world, fp.getMinX(), fp.getMaxZ())) {
            return true;
        }
        return EntityChunkUtil.isBlockChunkInMemory(world, fp.getMaxX(), fp.getMaxZ());
    }

    private static boolean scanPlotForPortals(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TouristPortalRegistry registry,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot
    ) {
        PlotFootprintRecord fp = plot.toFootprint();
        boolean changed = false;
        UUID plotId = plot.getPlotId();
        for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
            for (int y = fp.getMinY(); y <= fp.getMaxY(); y++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    if (!TouristPortalBlockUtil.isPortalBaseBlock(world, x, y, z)) {
                        continue;
                    }
                    Vector3i pos = new Vector3i(x, y, z);
                    if (ensureRecordAtBlock(world, registry, town, plotId, pos)) {
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    @Nullable
    private static TouristPortalRecord materializeFromBlock(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TouristPortalRegistry registry,
        @Nonnull Vector3i basePos
    ) {
        if (!TouristPortalBlockUtil.isPortalBaseBlock(world, basePos.x, basePos.y, basePos.z)) {
            return null;
        }
        TouristPortalBlock block = TouristPortalBlockUtil.getBlockComponent(world, basePos);
        if (block == null || block.isTemplatePlacement()) {
            return null;
        }
        UUID townId;
        UUID plotId;
        try {
            townId = UUID.fromString(block.getTownId().trim());
            plotId = UUID.fromString(block.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !world.getName().equals(town.getWorldName())) {
            return null;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return null;
        }
        if (!ensureRecordAtBlock(world, registry, town, plotId, basePos)) {
            return registry.getAtBlock(basePos.x, basePos.y, basePos.z);
        }
        TouristPortalRecord created = registry.getAtBlock(basePos.x, basePos.y, basePos.z);
        if (created != null) {
            TouristPortalBlockUtil.syncConfigToBlock(world, basePos, created);
        }
        return created;
    }

    private static boolean ensureRecordAtBlock(
        @Nonnull World world,
        @Nonnull TouristPortalRegistry registry,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull Vector3i pos
    ) {
        TouristPortalRecord existing = registry.getAtBlock(pos.x, pos.y, pos.z);
        if (existing != null
            && town.getTownId().equals(existing.getTownId())
            && plotId.equals(existing.getPlotId())
            && world.getName().equals(existing.getWorldName())) {
            return false;
        }
        if (existing != null) {
            registry.remove(existing.getPortalId());
        }

        TouristPortalBlock blockComp = TouristPortalBlockUtil.getBlockComponent(world, pos);
        UUID portalId =
            registry.allocatePortalId(pos, TouristPortalIdAllocation.preferredIdFromBlock(blockComp));

        TouristPortalRecord record = new TouristPortalRecord();
        record.setPortalId(portalId);
        record.setWorldName(world.getName());
        record.setBlockPosition(pos);
        record.setTownId(town.getTownId());
        record.setPlotId(plotId);
        registry.put(record);
        TouristPortalBlockUtil.syncConfigToBlock(world, pos, record);
        TouristPortalVisualService.applyColorVariantAtBlock(world, pos, town);
        return true;
    }
}
