package com.hexvane.aetherhaven.festival.wintertide;

import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-town Wintertide state. Kept outside the entity Store so dialogue and tick systems can share it. */
public final class WintertideSession {
    private long year;
    @Nonnull
    private final Map<UUID, WintertideTarget> outgoing = new LinkedHashMap<>();
    @Nonnull
    private final Map<UUID, WintertideTarget> incoming = new LinkedHashMap<>();
    @Nonnull
    private final Set<UUID> ceremonyStarted = new LinkedHashSet<>();
    @Nonnull
    private final Set<UUID> given = new LinkedHashSet<>();
    @Nonnull
    private final Set<UUID> received = new LinkedHashSet<>();
    @Nonnull
    private final Set<UUID> seekQueued = new LinkedHashSet<>();
    @Nonnull
    private final Map<UUID, GiftPreference> lastOutgoingPreference = new LinkedHashMap<>();
    @Nullable
    private PendingPlayerGift pendingPlayerGift;

    public long getYear() {
        return year;
    }

    public void setYear(long year) {
        this.year = year;
    }

    public boolean hasOutgoing(@Nonnull UUID playerUuid) {
        return outgoing.containsKey(playerUuid);
    }

    @Nullable
    public WintertideTarget getOutgoing(@Nonnull UUID playerUuid) {
        return outgoing.get(playerUuid);
    }

    public void putOutgoing(@Nonnull UUID playerUuid, @Nonnull WintertideTarget target) {
        outgoing.put(playerUuid, target);
    }

    @Nullable
    public WintertideTarget getIncoming(@Nonnull UUID playerUuid) {
        return incoming.get(playerUuid);
    }

    public void putIncoming(@Nonnull UUID playerUuid, @Nonnull WintertideTarget target) {
        incoming.put(playerUuid, target);
    }

    public boolean isCeremonyStarted(@Nonnull UUID playerUuid) {
        return ceremonyStarted.contains(playerUuid);
    }

    public void markCeremonyStarted(@Nonnull UUID playerUuid) {
        ceremonyStarted.add(playerUuid);
    }

    public boolean hasGiven(@Nonnull UUID playerUuid) {
        return given.contains(playerUuid);
    }

    public void markGiven(@Nonnull UUID playerUuid) {
        given.add(playerUuid);
    }

    public boolean hasReceived(@Nonnull UUID playerUuid) {
        return received.contains(playerUuid);
    }

    public void markReceived(@Nonnull UUID playerUuid) {
        received.add(playerUuid);
    }

    public boolean isSeekQueued(@Nonnull UUID playerUuid) {
        return seekQueued.contains(playerUuid);
    }

    public void markSeekQueued(@Nonnull UUID playerUuid) {
        seekQueued.add(playerUuid);
    }

    @Nullable
    public GiftPreference getLastOutgoingPreference(@Nonnull UUID playerUuid) {
        return lastOutgoingPreference.get(playerUuid);
    }

    public void setLastOutgoingPreference(@Nonnull UUID playerUuid, @Nonnull GiftPreference preference) {
        lastOutgoingPreference.put(playerUuid, preference);
    }

    @Nullable
    public PendingPlayerGift getPendingPlayerGift() {
        return pendingPlayerGift;
    }

    public void setPendingPlayerGift(@Nullable PendingPlayerGift pendingPlayerGift) {
        this.pendingPlayerGift = pendingPlayerGift;
    }

    public boolean isLivePlayerGiftTarget(@Nonnull UUID receiverUuid) {
        for (Map.Entry<UUID, WintertideTarget> e : outgoing.entrySet()) {
            WintertideTarget t = e.getValue();
            if (t == null || !t.isPlayer() || !receiverUuid.equals(t.getUuid())) {
                continue;
            }
            UUID giver = e.getKey();
            if (ceremonyStarted.contains(giver) && !given.contains(giver)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public UUID liveGiverForReceiver(@Nonnull UUID receiverUuid) {
        for (Map.Entry<UUID, WintertideTarget> e : outgoing.entrySet()) {
            WintertideTarget t = e.getValue();
            if (t == null || !t.isPlayer() || !receiverUuid.equals(t.getUuid())) {
                continue;
            }
            UUID giver = e.getKey();
            if (ceremonyStarted.contains(giver) && !given.contains(giver)) {
                return giver;
            }
        }
        return null;
    }

    @Nonnull
    public Set<UUID> assignedPlayerUuids() {
        return Set.copyOf(outgoing.keySet());
    }

    @Nonnull
    public Set<UUID> assignedVillagerUuids() {
        Set<UUID> out = new LinkedHashSet<>();
        for (WintertideTarget t : outgoing.values()) {
            if (t != null && t.isVillager()) {
                out.add(t.getUuid());
            }
        }
        out.addAll(usedIncomingVillagerUuids());
        return out;
    }

    public boolean isAssignedVillager(@Nonnull UUID villagerUuid) {
        return assignedVillagerUuids().contains(villagerUuid);
    }

    /**
     * Stand index among assigned villagers whose kind has no authored festival spot. Returns -1 when this villager
     * uses a kind stand instead.
     */
    public int overflowStandIndex(@Nonnull UUID villagerUuid, @Nonnull Set<String> kindsWithSpots) {
        if (!isAssignedVillager(villagerUuid)) {
            return -1;
        }
        String ownKind = villagerKind(villagerUuid);
        if (ownKind != null
            && !ownKind.isBlank()
            && kindsWithSpots.contains(ownKind.trim().toLowerCase(Locale.ROOT))) {
            return -1;
        }
        List<UUID> overflow = new ArrayList<>();
        for (UUID id : assignedVillagerUuids()) {
            String kind = villagerKind(id);
            if (kind == null
                || kind.isBlank()
                || !kindsWithSpots.contains(kind.trim().toLowerCase(Locale.ROOT))) {
                overflow.add(id);
            }
        }
        overflow.sort(Comparator.comparing(UUID::toString));
        return overflow.indexOf(villagerUuid);
    }

    @Nullable
    public String villagerKind(@Nonnull UUID villagerUuid) {
        for (WintertideTarget t : outgoing.values()) {
            if (t != null && t.isVillager() && villagerUuid.equals(t.getUuid())) {
                return t.getVillagerKind();
            }
        }
        for (WintertideTarget t : incoming.values()) {
            if (t != null && t.isVillager() && villagerUuid.equals(t.getUuid())) {
                return t.getVillagerKind();
            }
        }
        return null;
    }

    @Nonnull
    public Set<UUID> usedOutgoingTargetUuids() {
        Set<UUID> out = new LinkedHashSet<>();
        for (WintertideTarget t : outgoing.values()) {
            if (t != null) {
                out.add(t.getUuid());
            }
        }
        return out;
    }

    @Nonnull
    public Set<UUID> usedIncomingVillagerUuids() {
        Set<UUID> out = new LinkedHashSet<>();
        for (WintertideTarget t : incoming.values()) {
            if (t != null && t.isVillager()) {
                out.add(t.getUuid());
            }
        }
        return out;
    }

    public void clearAll() {
        year = 0L;
        outgoing.clear();
        incoming.clear();
        ceremonyStarted.clear();
        given.clear();
        received.clear();
        seekQueued.clear();
        lastOutgoingPreference.clear();
        pendingPlayerGift = null;
    }

    public record PendingPlayerGift(
        @Nonnull UUID giverUuid,
        @Nonnull UUID receiverUuid,
        @Nonnull String itemId,
        int quantity
    ) {}
}
