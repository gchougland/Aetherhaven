package com.hexvane.aetherhaven.worldnpc;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-player world NPC progress (quests, reputation, board). */
public final class WorldNpcPlayerProgress {
    @SerializedName("playerUuid")
    @Nullable
    private String playerUuid;

    @SerializedName("activeQuestIds")
    @Nullable
    private List<String> activeQuestIds;

    @SerializedName("completedQuestIds")
    @Nullable
    private List<String> completedQuestIds;

    @SerializedName("questObjectiveProgress")
    @Nullable
    private Map<String, Map<String, Boolean>> questObjectiveProgress;

    @SerializedName("questKillProgress")
    @Nullable
    private Map<String, Map<String, Integer>> questKillProgress;

    /** placementId -> reputation entry */
    @SerializedName("reputationByPlacementId")
    @Nullable
    private Map<String, VillagerReputationEntry> reputationByPlacementId;

    @SerializedName("giftLastWeekByPlacementId")
    @Nullable
    private Map<String, Long> giftLastWeekByPlacementId;

    @SerializedName("giftCountThisWeekByPlacementId")
    @Nullable
    private Map<String, Integer> giftCountThisWeekByPlacementId;

    @SerializedName("boardSlotsByProfileId")
    @Nullable
    private Map<String, List<QuestBoardSlotRecord>> boardSlotsByProfileId;

    @SerializedName("boardRankXpByProfileId")
    @Nullable
    private Map<String, Integer> boardRankXpByProfileId;

    @SerializedName("boardDrawPoolByProfileId")
    @Nullable
    private Map<String, List<String>> boardDrawPoolByProfileId;

    @SerializedName("pendingRewardNodeByPlacementId")
    @Nullable
    private Map<String, String> pendingRewardNodeByPlacementId;

    @SerializedName("pendingMainHubBodyByPlacementId")
    @Nullable
    private Map<String, String> pendingMainHubBodyByPlacementId;

    @Nonnull
    public UUID playerUuidOrThrow() {
        return UUID.fromString(playerUuid != null ? playerUuid.trim() : "");
    }

    public void setPlayerUuid(@Nonnull UUID uuid) {
        this.playerUuid = uuid.toString();
    }

    @Nonnull
    public Set<String> activeQuestIdsSnapshot() {
        return normalizedSet(activeQuestIds);
    }

    @Nonnull
    public Set<String> completedQuestIdsSnapshot() {
        return normalizedSet(completedQuestIds);
    }

    public boolean hasQuestActive(@Nonnull String questId) {
        return normalizedSet(activeQuestIds).contains(questId.trim());
    }

    public boolean hasQuestCompleted(@Nonnull String questId) {
        return normalizedSet(completedQuestIds).contains(questId.trim());
    }

    public void addActiveQuest(@Nonnull String questId) {
        Set<String> active = normalizedSet(activeQuestIds);
        active.add(questId.trim());
        activeQuestIds = new ArrayList<>(active);
    }

    public void removeActiveQuest(@Nonnull String questId) {
        Set<String> active = normalizedSet(activeQuestIds);
        active.remove(questId.trim());
        activeQuestIds = new ArrayList<>(active);
    }

    public void markQuestCompleted(@Nonnull String questId) {
        removeActiveQuest(questId);
        Set<String> done = normalizedSet(completedQuestIds);
        done.add(questId.trim());
        completedQuestIds = new ArrayList<>(done);
        if (questObjectiveProgress != null) {
            questObjectiveProgress.remove(questId.trim());
        }
        if (questKillProgress != null) {
            questKillProgress.remove(questId.trim());
        }
    }

    public void clearQuest(@Nonnull String questId) {
        removeActiveQuest(questId);
        Set<String> done = normalizedSet(completedQuestIds);
        done.remove(questId.trim());
        completedQuestIds = new ArrayList<>(done);
        if (questObjectiveProgress != null) {
            questObjectiveProgress.remove(questId.trim());
        }
        if (questKillProgress != null) {
            questKillProgress.remove(questId.trim());
        }
    }

    public void initQuestObjectiveProgress(@Nonnull String questId, @Nonnull List<String> objectiveIds) {
        Map<String, Map<String, Boolean>> root = objectiveProgressMutable();
        Map<String, Boolean> row = root.computeIfAbsent(questId.trim(), k -> new LinkedHashMap<>());
        for (String oid : objectiveIds) {
            if (oid == null || oid.isBlank()) {
                continue;
            }
            row.putIfAbsent(oid.trim(), Boolean.FALSE);
        }
    }

