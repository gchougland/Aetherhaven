package com.hexvane.aetherhaven.shopspot;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ShopSpotRegistry {
    private final World world;
    private final Map<UUID, ShopSpotRecord> byId = new HashMap<>();
    private final Map<Long, UUID> byBlockKey = new HashMap<>();

    public ShopSpotRegistry(@Nonnull World world) {
        this.world = world;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    public void replaceAll(@Nonnull List<ShopSpotRecord> records) {
        byId.clear();
        byBlockKey.clear();
        for (ShopSpotRecord r : records) {
            if (!world.getName().equals(r.getWorldName())) {
                continue;
            }
            put(r);
        }
    }

    public void put(@Nonnull ShopSpotRecord record) {
        byId.put(record.getSpotId(), record);
        byBlockKey.put(blockKey(record.getBlockX(), record.getBlockY(), record.getBlockZ()), record.getSpotId());
    }

    public void remove(@Nonnull UUID spotId) {
        ShopSpotRecord r = byId.remove(spotId);
        if (r != null) {
            byBlockKey.remove(blockKey(r.getBlockX(), r.getBlockY(), r.getBlockZ()));
        }
    }

    @Nullable
    public ShopSpotRecord get(@Nonnull UUID spotId) {
        return byId.get(spotId);
    }

    @Nullable
    public ShopSpotRecord getAtBlock(int x, int y, int z) {
        UUID id = byBlockKey.get(blockKey(x, y, z));
        return id != null ? byId.get(id) : null;
    }

    @Nonnull
    public List<ShopSpotRecord> allRecords() {
        return new ArrayList<>(byId.values());
    }

    @Nonnull
    public List<ShopSpotRecord> listForPlot(@Nonnull UUID plotId) {
        List<ShopSpotRecord> out = new ArrayList<>();
        for (ShopSpotRecord r : byId.values()) {
            if (plotId.equals(r.getPlotId())) {
                out.add(r);
            }
        }
        return out;
    }

    /** Player controlled listings with stock on a plot. */
    @Nonnull
    public List<ShopSpotRecord> listPlayerListingsOnPlot(@Nonnull UUID plotId) {
        List<ShopSpotRecord> out = new ArrayList<>();
        for (ShopSpotRecord r : byId.values()) {
            if (!plotId.equals(r.getPlotId())) {
                continue;
            }
            if (!r.isPlayerControlled() || !r.hasStock() || r.getSellerUuid() == null) {
                continue;
            }
            String itemId = r.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) y & 0xFFFL) << 26) | (((long) z & 0x3FFFFFFL) << 38);
    }

    public static boolean sameBlock(@Nonnull ShopSpotRecord r, @Nonnull Vector3i pos) {
        return r.getBlockX() == pos.x && r.getBlockY() == pos.y && r.getBlockZ() == pos.z;
    }
}
