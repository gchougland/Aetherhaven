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
    /**
     * Player listings whose stall no longer exists (a rebuilt building dropped the stall). They are saved with the
     * stalls but are never offered for sale; {@link ShopSpotListingReturns} hands them back to their seller.
     */
    private final List<ShopSpotRecord> heldReturns = new ArrayList<>();
    final ShopSpotDisplayQueue displayUpdates = new ShopSpotDisplayQueue();

    public ShopSpotRegistry(@Nonnull World world) {
        this.world = world;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    public void replaceAll(@Nonnull List<ShopSpotRecord> records) {
        replaceAll(records, List.of());
    }

    public void replaceAll(@Nonnull List<ShopSpotRecord> records, @Nonnull List<ShopSpotRecord> held) {
        byId.clear();
        byBlockKey.clear();
        heldReturns.clear();
        for (ShopSpotRecord r : records) {
            if (!world.getName().equals(r.getWorldName())) {
                continue;
            }
            put(r);
        }
        for (ShopSpotRecord r : held) {
            if (world.getName().equals(r.getWorldName()) && ShopSpotListingReturns.isReturnable(r)) {
                heldReturns.add(r);
            }
        }
    }

    /**
     * Takes a stall out of the live registry. A player listing it still carries is kept as a held return instead of
     * being discarded.
     *
     * @return true when the listing was kept for its seller
     */
    public boolean removeKeepingListing(@Nonnull ShopSpotRecord record) {
        remove(record.getSpotId());
        if (!ShopSpotListingReturns.isReturnable(record) || heldReturns.contains(record)) {
            return false;
        }
        record.setDisplayEntityUuid(null);
        heldReturns.add(record);
        return true;
    }

    public boolean hasHeldReturns() {
        return !heldReturns.isEmpty();
    }

    @Nonnull
    public List<ShopSpotRecord> heldReturns() {
        return new ArrayList<>(heldReturns);
    }

    @Nonnull
    public List<ShopSpotRecord> heldReturnsFor(@Nonnull UUID sellerUuid) {
        List<ShopSpotRecord> out = new ArrayList<>();
        for (ShopSpotRecord r : heldReturns) {
            if (sellerUuid.equals(r.getSellerUuid())) {
                out.add(r);
            }
        }
        return out;
    }

    /** Call once the held listing has really been handed to its seller. */
    public boolean releaseHeldReturn(@Nonnull ShopSpotRecord record) {
        return heldReturns.remove(record);
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