    public void initQuestKillProgress(@Nonnull String questId, @Nonnull List<String> objectiveIds) {
        Map<String, Map<String, Integer>> root = killProgressMutable();
        Map<String, Integer> row = root.computeIfAbsent(questId.trim(), k -> new LinkedHashMap<>());
        for (String oid : objectiveIds) {
            if (oid == null || oid.isBlank()) {
                continue;
            }
            row.putIfAbsent(oid.trim(), 0);
        }
    }

    public boolean completeQuestObjective(@Nonnull String questId, @Nonnull String objectiveId) {
        Map<String, Boolean> row = objectiveProgressMutable().computeIfAbsent(questId.trim(), k -> new LinkedHashMap<>());
        Boolean prev = row.put(objectiveId.trim(), Boolean.TRUE);
        return prev == null || !prev;
    }

    public boolean isQuestObjectiveComplete(@Nonnull String questId, @Nonnull String objectiveId) {
        Map<String, Map<String, Boolean>> root = questObjectiveProgress;
        if (root == null) {
            return false;
        }
        Map<String, Boolean> row = root.get(questId.trim());
        if (row == null) {
            return false;
        }
        return Boolean.TRUE.equals(row.get(objectiveId.trim()));
    }

    public int getQuestKillCount(@Nonnull String questId, @Nonnull String objectiveId) {
        Map<String, Map<String, Integer>> root = questKillProgress;
        if (root == null) {
            return 0;
        }
        Map<String, Integer> row = root.get(questId.trim());
        if (row == null) {
            return 0;
        }
        Integer v = row.get(objectiveId.trim());
        return v != null ? v : 0;
    }

    public void setQuestKillCount(@Nonnull String questId, @Nonnull String objectiveId, int count) {
        Map<String, Integer> row = killProgressMutable().computeIfAbsent(questId.trim(), k -> new LinkedHashMap<>());
        row.put(objectiveId.trim(), Math.max(0, count));
    }

    @Nonnull
    public VillagerReputationEntry reputationForPlacement(@Nonnull String placementId) {
        Map<String, VillagerReputationEntry> map = reputationMutable();
        return map.computeIfAbsent(placementId.trim(), k -> new VillagerReputationEntry());
    }

    @Nullable
    public VillagerReputationEntry findReputation(@Nonnull String placementId) {
        Map<String, VillagerReputationEntry> map = reputationByPlacementId;
        if (map == null) {
            return null;
        }
        return map.get(placementId.trim());
    }

    public long giftLastWeek(@Nonnull String placementId) {
        Map<String, Long> map = giftLastWeekByPlacementId;
        if (map == null) {
            return 0L;
        }
        Long v = map.get(placementId.trim());
        return v != null ? v : 0L;
    }

    public void setGiftLastWeek(@Nonnull String placementId, long weekId) {
        if (giftLastWeekByPlacementId == null) {
            giftLastWeekByPlacementId = new LinkedHashMap<>();
        }
        giftLastWeekByPlacementId.put(placementId.trim(), weekId);
    }

    public int giftCountThisWeek(@Nonnull String placementId) {
        Map<String, Integer> map = giftCountThisWeekByPlacementId;
        if (map == null) {
            return 0;
        }
        Integer v = map.get(placementId.trim());
        return v != null ? v : 0;
    }

    public void setGiftCountThisWeek(@Nonnull String placementId, int count) {
        if (giftCountThisWeekByPlacementId == null) {
            giftCountThisWeekByPlacementId = new LinkedHashMap<>();
        }
        giftCountThisWeekByPlacementId.put(placementId.trim(), Math.max(0, count));
    }

    @Nonnull
    public List<QuestBoardSlotRecord> allBoardSlotsFlat() {
        List<QuestBoardSlotRecord> out = new ArrayList<>();
        if (boardSlotsByProfileId == null) {
            return out;
        }
        for (List<QuestBoardSlotRecord> slots : boardSlotsByProfileId.values()) {
            if (slots != null) {
                out.addAll(slots);
            }
        }
        return out;
    }

    @Nonnull
    public List<QuestBoardSlotRecord> boardSlots(@Nonnull String profileId) {
        Map<String, List<QuestBoardSlotRecord>> map = boardSlotsMutable();
        return map.computeIfAbsent(profileId.trim(), k -> new ArrayList<>());
    }

    public int boardRankXp(@Nonnull String profileId) {
        Map<String, Integer> map = boardRankXpByProfileId;
        if (map == null) {
            return 0;
        }
        Integer v = map.get(profileId.trim());
        return v != null ? v : 0;
    }

    public void addBoardRankXp(@Nonnull String profileId, int delta) {
        Map<String, Integer> map = boardRankXpMutable();
        String key = profileId.trim();
        map.put(key, Math.max(0, boardRankXp(key) + delta));
    }

