package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRaidEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRaidGuaranteedMobJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRaidMobPoolEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRaidSetJson;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class RaidQuestBoardHandler implements QuestBoardQuestTypeHandler {
    public static final String TYPE_ID = "raid";

    private static final Map<String, Integer> DEFAULT_MOB_COUNTS_BY_RANK = Map.of(
        "E", 4,
        "D", 6,
        "C", 8,
        "B", 10,
        "A", 12,
        "S", 15,
        "SS", 18,
        "SSS", 22
    );

    @Override
    @Nonnull
    public String typeId() {
        return TYPE_ID;
    }

    public boolean populateSlot(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull String giverRoleId,
        @Nonnull String giverEntityUuid,
        @Nonnull QuestBoardRaidEntryJson entry,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull Random rng
    ) {
        QuestBoardRaidSetJson raidSet = pickWeightedRaidSet(entry, rng);
        if (raidSet == null
            || (raidSet.mobPoolOrEmpty().isEmpty() && raidSet.guaranteedMobsOrEmpty().isEmpty())) {
            return false;
        }
        String rank = entry.rank() != null ? entry.rank().trim() : "E";
        List<String> roster = buildMobRoster(raidSet, rank, rng);
        if (roster.isEmpty()) {
            return false;
        }

        slot.setInstanceId(QuestBoardSlotRecord.newInstanceId());
        slot.setState(QuestBoardSlotState.OFFER);
        slot.setQuestType(TYPE_ID);
        slot.setGiverRoleId(giverRoleId);
        slot.setGiverEntityUuid(giverEntityUuid);
        slot.setQuestRank(rank);
        if (entry.id() != null && !entry.id().isBlank()) {
            slot.setConfigEntryId(entry.id().trim());
        }
        if (entry.titleLangKey() != null && !entry.titleLangKey().isBlank()) {
            slot.setTitleLangKey(entry.titleLangKey().trim());
        }
        if (entry.descriptionLangKey() != null && !entry.descriptionLangKey().isBlank()) {
            slot.setDescriptionLangKey(entry.descriptionLangKey().trim());
        }
        slot.setRequiredItems(new ArrayList<>());
        slot.setRewards(new ArrayList<>(entry.rewardsOrEmpty()));
        slot.setDaysLimit(entry.daysLimit());
        int xp = entry.rankXpReward() > 0 ? entry.rankXpReward() : catalog.defaultXpRewardForRank(rank);
        slot.setRankXpReward(xp);
        slot.setGenerationSeed(rng.nextLong());
        slot.setAcceptedByPlayerUuid(null);
        slot.setOnlineDaysElapsed(0);
        slot.setRaidMobRoleIds(new ArrayList<>(roster));
        slot.setRaidKillRequired(roster.size());
        slot.setRaidKillProgress(0);
        slot.setRaidSpawnedEntityUuids(new ArrayList<>());
        slot.setRaidApproachDirection(RaidApproachDirection.random(rng).id());
        if (entry.targetLabelLangKey() != null && !entry.targetLabelLangKey().isBlank()) {
            slot.setRaidTargetLabelLangKey(entry.targetLabelLangKey().trim());
        }
        return true;
    }

    @Override
    public boolean populateSlot(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull String giverRoleId,
        @Nonnull String giverEntityUuid,
        @Nonnull QuestBoardFetchEntryJson entry,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull Random rng
    ) {
        return false;
    }

    @Nonnull
    static List<String> buildMobRoster(@Nonnull QuestBoardRaidSetJson raidSet, @Nonnull String rank, @Nonnull Random rng) {
        List<QuestBoardRaidMobPoolEntryJson> pool = raidSet.mobPoolOrEmpty().stream()
            .filter(e -> e.roleId() != null && !e.roleId().isBlank())
            .toList();
        int totalCount = resolveMobCount(raidSet, rank);
        if (totalCount <= 0) {
            return List.of();
        }

        List<String> roster = new ArrayList<>();
        Set<String> guaranteedRoleIds = new HashSet<>();
        for (QuestBoardRaidGuaranteedMobJson guaranteed : raidSet.guaranteedMobsOrEmpty()) {
            if (guaranteed.roleId() == null || guaranteed.roleId().isBlank()) {
                continue;
            }
            String roleId = guaranteed.roleId().trim();
            guaranteedRoleIds.add(roleId);
            for (int i = 0; i < guaranteed.count() && roster.size() < totalCount; i++) {
                roster.add(roleId);
            }
        }

        List<QuestBoardRaidMobPoolEntryJson> fillPool = pool.stream()
            .filter(e -> !guaranteedRoleIds.contains(e.roleId().trim()))
            .toList();
        if (fillPool.isEmpty() && roster.isEmpty()) {
            return List.of();
        }

        int distinctTarget = Math.min(3, Math.min(totalCount - roster.size(), fillPool.size()));
        List<QuestBoardRaidMobPoolEntryJson> remaining = new ArrayList<>(fillPool);

        for (int i = 0; i < distinctTarget; i++) {
            QuestBoardRaidMobPoolEntryJson picked = pickWeightedPoolEntry(remaining, rng);
            if (picked == null) {
                break;
            }
            roster.add(picked.roleId().trim());
            remaining.remove(picked);
        }
        while (roster.size() < totalCount) {
            QuestBoardRaidMobPoolEntryJson picked = pickWeightedPoolEntry(fillPool, rng);
            if (picked == null) {
                break;
            }
            roster.add(picked.roleId().trim());
        }
        return roster;
    }

    static int resolveMobCount(@Nonnull QuestBoardRaidSetJson raidSet, @Nonnull String rank) {
        String r = rank.trim().toUpperCase();
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, Integer> e : raidSet.mobCountsByRankOrEmpty().entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                counts.put(e.getKey().trim().toUpperCase(), Math.max(1, e.getValue()));
            }
        }
        Integer configured = counts.get(r);
        if (configured != null) {
            return configured;
        }
        Integer fallback = DEFAULT_MOB_COUNTS_BY_RANK.get(r);
        return fallback != null ? fallback : 6;
    }

    @Nullable
    private static QuestBoardRaidSetJson pickWeightedRaidSet(@Nonnull QuestBoardRaidEntryJson entry, @Nonnull Random rng) {
        List<QuestBoardRaidSetJson> sets = entry.raidSetsOrEmpty();
        if (sets.isEmpty()) {
            return null;
        }
        int total = 0;
        for (QuestBoardRaidSetJson s : sets) {
            total += s.weight();
        }
        if (total <= 0) {
            return sets.get(0);
        }
        int roll = rng.nextInt(total);
        int acc = 0;
        for (QuestBoardRaidSetJson s : sets) {
            acc += s.weight();
            if (roll < acc) {
                return s;
            }
        }
        return sets.get(sets.size() - 1);
    }

    @Nullable
    private static QuestBoardRaidMobPoolEntryJson pickWeightedPoolEntry(
        @Nonnull List<QuestBoardRaidMobPoolEntryJson> pool,
        @Nonnull Random rng
    ) {
        if (pool.isEmpty()) {
            return null;
        }
        int total = 0;
        for (QuestBoardRaidMobPoolEntryJson e : pool) {
            total += e.weight();
        }
        if (total <= 0) {
            return pool.get(rng.nextInt(pool.size()));
        }
        int roll = rng.nextInt(total);
        int acc = 0;
        for (QuestBoardRaidMobPoolEntryJson e : pool) {
            acc += e.weight();
            if (roll < acc) {
                return e;
            }
        }
        return pool.get(pool.size() - 1);
    }

    @Override
    @Nonnull
    public Message displayTitle(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog
    ) {
        String key = slot.getTitleLangKey();
        String villager = QuestBoardGiverDisplay.giverName(slot, store, town);
        if (key != null && !key.isBlank()) {
            return Message.translation(key.trim()).param("villager", villager);
        }
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.raidFallbackTitle")
            .param("villager", villager);
    }

    @Override
    @Nonnull
    public Message displayDescription(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog
    ) {
        String key = slot.getDescriptionLangKey();
        String villager = QuestBoardGiverDisplay.giverName(slot, store, town);
        Message direction = raidApproachDirectionLabel(slot);
        if (key != null && !key.isBlank()) {
            return Message.translation(key.trim()).param("villager", villager).param("direction", direction);
        }
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.raidFallbackDescription")
            .param("villager", villager)
            .param("direction", direction);
    }

    @Override
    @Nonnull
    public Message objectivesText(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog
    ) {
        String villager = QuestBoardGiverDisplay.giverName(slot, store, town);
        Message target = raidTargetLabel(slot);
        Message direction = raidApproachDirectionLabel(slot);
        int need = slot.getRaidKillRequired();
        int cur = slot.getRaidKillProgress();
        if (slot.isAccepted() && need > 0) {
            return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.raidObjectiveProgress")
                .param("current", String.valueOf(cur))
                .param("need", String.valueOf(need))
                .param("target", target)
                .param("direction", direction)
                .param("villager", villager);
        }
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.raidObjective")
            .param("count", String.valueOf(need))
            .param("target", target)
            .param("direction", direction)
            .param("villager", villager);
    }

    @Nonnull
    public static Message raidTargetLabel(@Nonnull QuestBoardSlotRecord slot) {
        String key = slot.getRaidTargetLabelLangKey();
        if (key != null && !key.isBlank()) {
            return Message.translation(key.trim());
        }
        return Message.raw("raiders");
    }

    @Nonnull
    public static Message raidApproachDirectionLabel(@Nonnull QuestBoardSlotRecord slot) {
        return slot.raidApproachDirectionEnum().displayLabel();
    }

    @Nonnull
    public static Message raidObjectiveCardText(@Nonnull QuestBoardSlotRecord slot) {
        Message target = raidTargetLabel(slot);
        Message direction = raidApproachDirectionLabel(slot);
        int need = slot.getRaidKillRequired();
        if (slot.isAccepted() && need > 0) {
            return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.raidObjectiveCardProgress")
                .param("current", String.valueOf(slot.getRaidKillProgress()))
                .param("count", String.valueOf(need))
                .param("target", target)
                .param("direction", direction);
        }
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.raidObjectiveCard")
            .param("count", String.valueOf(need))
            .param("target", target)
            .param("direction", direction);
    }

    @Override
    public boolean hasRequiredItems(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull QuestBoardSlotRecord slot) {
        int need = slot.getRaidKillRequired();
        if (need <= 0) {
            return false;
        }
        return slot.getRaidKillProgress() >= need;
    }

    @Override
    public boolean consumeRequiredItems(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull QuestBoardSlotRecord slot) {
        return hasRequiredItems(playerRef, store, slot);
    }
}
