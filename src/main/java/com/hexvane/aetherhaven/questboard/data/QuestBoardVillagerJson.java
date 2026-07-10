package com.hexvane.aetherhaven.questboard.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestBoardVillagerJson {
    @SerializedName("fetchEntries")
    @Nullable
    private List<QuestBoardFetchEntryJson> fetchEntries;

    @SerializedName("huntEntries")
    @Nullable
    private List<QuestBoardHuntEntryJson> huntEntries;

    @SerializedName("raidEntries")
    @Nullable
    private List<QuestBoardRaidEntryJson> raidEntries;

    @Nonnull
    public List<QuestBoardFetchEntryJson> fetchEntriesOrEmpty() {
        return fetchEntries != null ? fetchEntries : List.of();
    }

    public void setFetchEntries(@Nullable List<QuestBoardFetchEntryJson> fetchEntries) {
        this.fetchEntries = fetchEntries != null ? new ArrayList<>(fetchEntries) : null;
    }

    @Nonnull
    public List<QuestBoardHuntEntryJson> huntEntriesOrEmpty() {
        return huntEntries != null ? huntEntries : List.of();
    }

    public void setHuntEntries(@Nullable List<QuestBoardHuntEntryJson> huntEntries) {
        this.huntEntries = huntEntries != null ? new ArrayList<>(huntEntries) : null;
    }

    @Nonnull
    public List<QuestBoardRaidEntryJson> raidEntriesOrEmpty() {
        return raidEntries != null ? raidEntries : List.of();
    }

    public void setRaidEntries(@Nullable List<QuestBoardRaidEntryJson> raidEntries) {
        this.raidEntries = raidEntries != null ? new ArrayList<>(raidEntries) : null;
    }
}
