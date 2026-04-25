package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import javax.annotation.Nonnull;

/** One gift event for a villager role, stored on the town (filter by {@link #giverPlayerUuid} in UI). */
public final class VillagerGiftLogEntry {
    @SerializedName("itemId")
    private String itemId = "";

    @SerializedName("tier")
    private String tier = "neutral";

    @SerializedName("giverPlayerUuid")
    private String giverPlayerUuid = "";

    @SerializedName("gameEpochDay")
    private long gameEpochDay;

    public VillagerGiftLogEntry() {}

    public VillagerGiftLogEntry(
        @Nonnull String itemId,
        @Nonnull GiftPreference tier,
        @Nonnull String giverPlayerUuid,
        long gameEpochDay
    ) {
        this.itemId = itemId != null ? itemId.trim() : "";
        this.tier = tier.toWireId();
        this.giverPlayerUuid = giverPlayerUuid != null ? giverPlayerUuid.trim() : "";
        this.gameEpochDay = gameEpochDay;
    }

    @Nonnull
    public String getItemId() {
        return itemId != null ? itemId : "";
    }

    @Nonnull
    public GiftPreference getTier() {
        return GiftPreference.fromLabel(tier);
    }

    @Nonnull
    public String getGiverPlayerUuid() {
        return giverPlayerUuid != null ? giverPlayerUuid : "";
    }

    public long getGameEpochDay() {
        return gameEpochDay;
    }
}