    public void setBoardRankXp(@Nonnull String profileId, int xp) {
        boardRankXpMutable().put(profileId.trim(), Math.max(0, xp));
    }

    @Nonnull
    public List<String> boardDrawPool(@Nonnull String profileId) {
        Map<String, List<String>> map = boardDrawPoolMutable();
        return map.computeIfAbsent(profileId.trim(), k -> new ArrayList<>());
    }

    @Nullable
    public String peekPendingRewardNode(@Nonnull String placementId) {
        Map<String, String> map = pendingRewardNodeByPlacementId;
        if (map == null) {
            return null;
        }
        String v = map.get(placementId.trim());
        return v != null && !v.isBlank() ? v.trim() : null;
    }

    public void setPendingRewardNode(@Nonnull String placementId, @Nullable String nodeId) {
        if (pendingRewardNodeByPlacementId == null) {
            pendingRewardNodeByPlacementId = new LinkedHashMap<>();
        }
        if (nodeId == null || nodeId.isBlank()) {
            pendingRewardNodeByPlacementId.remove(placementId.trim());
        } else {
            pendingRewardNodeByPlacementId.put(placementId.trim(), nodeId.trim());
        }
    }

    @Nullable
    public String peekPendingMainHubBody(@Nonnull String placementId) {
        Map<String, String> map = pendingMainHubBodyByPlacementId;
        if (map == null) {
            return null;
        }
        String v = map.get(placementId.trim());
        return v != null && !v.isBlank() ? v.trim() : null;
    }

    public void setPendingMainHubBody(@Nonnull String placementId, @Nullable String langKey) {
        if (pendingMainHubBodyByPlacementId == null) {
            pendingMainHubBodyByPlacementId = new LinkedHashMap<>();
        }
        if (langKey == null || langKey.isBlank()) {
            pendingMainHubBodyByPlacementId.remove(placementId.trim());
        } else {
            pendingMainHubBodyByPlacementId.put(placementId.trim(), langKey.trim());
        }
    }

    @Nullable
    public String findBoardProfileIdForInstance(@Nonnull String instanceId) {
        String want = instanceId.trim();
        if (want.isEmpty() || boardSlotsByProfileId == null) {
            return null;
        }
        for (Map.Entry<String, List<QuestBoardSlotRecord>> e : boardSlotsByProfileId.entrySet()) {
            List<QuestBoardSlotRecord> slots = e.getValue();
            if (slots == null) {
                continue;
            }
            for (QuestBoardSlotRecord slot : slots) {
                if (slot != null && want.equals(slot.getInstanceId())) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    @Nonnull
    public List<QuestBoardSlotRecord> acceptedBoardSlotsSnapshot() {
        List<QuestBoardSlotRecord> out = new ArrayList<>();
        for (QuestBoardSlotRecord slot : allBoardSlotsFlat()) {
            if (slot != null && slot.isAccepted()) {
                out.add(slot);
            }
        }
        return out;
    }

    @Nonnull
    private static Set<String> normalizedSet(@Nullable List<String> raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    @Nonnull
    private Map<String, Map<String, Boolean>> objectiveProgressMutable() {
        if (questObjectiveProgress == null) {
            questObjectiveProgress = new LinkedHashMap<>();
        }
        return questObjectiveProgress;
    }

    @Nonnull
    private Map<String, Map<String, Integer>> killProgressMutable() {
        if (questKillProgress == null) {
            questKillProgress = new LinkedHashMap<>();
        }
        return questKillProgress;
    }

    @Nonnull
    private Map<String, VillagerReputationEntry> reputationMutable() {
        if (reputationByPlacementId == null) {
            reputationByPlacementId = new LinkedHashMap<>();
        }
        return reputationByPlacementId;
    }

    @Nonnull
    private Map<String, List<QuestBoardSlotRecord>> boardSlotsMutable() {
        if (boardSlotsByProfileId == null) {
            boardSlotsByProfileId = new LinkedHashMap<>();
        }
        return boardSlotsByProfileId;
    }

    @Nonnull
    private Map<String, Integer> boardRankXpMutable() {
        if (boardRankXpByProfileId == null) {
            boardRankXpByProfileId = new LinkedHashMap<>();
        }
        return boardRankXpByProfileId;
    }

    @Nonnull
    private Map<String, List<String>> boardDrawPoolMutable() {
        if (boardDrawPoolByProfileId == null) {
            boardDrawPoolByProfileId = new LinkedHashMap<>();
        }
        return boardDrawPoolByProfileId;
    }
}
