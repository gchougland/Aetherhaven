package com.hexvane.aetherhaven.production;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/** Stored production for one workplace plot: three catalog slot cursors and per item amounts. */
public final class PlotProductionState {

    @SerializedName("s0")
    private int s0;

    @SerializedName("s1")
    private int s1;

    @SerializedName("s2")
    private int s2;

    /** Game ticks accumulated toward the next production pulse for this plot. */
    @SerializedName("tickAccum")
    private int tickAccum;

    @SerializedName("amounts")
    private Map<String, Long> amounts = new LinkedHashMap<>();

    public PlotProductionState() {}

    public int getSlotCursor(int slotIndex) {
        return switch (slotIndex) {
            case 0 -> s0;
            case 1 -> s1;
            case 2 -> s2;
            default -> 0;
        };
    }

    public void setSlotCursor(int slotIndex, int value) {
        switch (slotIndex) {
            case 0 -> s0 = value;
            case 1 -> s1 = value;
            case 2 -> s2 = value;
            default -> {}
        }
    }

    /** Rotates slot {@code slotIndex} cursor by {@code delta} (+/-) modulo {@code catalogSize}. */
    public void cycleSlotCursor(int slotIndex, int delta, int catalogSize) {
        if (catalogSize <= 0) {
            return;
        }
        int c = getSlotCursor(slotIndex) + delta;
        c = Math.floorMod(c, catalogSize);
        setSlotCursor(slotIndex, c);
    }

    public int getTickAccum() {
        return tickAccum;
    }

    public void setTickAccum(int tickAccum) {
        this.tickAccum = Math.max(0, tickAccum);
    }

    @Nonnull
    public Map<String, Long> getAmounts() {
        return amounts;
    }

    public long getAmount(@Nonnull String itemId) {
        return amounts.getOrDefault(itemId, 0L);
    }

    public void addAmount(@Nonnull String itemId, long delta) {
        if (itemId.isBlank()) {
            return;
        }
        long v = amounts.getOrDefault(itemId, 0L) + delta;
        if (v < 0L) {
            v = 0L;
        }
        if (v > AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX) {
            v = AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX;
        }
        if (v == 0L) {
            amounts.remove(itemId);
        } else {
            amounts.put(itemId, v);
        }
    }

    /** Removes up to {@code request} of {@code itemId}; returns amount actually removed. */
    public long removeAmountUpTo(@Nonnull String itemId, long request) {
        if (itemId.isBlank() || request <= 0L) {
            return 0L;
        }
        long have = amounts.getOrDefault(itemId, 0L);
        long take = Math.min(have, request);
        if (take <= 0L) {
            return 0L;
        }
        long left = have - take;
        if (left <= 0L) {
            amounts.remove(itemId);
        } else {
            amounts.put(itemId, left);
        }
        return take;
    }

    @Nonnull
    public static PlotProductionState empty() {
        return new PlotProductionState();
    }

    /** Gson may set amounts null on old saves. */
    public void migrateIfNeeded() {
        if (amounts == null) {
            amounts = new LinkedHashMap<>();
            return;
        }
        long cap = AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX;
        for (var it = amounts.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue() == null || e.getValue() <= 0L) {
                it.remove();
            } else if (e.getValue() > cap) {
                e.setValue(cap);
            }
        }
    }
}
