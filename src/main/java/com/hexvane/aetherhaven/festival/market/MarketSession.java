package com.hexvane.aetherhaven.festival.market;

import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Per-town Market Festival state. Kept outside the entity Store so dialogue and tick systems can share it safely.
 */
public final class MarketSession {
    public enum Phase {
        IDLE,
        JUDGING,
        JUDGED
    }

    private Phase phase = Phase.IDLE;
    private long year;
    @Nonnull
    private final String[] slotItemIds = new String[MarketIds.SLOT_COUNT];
    @Nonnull
    private final int[] slotQuantities = new int[MarketIds.SLOT_COUNT];
    @Nullable
    private SimpleItemContainer liveContainer;
    @Nonnull
    private final List<UUID> playerDisplayUuids = new ArrayList<>();
    @Nonnull
    private final List<UUID> rivalDisplayUuids = new ArrayList<>();
    @Nullable
    private UUID stallPadUuid;
    private int currentStandIndex;
    private long ponderUntilMs;
    private boolean pondering;
    private int score;
    private int place;
    @Nonnull
    private final List<UUID> vendorUuids = new ArrayList<>();
    @Nonnull
    private final Set<UUID> claimedTicketPlayers = new LinkedHashSet<>();
    private boolean plushieGranted;
    private boolean goodsReturned;
    private boolean announced;
    @Nullable
    private Vector3d stallDropPos;
    @Nonnull
    private final List<Vector3d> displayPositions = new ArrayList<>();
    @Nonnull
    private final List<Vector3d> standPositions = new ArrayList<>();

    @Nonnull
    public Phase getPhase() {
        return phase;
    }

    public boolean isJudging() {
        return phase == Phase.JUDGING;
    }

    public boolean isJudged() {
        return phase == Phase.JUDGED;
    }

    public boolean isStallLocked() {
        return phase != Phase.IDLE;
    }

    public long getYear() {
        return year;
    }

    public void setYear(long year) {
        this.year = year;
    }

