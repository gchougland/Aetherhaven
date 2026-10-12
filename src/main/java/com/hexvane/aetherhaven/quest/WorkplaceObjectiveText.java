package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.AetherhavenUiItemGrids;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.workplace.WorkplaceGuide;
import com.hexvane.aetherhaven.workplace.WorkplaceQuestSteps;
import com.hexvane.aetherhaven.workplace.WorkplaceStations;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The words of a workplace objective. Each of the four kinds says what to do with the town's own names in it, so its
 * line is the objective's lang key with a suffix for the wording that fits the town today, filled from
 * {@link WorkplaceGuide}. A finished step uses the suffix {@code done}, a short line with no gaps.
 *
 * <p>The suffixes a quest's language file must supply are listed in {@link #JOURNAL_VARIANTS} and
 * {@link #HUD_VARIANTS}; a test holds the shipped quest to them.
 */
public final class WorkplaceObjectiveText {
    private WorkplaceObjectiveText() {}

    /** Shown beside the step that asks for an area to be marked. */
    static final String BUILDING_STAFF_ITEM = "Aetherhaven_Building_Staff";

    public static final String DONE = "done";

    /** Every wording of each kind's journal line, besides {@link #DONE}. */
    public static final Map<String, List<String>> JOURNAL_VARIANTS = Map.of(
        QuestProgressionService.WORKPLACE_TYPE_UNLOCKED, List.of("ready", "readyMore", "readyUnnamed", "readyUnnamedMore", "waiting"),
        QuestProgressionService.WORKPLACE_MARKED, List.of("mark"),
        QuestProgressionService.WORKPLACE_TYPE_CHOSEN, List.of("ready", "locked"),
        QuestProgressionService.WORKPLACE_BENCH_PLACED, List.of("place", "placeTier", "placePocket", "placeNoRecipe", "placeUnknown", "noSite")
    );

    /** Every wording of each kind's HUD line, besides {@link #DONE}. */
    public static final Map<String, List<String>> HUD_VARIANTS = Map.of(
        QuestProgressionService.WORKPLACE_TYPE_UNLOCKED, List.of("ready", "readyUnnamed", "waiting"),
        QuestProgressionService.WORKPLACE_MARKED, List.of("mark"),
        QuestProgressionService.WORKPLACE_TYPE_CHOSEN, List.of("choose"),
        QuestProgressionService.WORKPLACE_BENCH_PLACED, List.of("place", "placeUnknown", "noSite")
    );

    /** What the town should do for this objective today, or null when it is not a workplace objective. */
    @Nullable
    static WorkplaceGuide.Step step(
        @Nonnull QuestObjective objective,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin
    ) {
        String kind = objective.kind() == null ? "" : objective.kind().trim().toLowerCase(java.util.Locale.ROOT);
        var only = WorkplaceQuestSteps.type(objective.workType());
        return switch (kind) {
            case QuestProgressionService.WORKPLACE_TYPE_UNLOCKED ->
                WorkplaceGuide.unlockStep(town, plugin.getConstructionCatalog(), only, role -> villagerName(plugin, role));
            case QuestProgressionService.WORKPLACE_MARKED -> WorkplaceGuide.markStep();
            case QuestProgressionService.WORKPLACE_TYPE_CHOSEN -> WorkplaceGuide.chooseStep(town, only);
            case QuestProgressionService.WORKPLACE_BENCH_PLACED -> WorkplaceGuide.benchStep(town, only, WorkplaceStations::find);
            default -> null;
        };
    }

    @Nullable
    private static String villagerName(@Nonnull AetherhavenPlugin plugin, @Nonnull String npcRoleId) {
        VillagerDefinition villager = plugin.getVillagerDefinitionCatalog().byNpcRoleId(npcRoleId);
        return villager == null ? null : villager.getDisplayName();
    }

    /**
     * The line for a workplace objective, or null when this objective is of another kind or has no lang key to vary.
     *
     * @param hud true for the HUD tracker's shorter line, when the objective has one
     */
    @Nullable
    static Message line(
        @Nonnull QuestObjective objective,
        @Nullable TownRecord town,
        @Nullable AetherhavenPlugin plugin,
        @Nonnull String questId,
        boolean hud
    ) {
        if (!QuestProgressionService.isWorkplaceKind(objective)) {
            return null;
        }
        boolean shortLine = hud && filled(objective.hudTextLangKey());
        String key = shortLine ? objective.hudTextLangKey() : objective.textLangKey();
        if (!filled(key)) {
            return null;
        }
        key = key.trim();
        if (town == null || plugin == null || QuestProgressionService.isObjectiveComplete(town, questId, objective)) {
            return Message.translation(key + "." + DONE);
        }
        WorkplaceGuide.Step step = step(objective, town, plugin);
        if (step == null) {
            return null;
        }
        Message line = Message.translation(key + "." + (shortLine ? step.hudVariant() : step.variant()));
        for (Map.Entry<String, WorkplaceGuide.Word> word : step.words().entrySet()) {
            WorkplaceGuide.Word value = word.getValue();
            if (value.kind() == WorkplaceGuide.Word.Kind.RAW) {
                line.param(word.getKey(), value.value());
            } else {
                line.param(word.getKey(), message(value, plugin));
            }
        }
        return line;
    }

    @Nonnull
    private static Message message(@Nonnull WorkplaceGuide.Word word, @Nonnull AetherhavenPlugin plugin) {
        return switch (word.kind()) {
            case RAW -> Message.raw(word.value());
            case LANG -> Message.translation(word.value());
            case ITEM -> {
                Item item = Item.getAssetMap().getAsset(word.value());
                yield item == null ? Message.raw(word.value().replace('_', ' ')) : Message.translation(item.getTranslationKey());
            }
            case BUILDING -> {
                ConstructionDefinition building = plugin.getConstructionCatalog().get(word.value());
                yield building == null ? Message.raw(word.value().replace('_', ' ')) : building.displayNameMessage();
            }
        };
    }

    /**
     * Changes whenever the line would read differently. The HUD only redraws a pinned quest when its key changes, and
     * these lines change with the town (a type is unlocked, a workplace is named) while the step stays the same.
     */
    @Nonnull
    static String signature(
        @Nonnull QuestObjective objective,
        @Nullable TownRecord town,
        @Nullable AetherhavenPlugin plugin
    ) {
        if (town == null || plugin == null || !QuestProgressionService.isWorkplaceKind(objective)) {
            return "";
        }
        WorkplaceGuide.Step step = step(objective, town, plugin);
        return step == null ? "" : step.signature();
    }

    /** The item to show beside a workplace step in the journal: the staff that marks, the bench to set down. */
    @Nonnull
    static List<ItemGridSlot> icons(
        @Nonnull QuestObjective objective,
        @Nullable TownRecord town,
        @Nonnull AetherhavenPlugin plugin
    ) {
        String itemId = null;
        if (QuestProgressionService.WORKPLACE_MARKED.equalsIgnoreCase(objective.kind() == null ? "" : objective.kind().trim())) {
            itemId = BUILDING_STAFF_ITEM;
        } else if (town != null) {
            WorkplaceGuide.Step step = step(objective, town, plugin);
            itemId = step == null ? null : step.itemId();
        }
        if (itemId == null) {
            return List.of();
        }
        ItemGridSlot slot = AetherhavenUiItemGrids.slotForKnownItem(itemId, 1);
        return slot == null ? List.of() : List.of(slot);
    }

    private static boolean filled(@Nullable String text) {
        return text != null && !text.isBlank();
    }
}
