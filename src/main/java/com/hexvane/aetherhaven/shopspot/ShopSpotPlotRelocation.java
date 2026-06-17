package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Detaches shop spots when a plot footprint is cleared for relocation or removal. */
public final class ShopSpotPlotRelocation {
    private static final Map<UUID, Map<Long, ShopSpotRecord>> PENDING_BY_PLOT = new ConcurrentHashMap<>();

    private ShopSpotPlotRelocation() {}

    /**
     * Removes shop spot displays and registry rows before a plot move. Listing data is keyed by prefab-local cell so
     * {@link ShopSpotExtractor} can rebind stalls after the building is pasted at the new pose.
     */
    public static void beginPlotMove(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull UUID plotId,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def
    ) {
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        Rotation yaw = plot.resolvePrefabYaw();
        Map<Long, ShopSpotRecord> pending = new HashMap<>();
        for (ShopSpotRecord record : registry.listForPlot(plotId)) {
            ShopSpotDisplayService.removeDisplayImmediate(world, store, plugin, registry, record);
            Vector3i local = prefabLocalOffset(anchor, yaw, record.getBlockPosition());
            pending.put(localKey(local.x, local.y, local.z), copyForRelocation(record));
            registry.remove(record.getSpotId());
        }
        if (!pending.isEmpty()) {
            PENDING_BY_PLOT.put(plotId, pending);
            ShopSpotPersistence.save(world, plugin, registry);
        }
    }

    /** Removes shop spot displays and registry rows when a plot is destroyed (no rebind). */
    public static void clearPlotSpots(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull UUID plotId
    ) {
        PENDING_BY_PLOT.remove(plotId);
        List<ShopSpotRecord> records = new ArrayList<>(registry.listForPlot(plotId));
        if (records.isEmpty()) {
            return;
        }
        for (ShopSpotRecord record : records) {
            ShopSpotDisplayService.removeDisplayImmediate(world, store, plugin, registry, record);
            registry.remove(record.getSpotId());
        }
        ShopSpotPersistence.save(world, plugin, registry);
    }

    @Nullable
    public static ShopSpotRecord takeDetached(
        @Nonnull UUID plotId,
        @Nonnull Vector3i worldPos,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def
    ) {
        Map<Long, ShopSpotRecord> pending = PENDING_BY_PLOT.get(plotId);
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        Rotation yaw = plot.resolvePrefabYaw();
        Vector3i local = prefabLocalOffset(anchor, yaw, worldPos);
        ShopSpotRecord detached = pending.remove(localKey(local.x, local.y, local.z));
        if (pending.isEmpty()) {
            PENDING_BY_PLOT.remove(plotId);
        }
        return detached;
    }

    public static void finishPlotMove(@Nonnull UUID plotId) {
        PENDING_BY_PLOT.remove(plotId);
    }

    @Nonnull
    private static Vector3i prefabLocalOffset(@Nonnull Vector3i anchor, @Nonnull Rotation yaw, @Nonnull Vector3i worldPos) {
        return PrefabLocalOffset.inverseRotateWorldDelta(
            yaw,
            worldPos.x - anchor.x,
            worldPos.y - anchor.y,
            worldPos.z - anchor.z
        );
    }

    @Nonnull
    private static ShopSpotRecord copyForRelocation(@Nonnull ShopSpotRecord src) {
        ShopSpotRecord copy = new ShopSpotRecord();
        copy.setSpotId(src.getSpotId());
        copy.setWorldName(src.getWorldName());
        copy.setBlockPosition(src.getBlockPosition());
        copy.setTownId(src.getTownId());
        copy.setPlotId(src.getPlotId());
        copy.setDisplayYawRadians(src.getDisplayYawRadians());
        copy.setPlayerControlled(src.isPlayerControlled());
        copy.setLootTableId(src.getLootTableId());
        copy.setItemId(src.getItemId());
        copy.setStock(src.getStock());
        copy.setStockEpochDay(src.getStockEpochDay());
        copy.setJewelryMetaJson(src.getJewelryMetaJson());
        copy.setSellerUuid(src.getSellerUuid());
        copy.setSellerName(src.getSellerName());
        return copy;
    }

    private static long localKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) y & 0xFFFL) << 26) | (((long) z & 0x3FFFFFFL) << 38);
    }
}
