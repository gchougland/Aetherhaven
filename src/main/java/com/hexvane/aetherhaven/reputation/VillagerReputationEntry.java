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

    /**
     * One-time: innkeeper reward ids were collapsed (one dialogue at 50, new ids for 75/100). When false, legacy
     * claimed/pending entries are remapped; old {@code rep_innkeeper_50} (meat) is cleared so the new 50 can show.
     */
    @SerializedName("migratedInnkeeperRewardsV2")
    private boolean migratedInnkeeperRewardsV2;

    @Nullable
    @SerializedName("lastGiftGameEpochDay")
    private Long lastGiftGameEpochDay;

    @Nullable
    @SerializedName("giftWeekBlockId")
    private Long giftWeekBlockId;

    @SerializedName("giftsThisWeekBlock")
    private int giftsThisWeekBlock;

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

    @Nullable
    public Long getLastGiftGameEpochDay() {
        return lastGiftGameEpochDay;
    }

    public void setLastGiftGameEpochDay(@Nullable Long lastGiftGameEpochDay) {
        this.lastGiftGameEpochDay = lastGiftGameEpochDay;
    }

    @Nullable
    public Long getGiftWeekBlockId() {
        return giftWeekBlockId;
    }

    public void setGiftWeekBlockId(@Nullable Long giftWeekBlockId) {
        this.giftWeekBlockId = giftWeekBlockId;
    }

    public int getGiftsThisWeekBlock() {
        return giftsThisWeekBlock;
    }

    public void setGiftsThisWeekBlock(int giftsThisWeekBlock) {
        this.giftsThisWeekBlock = Math.max(0, giftsThisWeekBlock);
    }

    public void migrateIfNeeded() {
        if (claimedRewardIds == null) {
            claimedRewardIds = new ArrayList<>();
        }
        if (pendingRewardIds == null) {
            pendingRewardIds = new ArrayList<>();
        }
        // Elder reputation rewards were replaced; drop obsolete pending ids so dialogue does not stall.
        pendingRewardIds.removeIf(id -> id != null && (id.equals("rep_elder_25") || id.equals("rep_elder_75")));
        pendingRewardIds.removeIf(id -> id != null && id.equals("rep_merchant_25"));
        claimedRewardIds.removeIf(id -> id != null && id.equals("rep_merchant_25"));
        // Jewelry workbench unlock moved from rep 75 to 100; rewrite stored ids.
        for (int i = 0; i < claimedRewardIds.size(); i++) {
            if ("rep_merchant_75".equals(claimedRewardIds.get(i))) {
                claimedRewardIds.set(i, "rep_merchant_100");
            }
        }
        for (int i = 0; i < pendingRewardIds.size(); i++) {
            if (!"rep_merchant_75".equals(pendingRewardIds.get(i))) {
                continue;
            }
            if (reputation >= 100) {
                pendingRewardIds.set(i, "rep_merchant_100");
            } else {
                pendingRewardIds.remove(i);
                i--;
            }
        }
        if (!migratedInnkeeperRewardsV2) {
            migrateInnkeeperRewardsV2();
            migratedInnkeeperRewardsV2 = true;
        }
        if (giftsThisWeekBlock < 0) {
            giftsThisWeekBlock = 0;
        }
    }

    private void migrateInnkeeperRewardsV2() {
        getPendingRewardIds()
            .removeIf(
                id ->
                    id != null
                        && (id.equals("rep_innkeeper_25")
                            || id.equals("rep_innkeeper_50_table")
                            || id.equals("rep_innkeeper_feast_stewards")
                            || id.equals("rep_innkeeper_feast_hearthglass")
                            || id.equals("rep_innkeeper_feast_berrycircle"))
            );
        if (getClaimedRewardIds().remove("rep_innkeeper_feast_hearthglass")) {
            if (!getClaimedRewardIds().contains("rep_innkeeper_75")) {
                getClaimedRewardIds().add("rep_innkeeper_75");
            }
        }
        if (getClaimedRewardIds().remove("rep_innkeeper_feast_berrycircle")) {
            if (!getClaimedRewardIds().contains("rep_innkeeper_100")) {
                getClaimedRewardIds().add("rep_innkeeper_100");
            }
        }
        getClaimedRewardIds()
            .removeIf(
                id ->
                    id != null
                        && (id.equals("rep_innkeeper_25")
                            || id.equals("rep_innkeeper_50_table")
                            || id.equals("rep_innkeeper_feast_stewards")
                            || id.equals("rep_innkeeper_50"))
            );
    }

    @Nonnull
    public Set<String> claimedSet() {
        return new LinkedHashSet<>(getClaimedRewardIds());
    }
}
