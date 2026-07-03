package com.hexvane.aetherhaven.tourist;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class TouristPortalRegistry {
    private final World world;
    private final Map<UUID, TouristPortalRecord> byId = new HashMap<>();
    private final Map<Long, UUID> byBlockKey = new HashMap<>();

    public TouristPortalRegistry(@Nonnull World world) {
        this.world = world;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    public void replaceAll(@Nonnull List<TouristPortalRecord> records) {
        byId.clear();
        byBlockKey.clear();
        for (TouristPortalRecord r : records) {
            if (!world.getName().equals(r.getWorldName())) {
                continue;
            }
            put(r);
        }
    }

    public void put(@Nonnull TouristPortalRecord record) {
        byId.put(record.getPortalId(), record);
        Vector3i pos = record.getBlockPosition();
        byBlockKey.put(blockKey(pos.x, pos.y, pos.z), record.getPortalId());
    }

    public void remove(@Nonnull UUID portalId) {
        TouristPortalRecord r = byId.remove(portalId);
        if (r != null) {
            Vector3i pos = r.getBlockPosition();
            byBlockKey.remove(blockKey(pos.x, pos.y, pos.z));
        }
    }

    @Nullable
    public TouristPortalRecord get(@Nonnull UUID portalId) {
        return byId.get(portalId);
    }

    @Nullable
    public TouristPortalRecord getAtBlock(int x, int y, int z) {
        UUID id = byBlockKey.get(blockKey(x, y, z));
        return id != null ? byId.get(id) : null;
    }

    @Nonnull
    public List<TouristPortalRecord> allRecords() {
        return new ArrayList<>(byId.values());
    }

    @Nonnull
    public List<TouristPortalRecord> recordsForTown(@Nonnull UUID townId) {
        List<TouristPortalRecord> out = new ArrayList<>();
        for (TouristPortalRecord r : byId.values()) {
            if (townId.equals(r.getTownId())) {
                out.add(r);
            }
        }
        return out;
    }

    @Nonnull
    public List<TouristPortalRecord> listForPlot(@Nonnull UUID plotId) {
        List<TouristPortalRecord> out = new ArrayList<>();
        for (TouristPortalRecord r : byId.values()) {
            if (plotId.equals(r.getPlotId())) {
                out.add(r);
            }
        }
        return out;
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) y & 0xFFFL) << 26) | (((long) z & 0x3FFFFFFL) << 38);
    }
}