    public boolean isStallEmpty() {
        for (String id : slotItemIds) {
            if (id != null && !id.isBlank()) {
                return false;
            }
        }
        if (liveContainer != null) {
            for (short i = 0; i < liveContainer.getCapacity(); i++) {
                var stack = liveContainer.getItemStack(i);
                if (stack != null && !com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void snapshotFromContainer(@Nullable SimpleItemContainer container) {
        Arrays.fill(slotItemIds, null);
        Arrays.fill(slotQuantities, 0);
        if (container == null) {
            return;
        }
        int cap = Math.min(MarketIds.SLOT_COUNT, container.getCapacity());
        for (short i = 0; i < cap; i++) {
            var stack = container.getItemStack(i);
            if (stack == null || com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack)) {
                continue;
            }
            String id = stack.getItemId();
            if (id == null || id.isBlank()) {
                continue;
            }
            slotItemIds[i] = id.trim();
            slotQuantities[i] = Math.max(1, stack.getQuantity());
        }
    }

    @Nonnull
    public List<String> filledItemIds() {
        List<String> out = new ArrayList<>();
        for (String id : slotItemIds) {
            if (id != null && !id.isBlank()) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    @Nullable
    public String slotItemId(int index) {
        if (index < 0 || index >= slotItemIds.length) {
            return null;
        }
        return slotItemIds[index];
    }

    public int slotQuantity(int index) {
        if (index < 0 || index >= slotQuantities.length) {
            return 0;
        }
        return slotQuantities[index];
    }

    @Nullable
    public SimpleItemContainer getLiveContainer() {
        return liveContainer;
    }

    public void setLiveContainer(@Nullable SimpleItemContainer liveContainer) {
        this.liveContainer = liveContainer;
    }

    @Nonnull
    public List<UUID> playerDisplayUuidsView() {
        return List.copyOf(playerDisplayUuids);
    }

    public void setPlayerDisplayUuids(@Nonnull List<UUID> uuids) {
        playerDisplayUuids.clear();
        playerDisplayUuids.addAll(uuids);
    }

    public void clearPlayerDisplayUuids() {
        playerDisplayUuids.clear();
    }

    @Nonnull
    public List<UUID> rivalDisplayUuidsView() {
        return List.copyOf(rivalDisplayUuids);
    }

    public void addRivalDisplay(@Nonnull UUID uuid) {
        rivalDisplayUuids.add(uuid);
    }

    public void clearRivalDisplayUuids() {
        rivalDisplayUuids.clear();
    }

    @Nullable
    public UUID getStallPadUuid() {
        return stallPadUuid;
    }

    public void setStallPadUuid(@Nullable UUID stallPadUuid) {
        this.stallPadUuid = stallPadUuid;
    }

    public int getCurrentStandIndex() {
        return currentStandIndex;
    }

    public boolean tryBeginJudging(long nowMs) {
        if (phase != Phase.IDLE || isStallEmpty()) {
            return false;
        }
        phase = Phase.JUDGING;
        currentStandIndex = 0;
        pondering = false;
        ponderUntilMs = 0L;
        announced = false;
        return true;
    }

    public void beginPonder(long untilMs) {
        pondering = true;
        ponderUntilMs = untilMs;
    }

    public boolean isPondering() {
        return pondering;
    }

    public boolean ponderFinished(long nowMs) {
        return pondering && nowMs >= ponderUntilMs;
    }

    public void advanceAfterPonder() {
        pondering = false;
        ponderUntilMs = 0L;
        currentStandIndex++;
        if (currentStandIndex >= MarketIds.STAND_COUNT) {
            finishJudging();
        }
    }

    private void finishJudging() {
        phase = Phase.JUDGED;
        currentStandIndex = 0;
        pondering = false;
        MarketScore.Breakdown breakdown = MarketScore.scoreSlots(filledItemIds());
        score = breakdown.total();
        place = MarketScore.place(score);
    }

    public int getScore() {
        return score;
    }

    public int getPlace() {
        return place;
    }

    @Nonnull
    public List<UUID> vendorUuidsView() {
        return List.copyOf(vendorUuids);
    }

    public void setVendorUuids(@Nonnull List<UUID> vendors) {
        vendorUuids.clear();
        vendorUuids.addAll(vendors);
    }

    public int vendorIndex(@Nonnull UUID villagerUuid) {
        for (int i = 0; i < vendorUuids.size(); i++) {
            if (villagerUuid.equals(vendorUuids.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public boolean isVendor(@Nonnull UUID villagerUuid) {
        return vendorIndex(villagerUuid) >= 0;
    }

    public boolean hasClaimedTickets(@Nonnull UUID playerUuid) {
        return claimedTicketPlayers.contains(playerUuid);
    }

    public void markTicketsClaimed(@Nonnull UUID playerUuid) {
        claimedTicketPlayers.add(playerUuid);
    }

    public boolean isPlushieGranted() {
        return plushieGranted;
    }

    public void markPlushieGranted() {
        plushieGranted = true;
    }

    public boolean isGoodsReturned() {
        return goodsReturned;
    }

    public void markGoodsReturned() {
        goodsReturned = true;
    }

    public boolean consumeAnnounce() {
        if (announced || phase != Phase.JUDGED) {
            return false;
        }
        announced = true;
        return true;
    }

    @Nullable
    public Vector3d getStallDropPos() {
        return stallDropPos != null ? new Vector3d(stallDropPos) : null;
    }

    public void setStallDropPos(@Nullable Vector3d stallDropPos) {
        this.stallDropPos = stallDropPos != null ? new Vector3d(stallDropPos) : null;
    }

    @Nonnull
    public List<Vector3d> displayPositionsView() {
        return List.copyOf(displayPositions);
    }

    public void setDisplayPositions(@Nonnull List<Vector3d> positions) {
        displayPositions.clear();
        for (Vector3d p : positions) {
            if (p != null) {
                displayPositions.add(new Vector3d(p));
            }
        }
    }

    @Nonnull
    public List<Vector3d> standPositionsView() {
        return List.copyOf(standPositions);
    }

    public void setStandPositions(@Nonnull List<Vector3d> positions) {
        standPositions.clear();
        for (Vector3d p : positions) {
            if (p != null) {
                standPositions.add(new Vector3d(p));
            }
        }
    }

    public void clearAll() {
        phase = Phase.IDLE;
        year = 0L;
        Arrays.fill(slotItemIds, null);
        Arrays.fill(slotQuantities, 0);
        liveContainer = null;
        playerDisplayUuids.clear();
        rivalDisplayUuids.clear();
        stallPadUuid = null;
        currentStandIndex = 0;
        ponderUntilMs = 0L;
        pondering = false;
        score = 0;
        place = 0;
        vendorUuids.clear();
        claimedTicketPlayers.clear();
        plushieGranted = false;
        goodsReturned = false;
        announced = false;
        stallDropPos = null;
        displayPositions.clear();
        standPositions.clear();
    }
}
