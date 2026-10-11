package com.hexvane.aetherhaven.questboard;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestBoardSlotRecord {
    @SerializedName("instanceId")
    @Nullable
    private String instanceId;

    @SerializedName("state")
    @Nullable
    private String state;

    @SerializedName("questType")
    @Nullable
    private String questType;

    @SerializedName("giverEntityUuid")
    @Nullable
    private String giverEntityUuid;

    @SerializedName("giverRoleId")
    @Nullable
    private String giverRoleId;

    @SerializedName("questRank")
    @Nullable
    private String questRank;

    @SerializedName("titleLangKey")
    @Nullable
    private String titleLangKey;

    @SerializedName("descriptionLangKey")
    @Nullable
    private String descriptionLangKey;

    @SerializedName("configEntryId")
    @Nullable
    private String configEntryId;

    @SerializedName("requiredItems")
    @Nullable
    private List<QuestBoardItemRequirement> requiredItems;

    @SerializedName("rewards")
    @Nullable
    private List<QuestReward> rewards;

    @SerializedName("rankXpReward")
    private int rankXpReward;

    @SerializedName("daysLimit")
    private int daysLimit;

    @SerializedName("acceptedByPlayerUuid")
    @Nullable
    private String acceptedByPlayerUuid;

    @SerializedName("onlineDaysElapsed")
    private int onlineDaysElapsed;

    @SerializedName("generationSeed")
    private long generationSeed;

    @SerializedName("huntEntityTagsAny")
    @Nullable
    private List<String> huntEntityTagsAny;

    /** Set on mining contracts, which otherwise behave as hunts: the same counter, turn in and journal entry. */
    @SerializedName("mineBlockIdPrefixesAny")
    @Nullable
    private List<String> mineBlockIdPrefixesAny;

    @SerializedName("huntTargetLabelLangKey")
    @Nullable
    private String huntTargetLabelLangKey;

    @SerializedName("huntKillRequired")
    private int huntKillRequired;

    @SerializedName("huntKillProgress")
    private int huntKillProgress;

    @SerializedName("raidTargetLabelLangKey")
    @Nullable
    private String raidTargetLabelLangKey;

    @SerializedName("raidKillRequired")
    private int raidKillRequired;

    @SerializedName("raidKillProgress")
    private int raidKillProgress;

    @SerializedName("raidSpawnedEntityUuids")
    @Nullable
    private List<String> raidSpawnedEntityUuids;

    @SerializedName("raidMobRoleIds")
    @Nullable
    private List<String> raidMobRoleIds;

    @SerializedName("raidApproachDirection")
    @Nullable
    private String raidApproachDirection;

    /** Rescue contracts are raids on a temporary camp. Set when the offer is posted, so the board can show who is held. */
    @SerializedName("rescueCharacterId")
    @Nullable
    private String rescueCharacterId;

    /** Skills (by id) the rescued townsfolk gains one level in when they move in. */
    @SerializedName("rescueSkillBoosts")
    @Nullable
    private List<String> rescueSkillBoosts;

    @SerializedName("rescueCampPrefabs")
    @Nullable
    private List<String> rescueCampPrefabs;

    /** World event that holds the camp, the camp origin and the captive. Set only while the contract is accepted. */
    @SerializedName("rescueEventId")
    @Nullable
    private String rescueEventId;

    @SerializedName("rescueCamp")
    @Nullable
    private int[] rescueCamp;

    @SerializedName("rescueCaptiveUuid")
    @Nullable
    private String rescueCaptiveUuid;

    /** True once a player has cut the captive's ropes. Only then can the contract be finished by talking to them. */
    @SerializedName("rescueUntied")
    private boolean rescueUntied;

    public boolean isRescueUntied() { return rescueUntied; }

    public void setRescueUntied(boolean untied) { this.rescueUntied = untied; }

    public boolean isRescueQuest() {
        return isRaidQuest() && rescueCharacterId != null && !rescueCharacterId.isBlank();
    }

    @Nullable
    public String getRescueCharacterId() { return rescueCharacterId; }

    public void setRescueCharacterId(@Nullable String id) { this.rescueCharacterId = id; }

    @Nonnull
    public List<String> rescueSkillBoostsOrEmpty() { return rescueSkillBoosts != null ? rescueSkillBoosts : List.of(); }

    public void setRescueSkillBoosts(@Nonnull List<String> skills) { this.rescueSkillBoosts = new ArrayList<>(skills); }

    @Nonnull
    public List<String> rescueCampPrefabsOrEmpty() { return rescueCampPrefabs != null ? rescueCampPrefabs : List.of(); }

    public void setRescueCampPrefabs(@Nonnull List<String> prefabs) { this.rescueCampPrefabs = new ArrayList<>(prefabs); }

    @Nullable
    public String getRescueEventId() { return rescueEventId; }

    public void setRescueEventId(@Nullable String id) { this.rescueEventId = id; }

    /** Camp origin as x, y, z, or null before the contract is accepted. */
    @Nullable
    public int[] getRescueCamp() { return rescueCamp != null && rescueCamp.length == 3 ? rescueCamp : null; }

    public void setRescueCamp(@Nullable int[] camp) { this.rescueCamp = camp; }

    /** Where the captive sits beside the camp. They are only placed there once a player comes near. */
    @SerializedName("rescueCaptiveSpot")
    @Nullable
    private int[] rescueCaptiveSpot;

    @Nullable
    public int[] getRescueCaptiveSpot() { return rescueCaptiveSpot != null && rescueCaptiveSpot.length == 3 ? rescueCaptiveSpot : null; }

    public void setRescueCaptiveSpot(@Nullable int[] spot) { this.rescueCaptiveSpot = spot; }

    @Nullable
    public String getRescueCaptiveUuid() { return rescueCaptiveUuid; }

    public void setRescueCaptiveUuid(@Nullable String uuid) { this.rescueCaptiveUuid = uuid; }

    @Nonnull
    public static QuestBoardSlotRecord empty() {
        QuestBoardSlotRecord r = new QuestBoardSlotRecord();
        r.state = QuestBoardSlotState.EMPTY.name();
        return r;
    }

    @Nonnull
    public QuestBoardSlotState stateEnum() {
        return QuestBoardSlotState.fromString(state);
    }

    public void setState(@Nonnull QuestBoardSlotState s) {
        this.state = s.name();
    }

    /** Restores an accepted slot back to offer state after a failed accept (e.g. raid spawn failure). */
    public void revertAcceptance() {
        state = QuestBoardSlotState.OFFER.name();
        acceptedByPlayerUuid = null;
        onlineDaysElapsed = 0;
        huntKillProgress = 0;
        raidKillProgress = 0;
        raidSpawnedEntityUuids = null;
        rescueEventId = null;
        rescueCamp = null;
        rescueCaptiveSpot = null;
        rescueCaptiveUuid = null;
        rescueUntied = false;
    }

    public void clearToEmpty() {
        instanceId = null;
        state = QuestBoardSlotState.EMPTY.name();
        questType = null;
        giverEntityUuid = null;
        giverRoleId = null;
        questRank = null;
        titleLangKey = null;
        descriptionLangKey = null;
        configEntryId = null;
        requiredItems = null;
        rewards = null;
        rankXpReward = 0;
        daysLimit = 0;
        acceptedByPlayerUuid = null;
        onlineDaysElapsed = 0;
        generationSeed = 0L;
        huntEntityTagsAny = null;
        mineBlockIdPrefixesAny = null;
        huntTargetLabelLangKey = null;
        huntKillRequired = 0;
        huntKillProgress = 0;
        raidTargetLabelLangKey = null;
        raidKillRequired = 0;
        raidKillProgress = 0;
        raidSpawnedEntityUuids = null;
        raidMobRoleIds = null;
        raidApproachDirection = null;
        rescueUntied = false;
        rescueCharacterId = null;
        rescueSkillBoosts = null;
        rescueCampPrefabs = null;
        rescueEventId = null;
        rescueCamp = null;
        rescueCaptiveSpot = null;
        rescueCaptiveUuid = null;
    }

    @Nullable
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(@Nonnull String id) {
        this.instanceId = id;
    }

    @Nonnull
    public String instanceIdOrEmpty() {
        return instanceId != null ? instanceId.trim() : "";
    }

    @Nullable
    public String getQuestType() {
        return questType;
    }

    public void setQuestType(@Nullable String questType) {
        this.questType = questType;
    }

    @Nullable
    public String getGiverEntityUuid() {
        return giverEntityUuid;
    }

    public void setGiverEntityUuid(@Nullable String giverEntityUuid) {
        this.giverEntityUuid = giverEntityUuid;
    }

    @Nullable
    public String getGiverRoleId() {
        return giverRoleId;
    }

    public void setGiverRoleId(@Nullable String giverRoleId) {
        this.giverRoleId = giverRoleId;
    }

    @Nullable
    public String getQuestRank() {
        return questRank;
    }

    public void setQuestRank(@Nullable String questRank) {
        this.questRank = questRank;
    }

    @Nullable
    public String getTitleLangKey() {
        return titleLangKey;
    }

    public void setTitleLangKey(@Nullable String titleLangKey) {
        this.titleLangKey = titleLangKey;
    }

    @Nullable
    public String getDescriptionLangKey() {
        return descriptionLangKey;
    }

    public void setDescriptionLangKey(@Nullable String descriptionLangKey) {
        this.descriptionLangKey = descriptionLangKey;
    }

    @Nullable
    public String getConfigEntryId() {
        return configEntryId;
    }

    public void setConfigEntryId(@Nullable String configEntryId) {
        this.configEntryId = configEntryId;
    }

    @Nonnull
    public List<QuestBoardItemRequirement> requiredItemsOrEmpty() {
        if (requiredItems == null) {
            requiredItems = new ArrayList<>();
        }
        return requiredItems;
    }

    public void setRequiredItems(@Nonnull List<QuestBoardItemRequirement> items) {
        this.requiredItems = new ArrayList<>(items);
    }

    @Nonnull
    public List<QuestReward> rewardsOrEmpty() {
        if (rewards == null) {
            rewards = new ArrayList<>();
        }
        return rewards;
    }

    public void setRewards(@Nonnull List<QuestReward> rewards) {
        this.rewards = new ArrayList<>(rewards);
    }

    public int getRankXpReward() {
        return rankXpReward;
    }

    public void setRankXpReward(int rankXpReward) {
        this.rankXpReward = Math.max(0, rankXpReward);
    }

    public int getDaysLimit() {
        return Math.max(1, daysLimit);
    }

    public void setDaysLimit(int daysLimit) {
        this.daysLimit = daysLimit;
    }

    @Nullable
    public String getAcceptedByPlayerUuid() {
        return acceptedByPlayerUuid;
    }

    public void setAcceptedByPlayerUuid(@Nullable String acceptedByPlayerUuid) {
        this.acceptedByPlayerUuid = acceptedByPlayerUuid;
    }

    public int getOnlineDaysElapsed() {
        return onlineDaysElapsed;
    }

    public void setOnlineDaysElapsed(int onlineDaysElapsed) {
        this.onlineDaysElapsed = Math.max(0, onlineDaysElapsed);
    }

    public long getGenerationSeed() {
        return generationSeed;
    }

    public void setGenerationSeed(long generationSeed) {
        this.generationSeed = generationSeed;
    }

    @Nonnull
    public List<String> huntEntityTagsAnyOrEmpty() {
        if (huntEntityTagsAny == null) {
            huntEntityTagsAny = new ArrayList<>();
        }
        return huntEntityTagsAny;
    }

    public void setHuntEntityTagsAny(@Nonnull List<String> tags) {
        this.huntEntityTagsAny = new ArrayList<>(tags);
    }

    @Nullable
    public String getHuntTargetLabelLangKey() {
        return huntTargetLabelLangKey;
    }

    public void setHuntTargetLabelLangKey(@Nullable String huntTargetLabelLangKey) {
        this.huntTargetLabelLangKey = huntTargetLabelLangKey;
    }

    public int getHuntKillRequired() {
        return Math.max(0, huntKillRequired);
    }

    public void setHuntKillRequired(int huntKillRequired) {
        this.huntKillRequired = Math.max(0, huntKillRequired);
    }

    public int getHuntKillProgress() {
        return Math.max(0, huntKillProgress);
    }

    public void setHuntKillProgress(int huntKillProgress) {
        this.huntKillProgress = Math.max(0, huntKillProgress);
    }

    @Nonnull
    public List<String> mineBlockIdPrefixesAnyOrEmpty() {
        return mineBlockIdPrefixesAny != null ? mineBlockIdPrefixesAny : List.of();
    }

    public void setMineBlockIdPrefixesAny(@Nonnull List<String> prefixes) {
        this.mineBlockIdPrefixesAny = prefixes.isEmpty() ? null : new ArrayList<>(prefixes);
    }

    /** A hunt whose target is ore the player mines, not creatures the player kills. */
    public boolean isMineQuest() {
        return isHuntQuest() && mineBlockIdPrefixesAny != null && !mineBlockIdPrefixesAny.isEmpty();
    }

    public boolean isHuntQuest() {
        return questType != null && "hunt".equalsIgnoreCase(questType.trim());
    }

    public boolean isRaidQuest() {
        return questType != null && "raid".equalsIgnoreCase(questType.trim());
    }

    @Nullable
    public String getRaidTargetLabelLangKey() {
        return raidTargetLabelLangKey;
    }

    public void setRaidTargetLabelLangKey(@Nullable String raidTargetLabelLangKey) {
        this.raidTargetLabelLangKey = raidTargetLabelLangKey;
    }

    public int getRaidKillRequired() {
        return Math.max(0, raidKillRequired);
    }

    public void setRaidKillRequired(int raidKillRequired) {
        this.raidKillRequired = Math.max(0, raidKillRequired);
    }

    public int getRaidKillProgress() {
        return Math.max(0, raidKillProgress);
    }

    public void setRaidKillProgress(int raidKillProgress) {
        this.raidKillProgress = Math.max(0, raidKillProgress);
    }

    @Nonnull
    public List<String> raidSpawnedEntityUuidsOrEmpty() {
        if (raidSpawnedEntityUuids == null) {
            raidSpawnedEntityUuids = new ArrayList<>();
        }
        return raidSpawnedEntityUuids;
    }

    public void setRaidSpawnedEntityUuids(@Nonnull List<String> uuids) {
        this.raidSpawnedEntityUuids = new ArrayList<>(uuids);
    }

    @Nonnull
    public List<String> raidMobRoleIdsOrEmpty() {
        if (raidMobRoleIds == null) {
            raidMobRoleIds = new ArrayList<>();
        }
        return raidMobRoleIds;
    }

    public void setRaidMobRoleIds(@Nonnull List<String> roleIds) {
        this.raidMobRoleIds = new ArrayList<>(roleIds);
    }

    @Nullable
    public String getRaidApproachDirection() {
        return raidApproachDirection;
    }

    public void setRaidApproachDirection(@Nullable String raidApproachDirection) {
        this.raidApproachDirection = raidApproachDirection;
    }

    @Nonnull
    public RaidApproachDirection raidApproachDirectionEnum() {
        RaidApproachDirection parsed = RaidApproachDirection.fromId(raidApproachDirection);
        return parsed != null ? parsed : RaidApproachDirection.NORTH;
    }

    public boolean isAccepted() {
        return stateEnum() == QuestBoardSlotState.ACCEPTED;
    }

    public boolean isCompleted() {
        return stateEnum() == QuestBoardSlotState.COMPLETED;
    }

    public void markCompleted() {
        setState(QuestBoardSlotState.COMPLETED);
        setAcceptedByPlayerUuid(null);
        setOnlineDaysElapsed(0);
    }

    public boolean hasOffer() {
        QuestBoardSlotState s = stateEnum();
        return s == QuestBoardSlotState.OFFER || s == QuestBoardSlotState.ACCEPTED;
    }

    public boolean occupiesBoardSlot() {
        QuestBoardSlotState s = stateEnum();
        return s == QuestBoardSlotState.OFFER || s == QuestBoardSlotState.ACCEPTED || s == QuestBoardSlotState.COMPLETED;
    }

    @Nonnull
    public static String newInstanceId() {
        return UUID.randomUUID().toString();
    }
}
