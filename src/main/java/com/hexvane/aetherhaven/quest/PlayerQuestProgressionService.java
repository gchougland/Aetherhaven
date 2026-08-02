package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Save wide per player progression for {@code category: "player"} quests. */
public final class PlayerQuestProgressionService {
    private PlayerQuestProgressionService() {}

    public static boolean isPlayerQuest(@Nullable QuestDefinition def) {
        if (def == null) {
            return false;
        }
        String cat = def.category();
        return cat != null && "player".equalsIgnoreCase(cat.trim());
    }

    public static boolean initialize(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        return initialize(plugin.getQuestCatalog(), progress, questId);
    }

    static boolean initialize(
        @Nonnull QuestCatalog catalog,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        QuestDefinition def = catalog.get(questId);
        if (def == null || !isPlayerQuest(def)) {
            return false;
        }
        progress.initQuestObjectiveProgress(questId, def.trackableObjectiveIds());
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (isKind(objective, "journal") && hasId(objective)) {
                progress.completeQuestObjective(questId, objective.id());
            }
        }
        return true;
    }

    public static boolean startQuest(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null || !isPlayerQuest(def)) {
            return false;
        }
        if (progress.hasQuestActive(questId) || progress.hasQuestCompleted(questId)) {
            return false;
        }
        progress.addActiveQuest(questId);
        initialize(plugin.getQuestCatalog(), progress, questId);
        reconcileOnStart(plugin, world, playerRef, store, progress, questId);
        return true;
    }

    public static boolean completeQuest(
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        if (!progress.hasQuestActive(questId)) {
            return false;
        }
        progress.markQuestCompleted(questId);
        return true;
    }

    public static boolean abandonQuest(
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        if (!progress.hasQuestActive(questId)) {
            return false;
        }
        progress.clearQuest(questId);
        return true;
    }

    public static boolean onItemCrafted(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String itemId
    ) {
        return onItemCrafted(plugin.getQuestCatalog(), progress, itemId);
    }

    static boolean onItemCrafted(
        @Nonnull QuestCatalog catalog,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String itemId
    ) {
        boolean changed = false;
        for (String questId : progress.activeQuestIdsSnapshot()) {
            changed |= advanceCurrent(catalog, progress, questId, QuestProgressionService.ITEM_CRAFTED, itemId);
        }
        return changed;
    }

    public static boolean onCharterPlaced(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerQuestProgress progress
    ) {
        return onCharterPlaced(plugin.getQuestCatalog(), progress);
    }

    static boolean onCharterPlaced(
        @Nonnull QuestCatalog catalog,
        @Nonnull PlayerQuestProgress progress
    ) {
        boolean changed = false;
        for (String questId : progress.activeQuestIdsSnapshot()) {
            changed |= advanceCurrent(catalog, progress, questId, QuestProgressionService.CHARTER_PLACED, null);
        }
        return changed;
    }

    public static boolean tryCompleteActiveQuests(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerQuestProgress progress
    ) {
        return tryCompleteActiveQuests(plugin.getQuestCatalog(), progress);
    }

    static boolean tryCompleteActiveQuests(
        @Nonnull QuestCatalog catalog,
        @Nonnull PlayerQuestProgress progress
    ) {
        boolean changed = false;
        for (String questId : List.copyOf(progress.activeQuestIdsSnapshot())) {
            if (allObjectivesComplete(catalog, progress, questId)) {
                changed |= completeQuest(progress, questId);
            }
        }
        return changed;
    }

    public static void reconcileOnStart(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null || !progress.hasQuestActive(questId)) {
            return;
        }
        UUID playerUuid = playerUuid(playerRef, store);
        if (playerUuid != null) {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord owned = tm.findTownForOwnerInWorld(playerUuid);
            if (owned != null) {
                for (QuestObjective objective : def.objectivesOrEmpty()) {
                    if (hasId(objective)) {
                        progress.completeQuestObjective(questId, objective.id());
                    }
                }
                completeQuest(progress, questId);
                return;
            }
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (!isKind(objective, QuestProgressionService.ITEM_CRAFTED) || !hasId(objective)) {
                continue;
            }
            String want = objective.itemId();
            if (want != null && !want.isBlank() && InventoryMaterials.count(inv, want.trim()) > 0) {
                progress.completeQuestObjective(questId, objective.id());
            }
        }
        tryCompleteActiveQuests(plugin.getQuestCatalog(), progress);
    }

    @Nullable
    public static QuestObjective currentObjective(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        return currentObjective(plugin.getQuestCatalog(), progress, questId);
    }

    @Nullable
    static QuestObjective currentObjective(
        @Nonnull QuestCatalog catalog,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        QuestDefinition def = catalog.get(questId);
        if (def == null) {
            return null;
        }
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (isKind(objective, "journal")) {
                continue;
            }
            if (hasId(objective) && !isObjectiveComplete(progress, questId, objective)) {
                return objective;
            }
        }
        return null;
    }

    public static boolean allObjectivesComplete(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        return allObjectivesComplete(plugin.getQuestCatalog(), progress, questId);
    }

    static boolean allObjectivesComplete(
        @Nonnull QuestCatalog catalog,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId
    ) {
        QuestDefinition def = catalog.get(questId);
        if (def == null) {
            return false;
        }
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (isKind(objective, "journal")) {
                continue;
            }
            if (hasId(objective) && !isObjectiveComplete(progress, questId, objective)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isObjectiveComplete(
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId,
        @Nonnull QuestObjective objective
    ) {
        return hasId(objective) && progress.isQuestObjectiveComplete(questId, objective.id());
    }

    private static boolean advanceCurrent(
        @Nonnull QuestCatalog catalog,
        @Nonnull PlayerQuestProgress progress,
        @Nonnull String questId,
        @Nonnull String kind,
        @Nullable String itemId
    ) {
        QuestDefinition def = catalog.get(questId);
        QuestObjective current = currentObjective(catalog, progress, questId);
        if (def == null || current == null || !isKind(current, kind) || !hasId(current)) {
            return false;
        }
        if (QuestProgressionService.ITEM_CRAFTED.equalsIgnoreCase(kind)) {
            String want = current.itemId();
            if (want == null || want.isBlank() || itemId == null || !want.trim().equalsIgnoreCase(itemId.trim())) {
                return false;
            }
        }
        return progress.completeQuestObjective(questId, current.id());
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    private static boolean isKind(@Nonnull QuestObjective objective, @Nonnull String kind) {
        return objective.kind() != null && kind.equalsIgnoreCase(objective.kind());
    }

    private static boolean hasId(@Nonnull QuestObjective objective) {
        return objective.id() != null && !objective.id().isBlank();
    }
}
