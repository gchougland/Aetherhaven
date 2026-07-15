package com.hexvane.aetherhaven.production;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Stored production for one workplace plot: catalog slot cursors and per item amounts. */
public final class PlotProductionState {

    @SerializedName("s0")
    private int s0;

    @SerializedName("s1")
    private int s1;

    @SerializedName("s2")
    private int s2;

    @SerializedName("s3")
    private int s3;

    @SerializedName("s4")
    private int s4;

    /**
     * Legacy single accumulator (pre per-slot timers). Cleared on {@link #migrateIfNeeded()}; production now uses
     * {@link #a0}..{@link #a4}.
     */
    @SerializedName("tickAccum")
    private int tickAccum;

    /** Entity ticks accumulated toward the next unit for output column 0..4 (independent timers per slot). */
    @SerializedName("a0")
    private int a0;

    @SerializedName("a1")
    private int a1;

    @SerializedName("a2")
    private int a2;

    @SerializedName("a3")
    private int a3;

    @SerializedName("a4")
    private int a4;

    @SerializedName("ironUpgrade")
    private int ironUpgrade;

    @SerializedName("thoriumLevel")
    private int thoriumLevel;

    @SerializedName("cobaltLevel")
    private int cobaltLevel;

    @SerializedName("adamantineLevel")
    private int adamantineLevel;

    /** One time legacy migration for pre upgrade 3 slot workplaces. */
    @SerializedName("legacySlotUpgradesApplied")
    private boolean legacySlotUpgradesApplied;

    @SerializedName("amounts")
    private Map<String, Long> amounts = new LinkedHashMap<>();

    /** Player-purchased workplace output item ids for this plot (see {@link WorkplaceUnlockCatalog}). */
    @SerializedName("workUnlockIds")
    private List<String> workplaceUnlockedOutputIds = new ArrayList<>();

    public PlotProductionState() {}

    public int getIronUpgrade() {
        return Math.max(0, Math.min(1, ironUpgrade));
    }

    public void setIronUpgrade(int ironUpgrade) {
        this.ironUpgrade = Math.max(0, Math.min(1, ironUpgrade));
    }

    public int getThoriumLevel() {
        return Math.max(0, Math.min(WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL, thoriumLevel));
    }

    public void setThoriumLevel(int thoriumLevel) {
        this.thoriumLevel = Math.max(0, Math.min(WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL, thoriumLevel));
    }

    public int getCobaltLevel() {
        return Math.max(0, Math.min(WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL, cobaltLevel));
    }

    public void setCobaltLevel(int cobaltLevel) {
        this.cobaltLevel = Math.max(0, Math.min(WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL, cobaltLevel));
    }

    public int getAdamantineLevel() {
        return Math.max(0, Math.min(WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL, adamantineLevel));
    }

    public void setAdamantineLevel(int adamantineLevel) {
        this.adamantineLevel = Math.max(0, Math.min(WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL, adamantineLevel));
    }

    public boolean isLegacySlotUpgradesApplied() {
        return legacySlotUpgradesApplied;
    }

    public int getSlotCursor(int slotIndex) {
        return switch (slotIndex) {
            case 0 -> s0;
            case 1 -> s1;
            case 2 -> s2;
            case 3 -> s3;
            case 4 -> s4;
            default -> 0;
        };
    }

