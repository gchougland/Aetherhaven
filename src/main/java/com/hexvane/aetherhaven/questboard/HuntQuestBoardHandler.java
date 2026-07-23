package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardHuntEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardKillSetJson;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class HuntQuestBoardHandler implements QuestBoardQuestTypeHandler {
    public static final String TYPE_ID = "hunt";

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
        @Nonnull QuestBoardHuntEntryJson entry,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull Random rng
    ) {
        QuestBoardKillSetJson killSet = pickWeightedKillSet(entry, rng);
        if (killSet == null || killSet.entityTagsAnyOrEmpty().isEmpty()) {
            return false;
        }
        slot.setInstanceId(QuestBoardSlotRecord.newInstanceId());
        slot.setState(QuestBoardSlotState.OFFER);
        slot.setQuestType(TYPE_ID);
        slot.setGiverRoleId(giverRoleId);
        slot.setGiverEntityUuid(giverEntityUuid);
        slot.setQuestRank(entry.rank() != null ? entry.rank().trim() : "E");
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
        slot.setRewards(
            QuestBoardGoldRewardScaling.applyGoldCoinMultiplier(
                entry.rewardsOrEmpty(),
                catalog.goldCoinMultiplierForType(TYPE_ID)
            )
        );
        slot.setDaysLimit(entry.daysLimit());
        String rank = slot.getQuestRank() != null ? slot.getQuestRank() : "E";
        int xp = entry.rankXpReward() > 0 ? entry.rankXpReward() : catalog.defaultXpRewardForRank(rank);
        slot.setRankXpReward(xp);
        slot.setGenerationSeed(rng.nextLong());
        slot.setAcceptedByPlayerUuid(null);
        slot.setOnlineDaysElapsed(0);
        slot.setHuntEntityTagsAny(new ArrayList<>(killSet.entityTagsAnyOrEmpty()));
        slot.setHuntKillRequired(killSet.killCount());
        slot.setHuntKillProgress(0);
        if (entry.targetLabelLangKey() != null && !entry.targetLabelLangKey().isBlank()) {
            slot.setHuntTargetLabelLangKey(entry.targetLabelLangKey().trim());
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

    @Nullable
    private static QuestBoardKillSetJson pickWeightedKillSet(@Nonnull QuestBoardHuntEntryJson entry, @Nonnull Random rng) {
        List<QuestBoardKillSetJson> sets = entry.killSetsOrEmpty();
        if (sets.isEmpty()) {
            return null;
        }
        int total = 0;
        for (QuestBoardKillSetJson s : sets) {
            total += s.weight();
        }
        if (total <= 0) {
            return sets.get(0);
        }
        int roll = rng.nextInt(total);
        int acc = 0;
        for (QuestBoardKillSetJson s : sets) {
            acc += s.weight();
            if (roll < acc) {
                return s;
            }
        }
        return sets.get(sets.size() - 1);
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
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.huntFallbackTitle")
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
        if (key != null && !key.isBlank()) {
            return Message.translation(key.trim()).param("villager", villager);
        }
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.huntFallbackDescription")
            .param("villager", villager);
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
        Message target = huntTargetLabel(slot);
        int need = slot.getHuntKillRequired();
        int cur = slot.getHuntKillProgress();
        if (slot.isAccepted() && need > 0) {
            return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.huntObjectiveProgress")
                .param("current", String.valueOf(cur))
                .param("need", String.valueOf(need))
                .param("target", target)
                .param("villager", villager);
        }
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.huntObjective")
            .param("count", String.valueOf(need))
            .param("target", target)
            .param("villager", villager);
    }

    @Nonnull
    public static Message huntTargetLabel(@Nonnull QuestBoardSlotRecord slot) {
        String key = slot.getHuntTargetLabelLangKey();
        if (key != null && !key.isBlank()) {
            return Message.translation(key.trim());
        }
        return Message.raw("foes");
    }

    @Nonnull
    public static Message huntObjectiveCardText(@Nonnull QuestBoardSlotRecord slot) {
        Message target = huntTargetLabel(slot);
        int need = slot.getHuntKillRequired();
        if (slot.isAccepted() && need > 0) {
            return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.huntObjectiveCardProgress")
                .param("current", String.valueOf(slot.getHuntKillProgress()))
                .param("count", String.valueOf(need))
                .param("target", target);
        }
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.huntObjectiveCard")
            .param("count", String.valueOf(need))
            .param("target", target);
    }

    @Override
    public boolean hasRequiredItems(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull QuestBoardSlotRecord slot) {
        int need = slot.getHuntKillRequired();
        if (need <= 0) {
            return false;
        }
        return slot.getHuntKillProgress() >= need;
    }

    @Override
    public boolean consumeRequiredItems(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull QuestBoardSlotRecord slot) {
        return hasRequiredItems(playerRef, store, slot);
    }
}
