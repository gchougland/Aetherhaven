package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardItemSetJson;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.UiMaterialLabels;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FetchQuestBoardHandler implements QuestBoardQuestTypeHandler {
    public static final String TYPE_ID = "fetch";

    @Override
    @Nonnull
    public String typeId() {
        return TYPE_ID;
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
        QuestBoardItemSetJson itemSet = pickWeightedItemSet(entry, rng);
        if (itemSet == null || itemSet.itemsOrEmpty().isEmpty()) {
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
        slot.setRequiredItems(new ArrayList<>(itemSet.itemsOrEmpty()));
        slot.setRewards(new ArrayList<>(entry.rewardsOrEmpty()));
        slot.setDaysLimit(entry.daysLimit());
        String rank = slot.getQuestRank() != null ? slot.getQuestRank() : "E";
        int xp = entry.rankXpReward() > 0 ? entry.rankXpReward() : catalog.defaultXpRewardForRank(rank);
        slot.setRankXpReward(xp);
        slot.setGenerationSeed(rng.nextLong());
        slot.setAcceptedByPlayerUuid(null);
        slot.setOnlineDaysElapsed(0);
        return true;
    }

    @Nullable
    private static QuestBoardItemSetJson pickWeightedItemSet(@Nonnull QuestBoardFetchEntryJson entry, @Nonnull Random rng) {
        List<QuestBoardItemSetJson> sets = entry.itemSetsOrEmpty();
        if (sets.isEmpty()) {
            return null;
        }
        int total = 0;
        for (QuestBoardItemSetJson s : sets) {
            total += s.weight();
        }
        if (total <= 0) {
            return sets.get(0);
        }
        int roll = rng.nextInt(total);
        int acc = 0;
        for (QuestBoardItemSetJson s : sets) {
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
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.fetchFallbackTitle")
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
        return Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.fetchFallbackDescription")
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
        Message result =
            Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.fetchObjective")
                .param("villager", villager);
        for (QuestBoardItemRequirement req : slot.requiredItemsOrEmpty()) {
            String itemId = req.itemIdOrEmpty();
            if (itemId.isBlank()) {
                continue;
            }
            result =
                result
                    .insert(Message.raw("\n"))
                    .insert(
                        Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.rewardLine")
                            .param("count", String.valueOf(req.count()))
                            .param("item", UiMaterialLabels.itemNameMessage(itemId))
                    );
        }
        return result;
    }

    @Override
    public boolean hasRequiredItems(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull QuestBoardSlotRecord slot) {
        CombinedItemContainer inv = playerInventory(playerRef, store);
        if (inv == null) {
            return false;
        }
        return InventoryMaterials.hasAll(inv, toMaterialRequirements(slot));
    }

    @Override
    public boolean consumeRequiredItems(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull QuestBoardSlotRecord slot) {
        CombinedItemContainer inv = playerInventory(playerRef, store);
        if (inv == null || !hasRequiredItems(playerRef, store, slot)) {
            return false;
        }
        InventoryMaterials.removeAll(inv, toMaterialRequirements(slot));
        return true;
    }

    @Nonnull
    private static List<MaterialRequirement> toMaterialRequirements(@Nonnull QuestBoardSlotRecord slot) {
        List<MaterialRequirement> out = new ArrayList<>();
        for (QuestBoardItemRequirement req : slot.requiredItemsOrEmpty()) {
            out.add(MaterialRequirement.ofItem(req.itemIdOrEmpty(), req.count()));
        }
        return out;
    }

    @Nullable
    private static CombinedItemContainer playerInventory(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return null;
        }
        return InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
    }
}
