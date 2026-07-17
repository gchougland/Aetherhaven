package com.hexvane.aetherhaven.hud;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.questboard.QuestBoardCatalog;
import com.hexvane.aetherhaven.questboard.QuestBoardService;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builds immutable HUD state by reading current inventory and town data. It never mutates ECS or inventory; story
 * objective rendering may lazily reconcile persisted quest progress from durable town state for old saves.
 */
public final class AetherhavenHudSnapshotService {
    public static final int MAX_QUESTS = 3;

    @Nonnull
    private final AetherhavenPlugin plugin;

    public AetherhavenHudSnapshotService(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Captures state for a player. The caller deliberately supplies the resolved town so snapshot creation cannot
     * create a world registry or persist town data.
     */
    @Nonnull
    public AetherhavenHudSnapshot capture(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull LocalDateTime gameTime,
        @Nullable TownRecord town,
        @Nonnull PlayerTownJournalState preferences
    ) {
        Ref<EntityStore> playerEntity = playerRef.getReference();
        CombinedItemContainer inventory =
            playerEntity != null ? InventoryComponent.getCombined(store, playerEntity, InventoryComponent.EVERYTHING) : null;
        long inventoryCoins = inventory != null
            ? InventoryMaterials.count(inventory, com.hexvane.aetherhaven.AetherhavenConstants.ITEM_GOLD_COIN)
            : 0L;
        long treasuryCoins = town != null ? Math.max(0L, town.getTreasuryGoldCoinCount()) : 0L;
        List<HudQuestEntry> quests =
            town != null && preferences.isHudShowQuests()
                ? questEntries(town, store, preferences.getPinnedQuestIds())
                : List.of();
        return new AetherhavenHudSnapshot(
            preferences.isHudShowTime(),
            preferences.isHudShowDate(),
            preferences.isHudShowGold(),
            preferences.isHudShowQuests(),
            preferences.getHudBackgroundOpacity(),
            AetherhavenCalendar.formatDate(gameTime),
            AetherhavenCalendar.formatClock(gameTime),
            inventoryCoins,
            treasuryCoins,
            combinedGold(inventoryCoins, treasuryCoins),
            quests
        );
    }

    @Nonnull
    private List<HudQuestEntry> questEntries(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<String> pinnedQuestIds
    ) {
        List<HudQuestEntry> entries = new ArrayList<>(MAX_QUESTS);
        QuestCatalog storyCatalog = plugin.getQuestCatalog();
        QuestBoardCatalog boardCatalog = plugin.getQuestBoardCatalog();
        List<String> activeStory = town.getActiveQuestIdsSnapshot();
        for (String questId : pinnedQuestIds) {
            if (entries.size() >= MAX_QUESTS) {
                break;
            }
            if (QuestBoardService.isBoardJournalRow(questId)) {
                String instanceId = QuestBoardService.parseJournalInstanceId(questId);
                QuestBoardSlotRecord slot =
                    instanceId != null ? town.findBoardSlotByInstanceId(instanceId) : null;
                if (slot == null || !slot.isAccepted()) {
                    continue;
                }
                entries.add(
                    new HudQuestEntry(
                        HudQuestEntry.Source.QUEST_BOARD,
                        questId,
                        QuestBoardService.displayTitle(slot, town, store, boardCatalog),
                        QuestBoardService.objectivesText(slot, town, store, boardCatalog)
                    )
                );
            } else if (activeStory.contains(questId)) {
                entries.add(
                    new HudQuestEntry(
                        HudQuestEntry.Source.STORY,
                        questId,
                        storyCatalog.journalTitle(questId, town, store, plugin),
                        storyCatalog.currentObjectiveMessage(questId, town, store, plugin)
                    )
                );
            }
        }
        return List.copyOf(entries);
    }

    public static long combinedGold(long left, long right) {
        long safeLeft = Math.max(0L, left);
        long safeRight = Math.max(0L, right);
        if (safeRight > 0L && safeLeft > Long.MAX_VALUE - safeRight) {
            return Long.MAX_VALUE;
        }
        return safeLeft + safeRight;
    }
}
