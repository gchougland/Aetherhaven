package com.hexvane.aetherhaven.questboard.data;

import com.google.gson.annotations.SerializedName;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestBoardDefinitionJson {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    @SerializedName("schemaVersion")
    private int schemaVersion;

    @SerializedName("slotCount")
    private int slotCount;

    @SerializedName("ranks")
    @Nullable
    private List<QuestBoardRankTierJson> ranks;

    @SerializedName("villagers")
    @Nullable
    private Map<String, QuestBoardVillagerJson> villagers;

    @SerializedName("questTypes")
    @Nullable
    private Map<String, QuestBoardQuestTypeWeightJson> questTypes;

    public int schemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    /** Raw slot count from JSON (0 means unset). */
    public int rawSlotCount() {
        return slotCount;
    }

    public int slotCount() {
        return slotCount <= 0 ? 3 : slotCount;
    }

    public void setSlotCount(int slotCount) {
        this.slotCount = slotCount;
    }

    @Nonnull
    public List<QuestBoardRankTierJson> ranksOrEmpty() {
        return ranks != null ? ranks : List.of();
    }

    public void setRanks(@Nullable List<QuestBoardRankTierJson> ranks) {
        this.ranks = ranks;
    }

    @Nonnull
    public Map<String, QuestBoardVillagerJson> villagersOrEmpty() {
        return villagers != null ? villagers : Map.of();
    }

    public void setVillagers(@Nullable Map<String, QuestBoardVillagerJson> villagers) {
        this.villagers = villagers;
    }

    @Nonnull
    public Map<String, QuestBoardQuestTypeWeightJson> questTypesOrEmpty() {
        return questTypes != null ? questTypes : Map.of();
    }

    public void setQuestTypes(@Nullable Map<String, QuestBoardQuestTypeWeightJson> questTypes) {
        this.questTypes = questTypes != null ? new LinkedHashMap<>(questTypes) : null;
    }
}
