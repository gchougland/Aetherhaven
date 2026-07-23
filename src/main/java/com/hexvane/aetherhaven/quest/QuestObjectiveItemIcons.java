package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.questboard.QuestBoardItemRequirement;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.tourist.TouristMoveInRequirements;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.AetherhavenUiItemGrids;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves {@link ItemGridSlot} icons for quest journal objective rows. */
public final class QuestObjectiveItemIcons {
    private QuestObjectiveItemIcons() {}

    @Nonnull
    public static List<ItemGridSlot> slotsForStoryObjective(
        @Nonnull QuestDefinition def,
        @Nonnull QuestObjective objective,
        @Nullable TownRecord town,
        @Nullable Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        String kind = objective.kind() != null ? objective.kind().trim() : "";
        if (QuestProgressionService.TOURIST_MOVE_IN_ITEMS.equalsIgnoreCase(kind) && town != null) {
            return materialSlots(TouristMoveInRequirements.forQuestTarget(plugin, town, store));
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        if (QuestProgressionService.PLOT_TOKEN_RECEIVED.equalsIgnoreCase(kind)) {
            return constructionTokenSlots(def.grantPlotTokenConstructionId(), catalog);
        }
        if ("construction_built".equalsIgnoreCase(kind) || "construction_placed".equalsIgnoreCase(kind)) {
            String constructionId = objective.constructionId();
            if (constructionId == null || constructionId.isBlank()) {
                constructionId = def.grantPlotTokenConstructionId();
            }
            return constructionTokenSlots(constructionId, catalog);
        }
        if ("plot_blueprint_received".equalsIgnoreCase(kind) || "plot_blueprint_learned".equalsIgnoreCase(kind)) {
            String blueprint = def.grantPlotBlueprintConstructionId();
            return constructionTokenSlots(blueprint, catalog);
        }
        return List.of();
    }

    @Nonnull
    public static List<ItemGridSlot> slotsForBoardFetch(@Nonnull QuestBoardSlotRecord slot) {
        List<ItemGridSlot> out = new ArrayList<>();
        for (QuestBoardItemRequirement req : slot.requiredItemsOrEmpty()) {
            String itemId = req.itemIdOrEmpty();
            if (itemId.isBlank()) {
                continue;
            }
            ItemGridSlot gridSlot = AetherhavenUiItemGrids.slotForKnownItem(itemId, Math.max(1, req.count()));
            if (gridSlot != null) {
                out.add(gridSlot);
            }
        }
        return out;
    }

    @Nonnull
    private static List<ItemGridSlot> materialSlots(@Nonnull List<MaterialRequirement> requirements) {
        List<ItemGridSlot> out = new ArrayList<>();
        for (MaterialRequirement req : requirements) {
            String itemId = req.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ItemGridSlot slot = AetherhavenUiItemGrids.slotForKnownItem(itemId, Math.max(1, req.getCount()));
            if (slot != null) {
                out.add(slot);
            }
        }
        return out;
    }

    @Nonnull
    private static List<ItemGridSlot> constructionTokenSlots(
        @Nullable String constructionId,
        @Nonnull ConstructionCatalog catalog
    ) {
        if (constructionId == null || constructionId.isBlank()) {
            return List.of();
        }
        ItemGridSlot slot =
            AetherhavenUiItemGrids.plotTokenSlotForConstruction(constructionId.trim().toLowerCase(Locale.ROOT), catalog);
        if (slot == null) {
            return List.of();
        }
        return List.of(slot);
    }
}
