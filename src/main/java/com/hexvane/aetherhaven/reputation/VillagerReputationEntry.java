package com.hexvane.aetherhaven.reputation;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-player per-villager-entity reputation row stored in {@link com.hexvane.aetherhaven.town.TownRecord}. */
public final class VillagerReputationEntry {
    @SerializedName("reputation")
    private int reputation;

    @SerializedName("lastTalkGameEpochDay")
    private Long lastTalkGameEpochDay;

    @SerializedName("claimedRewardIds")
    private List<String> claimedRewardIds = new ArrayList<>();

    @SerializedName("pendingRewardIds")
    private List<String> pendingRewardIds = new ArrayList<>();

    public int getReputation() {
        return reputation;
    }

    public void setReputation(int reputation) {
        this.reputation = reputation;
    }

    @Nonnull
    public List<String> getClaimedRewardIds() {
        if (claimedRewardIds == null) {
            claimedRewardIds = new ArrayList<>();
        }
        return claimedRewardIds;
    }

    @Nonnull
    public List<String> getPendingRewardIds() {
        if (pendingRewardIds == null) {
            pendingRewardIds = new ArrayList<>();
        }
        return pendingRewardIds;
    }

    public Long getLastTalkGameEpochDay() {
        return lastTalkGameEpochDay;
    }

    public void setLastTalkGameEpochDay(@Nullable Long lastTalkGameEpochDay) {
        this.lastTalkGameEpochDay = lastTalkGameEpochDay;
    }

    public void migrateIfNeeded() {
        if (claimedRewardIds == null) {
            claimedRewardIds = new ArrayList<>();
        }
        if (pendingRewardIds == null) {
            pendingRewardIds = new ArrayList<>();
        }
    }

    @Nonnull
    public Set<String> claimedSet() {
        return new LinkedHashSet<>(getClaimedRewardIds());
    }
}
