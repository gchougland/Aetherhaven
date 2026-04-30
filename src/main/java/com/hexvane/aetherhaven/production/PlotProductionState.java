package com.hexvane.aetherhaven.production;

import com.google.gson.annotations.SerializedName;
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

    /**
     * Legacy single accumulator (pre per-slot timers). Cleared on {@link #migrateIfNeeded()}; production now uses
     * {@link #a0}..{@link #a2}.
     */
    @SerializedName("tickAccum")
    private int tickAccum;

    /** Entity ticks accumulated toward the next unit for output column 0..2 (independent timers per slot). */
    @SerializedName("a0")
    private int a0;

    @SerializedName("a1")
    private int a1;

    @SerializedName("a2")
    private int a2;

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

    /**
     * When a new workplace finishes building, spread the three columns across the first catalog entries (0, 1, 2
     * modulo size) instead of defaulting every column to index 0.
     */
    public void initDefaultSlotCursorsForNewWorkplace(int catalogSize) {
        migrateIfNeeded();
        if (catalogSize <= 0) {
            return;
        }
        setSlotCursor(0, Math.floorMod(0, catalogSize));
        setSlotCursor(1, Math.floorMod(1, catalogSize));
        setSlotCursor(2, Math.floorMod(2, catalogSize));
        setSlotTickAccum(0, 0);
        setSlotTickAccum(1, 0);
        setSlotTickAccum(2, 0);
    }

    public int getTickAccum() {
        return tickAccum;
    }

    public void setTickAccum(int tickAccum) {
        this.tickAccum = Math.max(0, tickAccum);
    }

    public int getSlotTickAccum(int slotIndex) {
        return switch (slotIndex) {
            case 0 -> a0;
            case 1 -> a1;
            case 2 -> a2;
            default -> 0;
        };
    }

    public void setSlotTickAccum(int slotIndex, int value) {
        int v = Math.max(0, value);
        switch (slotIndex) {
            case 0 -> a0 = v;
            case 1 -> a1 = v;
            case 2 -> a2 = v;
            default -> {}
        }
    }

    @Nonnull
    public Map<String, Long> getAmounts() {
        return amounts;
    }

    public long getAmount(@Nonnull String itemId) {
        return amounts.getOrDefault(itemId, 0L);
    }

    public void addAmount(@Nonnull String itemId, long delta, long maxForItem) {
        if (itemId.isBlank()) {
            return;
        }
        long cap = Math.max(1L, Math.min(maxForItem, ProductionCatalog.MAX_STORAGE_PER_OUTPUT));
        long v = amounts.getOrDefault(itemId, 0L) + delta;
        if (v < 0L) {
            v = 0L;
        }
        if (v > cap) {
            v = cap;
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
        }
        tickAccum = 0;
    }

    /**
     * Clamps stored amounts to per-output caps from the workplace catalog. Call after {@link #migrateIfNeeded()} when
     * the plot's {@link com.hexvane.aetherhaven.town.PlotInstance} construction matches this entry.
     *
     * @return true if any stored amount was reduced
     */
    public boolean clampAmountsToCatalogEntry(@Nonnull ProductionCatalog.Entry entry) {
        migrateIfNeeded();
        boolean changed = false;
        for (var it = amounts.entrySet().iterator(); it.hasNext(); ) {
            var row = it.next();
            if (row.getValue() == null || row.getValue() <= 0L) {
                it.remove();
                changed = true;
                continue;
            }
            long cap = entry.maxStorageForItem(row.getKey());
            if (row.getValue() > cap) {
                row.setValue(cap);
                changed = true;
            }
        }
        return changed;
    }
}
