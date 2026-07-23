package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.quest.QuestAssigneeDisplay;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.QuestObjectiveItemIcons;
import com.hexvane.aetherhaven.quest.QuestProgressionService;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.questboard.FetchQuestBoardHandler;
import com.hexvane.aetherhaven.questboard.HuntQuestBoardHandler;
import com.hexvane.aetherhaven.questboard.QuestBoardCatalog;
import com.hexvane.aetherhaven.questboard.QuestBoardGiverDisplay;
import com.hexvane.aetherhaven.questboard.QuestBoardService;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.worldnpc.WorldNpcPlayerProgress;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Checkbox step rows for the Town Journal quest detail pane. */
public final class QuestJournalObjectivesUi {
    private static final String ROW_DOC = "Aetherhaven/QuestJournalObjectiveRow.ui";
    static final String STEPS_LIST = "#QuestsPage #QuestsSplit #QuestDetailPane #QuestStepsList";

    private QuestJournalObjectivesUi() {}

    public static void clear(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.clear(STEPS_LIST);
    }

    public static void applyStoryQuest(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull QuestCatalog quests,
        @Nonnull String questId,
        @Nullable TownRecord town,
        @Nullable WorldNpcPlayerProgress worldProgress,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        commandBuilder.clear(STEPS_LIST);
        QuestDefinition def = quests.get(questId);
        if (def == null) {
            return;
        }
        List<QuestObjective> objectives = def.objectivesOrEmpty();
        if (objectives.isEmpty()) {
            return;
        }
        if (town != null) {
            QuestProgressionService.reconcile(plugin, town, questId);
        }
        String targetName = null;
        if (town != null && def.assignByEntity()) {
            targetName = QuestAssigneeDisplay.targetName(def, town, store, plugin);
        }
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            boolean complete = isStoryObjectiveComplete(questId, objective, town, worldProgress);
            Message label =
                Message.raw(String.valueOf(i + 1))
                    .insert(Message.raw(". "))
                    .insert(quests.formatObjectiveLine(objective, targetName, town, store, plugin, questId))
                    .insert(killProgressSuffix(questId, objective, town, worldProgress));
            appendRow(
                commandBuilder,
                i,
                complete,
                label,
                QuestObjectiveItemIcons.slotsForStoryObjective(def, objective, town, store, plugin)
            );
        }
    }

    public static void applyBoardQuest(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog,
        @Nullable Ref<EntityStore> viewerRef
    ) {
        commandBuilder.clear(STEPS_LIST);
        String type = slot.getQuestType() != null ? slot.getQuestType().trim() : "";
        if (FetchQuestBoardHandler.TYPE_ID.equalsIgnoreCase(type)) {
            String villager = QuestBoardGiverDisplay.giverName(slot, store, town);
            Message label =
                Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.fetchObjective")
                    .param("villager", villager);
            boolean ready =
                viewerRef != null
                    && viewerRef.isValid()
                    && QuestBoardService.handlerFor(type).hasRequiredItems(viewerRef, store, slot);
            appendRow(
                commandBuilder,
                0,
                ready,
                label,
                QuestObjectiveItemIcons.slotsForBoardFetch(slot)
            );
            return;
        }
        Message label = QuestBoardService.objectivesText(slot, town, store, catalog);
        boolean complete = false;
        if (HuntQuestBoardHandler.TYPE_ID.equalsIgnoreCase(type)) {
            int need = slot.getHuntKillRequired();
            complete = need > 0 && slot.getHuntKillProgress() >= need;
        }
        appendRow(commandBuilder, 0, complete, label, List.of());
    }

    private static void appendRow(
        @Nonnull UICommandBuilder commandBuilder,
        int index,
        boolean complete,
        @Nonnull Message label,
        @Nonnull List<ItemGridSlot> itemSlots
    ) {
        commandBuilder.append(STEPS_LIST, ROW_DOC);
        String row = STEPS_LIST + "[" + index + "]";
        commandBuilder.set(row + " #CheckBox.Value", complete);
        commandBuilder.set(row + " #CheckBox.Disabled", true);
        commandBuilder.set(row + " #StepLabel.TextSpans", label);
        commandBuilder.set(row + " #StepLabel.Style.TextColor", complete ? "#a8d4b0" : "#d8ccb8");
        if (itemSlots.isEmpty()) {
            commandBuilder.set(row + " #StepItemsPanel.Visible", false);
            AetherhavenUiItemGrids.hide(commandBuilder, row + " #StepItemsPanel #StepItems");
            return;
        }
        commandBuilder.set(row + " #StepItemsPanel.Visible", true);
        AetherhavenUiItemGrids.setSlots(
            commandBuilder,
            row + " #StepItemsPanel #StepItems",
            itemSlots.toArray(ItemGridSlot[]::new)
        );
    }

    private static boolean isStoryObjectiveComplete(
        @Nonnull String questId,
        @Nonnull QuestObjective objective,
        @Nullable TownRecord town,
        @Nullable WorldNpcPlayerProgress worldProgress
    ) {
        if (town != null) {
            return QuestProgressionService.isObjectiveComplete(town, questId, objective);
        }
        if (worldProgress == null || objective.id() == null || objective.id().isBlank()) {
            return false;
        }
        String oid = objective.id().trim();
        if (objective.kind() != null && "entity_kills".equalsIgnoreCase(objective.kind().trim())) {
            int need = Math.max(1, objective.killCount());
            return worldProgress.getQuestKillCount(questId, oid) >= need;
        }
        return worldProgress.isQuestObjectiveComplete(questId, oid);
    }

    @Nonnull
    private static Message killProgressSuffix(
        @Nonnull String questId,
        @Nonnull QuestObjective objective,
        @Nullable TownRecord town,
        @Nullable WorldNpcPlayerProgress worldProgress
    ) {
        if (objective.kind() == null || !"entity_kills".equalsIgnoreCase(objective.kind().trim())) {
            return Message.raw("");
        }
        if (objective.id() == null || objective.id().isBlank()) {
            return Message.raw("");
        }
        int need = Math.max(1, objective.killCount());
        int cur;
        if (town != null) {
            cur = town.getQuestKillCount(questId, objective.id().trim());
        } else if (worldProgress != null) {
            cur = worldProgress.getQuestKillCount(questId, objective.id().trim());
        } else {
            return Message.raw("");
        }
        return Message.raw(" (" + Math.min(cur, need) + "/" + need + ")");
    }
}