    public void setSlotCursor(int slotIndex, int value) {
        switch (slotIndex) {
            case 0 -> s0 = value;
            case 1 -> s1 = value;
            case 2 -> s2 = value;
            case 3 -> s3 = value;
            case 4 -> s4 = value;
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
     * Sets slot {@code slotIndex} to the catalog index for {@code itemId}, or no op when not found.
     *
     * @return true when the cursor was updated
     */
    public boolean setSlotCursorToItem(int slotIndex, @Nonnull ProductionCatalog.Entry entry, @Nonnull String itemId) {
        if (itemId.isBlank() || entry.catalogSize() <= 0) {
            return false;
        }
        for (int i = 0; i < entry.catalogSize(); i++) {
            String id = entry.itemAtCursor(i);
            if (itemId.equals(id)) {
                setSlotCursor(slotIndex, i);
                return true;
            }
        }
        return false;
    }

    /** When a new workplace finishes building, default slot 0 to the first catalog entry. */
    public void initDefaultSlotCursorsForNewWorkplace(int catalogSize) {
        migrateIfNeeded();
        if (catalogSize <= 0) {
            return;
        }
        setSlotCursor(0, Math.floorMod(0, catalogSize));
        setSlotTickAccum(0, 0);
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
            case 3 -> a3;
            case 4 -> a4;
            default -> 0;
        };
    }

    public void setSlotTickAccum(int slotIndex, int value) {
        int v = Math.max(0, value);
        switch (slotIndex) {
            case 0 -> a0 = v;
            case 1 -> a1 = v;
            case 2 -> a2 = v;
            case 3 -> a3 = v;
            case 4 -> a4 = v;
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


    /** Gson may set amounts null on old saves. Applies one time legacy slot upgrade migration. */
    public void migrateIfNeeded() {
        if (amounts == null) {
            amounts = new LinkedHashMap<>();
        }
        if (workplaceUnlockedOutputIds == null) {
            workplaceUnlockedOutputIds = new ArrayList<>();
        }
        remapLegacyMilkBucketAmounts();
        tickAccum = 0;
        ironUpgrade = Math.max(0, Math.min(1, ironUpgrade));
        thoriumLevel = Math.max(0, Math.min(WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL, thoriumLevel));
        cobaltLevel = Math.max(0, Math.min(WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL, cobaltLevel));
        adamantineLevel = Math.max(0, Math.min(WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL, adamantineLevel));
        if (!legacySlotUpgradesApplied) {
            if (looksLikeLegacyThreeSlotPlot()) {
                ironUpgrade = 1;
                cobaltLevel = Math.max(cobaltLevel, 1);
            }
            legacySlotUpgradesApplied = true;
        }
    }

    /** Pre-asterisk milk bucket id was not a real Item asset; fold stored counts into the real state item. */
    private void remapLegacyMilkBucketAmounts() {
        Long legacy = amounts.remove("Container_Bucket_State_Filled_Milk");
        if (legacy == null || legacy <= 0L) {
            return;
        }
        amounts.put(
            ProductionWithdrawal.MILK_BUCKET_ITEM_ID,
            amounts.getOrDefault(ProductionWithdrawal.MILK_BUCKET_ITEM_ID, 0L) + legacy
        );
    }

    private boolean looksLikeLegacyThreeSlotPlot() {
        if (ironUpgrade > 0 || cobaltLevel > 0 || thoriumLevel > 0 || adamantineLevel > 0) {
            return false;
        }
        if (s1 != 0 || s2 != 0 || a1 != 0 || a2 != 0) {
            return true;
        }
        return amounts != null && !amounts.isEmpty();
    }

    public boolean isWorkplaceOutputUnlocked(@Nonnull String itemId) {
        migrateIfNeeded();
        String id = itemId.trim();
        for (String s : workplaceUnlockedOutputIds) {
            if (id.equals(s)) {
                return true;
            }
        }
        return false;
    }

    public void addWorkplaceOutputUnlock(@Nonnull String itemId) {
        migrateIfNeeded();
        String id = itemId.trim();
        if (id.isEmpty()) {
            return;
        }
        for (String s : workplaceUnlockedOutputIds) {
            if (id.equals(s)) {
                return;
            }
        }
        workplaceUnlockedOutputIds.add(id);
    }

    /**
     * Clamps stored amounts to per-output caps from the workplace catalog. Call after {@link #migrateIfNeeded()} when
     * the plot's {@link com.hexvane.aetherhaven.town.PlotInstance} construction matches this entry.
     *
     * @return true if any stored amount was reduced
     */
    public boolean clampAmountsToCatalogEntry(@Nonnull ProductionCatalog.Entry entry) {
        return clampAmountsToCatalogEntry(entry, 1.0);
    }

    /**
     * Clamps stored amounts using an optional capacity multiplier (adamantine upgrades).
     *
     * @return true if any stored amount was reduced
     */
    public boolean clampAmountsToCatalogEntry(@Nonnull ProductionCatalog.Entry entry, double capacityMultiplier) {
        migrateIfNeeded();
        boolean changed = false;
        double mul = capacityMultiplier <= 0.0 ? 1.0 : capacityMultiplier;
        for (var it = amounts.entrySet().iterator(); it.hasNext(); ) {
            var row = it.next();
            if (row.getValue() == null || row.getValue() <= 0L) {
                it.remove();
                changed = true;
                continue;
            }
            long baseCap = entry.maxStorageForItem(row.getKey());
            long cap = Math.max(1L, Math.min(ProductionCatalog.MAX_STORAGE_PER_OUTPUT, (long) Math.ceil(baseCap * mul)));
            if (row.getValue() > cap) {
                row.setValue(cap);
                changed = true;
            }
        }
        return changed;
    }

    @Nullable
    public String itemAtSlotCursor(int slotIndex, @Nonnull ProductionCatalog.Entry entry) {
        int cursor = getSlotCursor(slotIndex);
        return entry.itemAtCursor(cursor);
    }
}
