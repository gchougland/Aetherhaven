package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.QuestProgressionService;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-player progression for {@code category: "world"} quests. */
public final class WorldQuestProgressionService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private WorldQuestProgressionService() {}

    public static boolean isWorldQuest(@Nullable QuestDefinition def) {
        if (def == null) {
            return false;
        }
        String cat = def.category();
        return cat != null && "world".equalsIgnoreCase(cat.trim());
    }

    public static boolean initialize(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null || !isWorldQuest(def)) {
            return false;
        }
        warnUnsupportedObjectives(def);
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        progress.initQuestObjectiveProgress(questId, def.trackableObjectiveIds());
        progress.initQuestKillProgress(questId, def.entityKillObjectiveIds());
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (isKind(objective, "journal") && hasId(objective)) {
                progress.completeQuestObjective(questId, objective.id());
            }
        }
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
        return true;
    }

    public static boolean startQuest(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null || !isWorldQuest(def)) {
            return false;
        }
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        if (progress.hasQuestActive(questId) || progress.hasQuestCompleted(questId)) {
            return false;
        }
        progress.addActiveQuest(questId);
        initialize(plugin, world, playerUuid, questId);
        return true;
    }

    public static boolean completeQuest(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull String questId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        if (!progress.hasQuestActive(questId)) {
            return false;
        }
        progress.markQuestCompleted(questId);
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
        return true;
    }

    public static boolean abandonQuest(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull String questId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        if (!progress.hasQuestActive(questId)) {
            return false;
        }
        progress.clearQuest(questId);
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
        return true;
    }

    public static boolean markDialogueTurnIn(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull String questId,
        @Nonnull String objectiveId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        if (!progress.hasQuestActive(questId)) {
            return false;
        }
        boolean changed = progress.completeQuestObjective(questId, objectiveId);
        if (changed) {
            registry.markPlayerDirty();
            WorldNpcPersistence.save(world, plugin, registry);
        }
        return changed;
    }

    /**
     * Completes the current dialogue_turn_in objective when present. Returns true when the quest may be completed
     * (all non town only objectives done).
     */
    public static boolean advanceDialogueTurnIn(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull String questId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        if (!progress.hasQuestActive(questId)) {
            return false;
        }
        QuestObjective current = currentObjective(plugin, progress, questId);
        if (current != null && isKind(current, "dialogue_turn_in") && hasId(current)) {
            progress.completeQuestObjective(questId, current.id());
            registry.markPlayerDirty();
            WorldNpcPersistence.save(world, plugin, registry);
        }
        return allObjectivesComplete(plugin, progress, questId);
    }

    public static boolean allObjectivesComplete(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldNpcPlayerProgress progress,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null) {
            return false;
        }
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (isTownOnlyObjective(objective)) {
                continue;
            }
            if (isKillObjective(objective)) {
                int required = Math.max(1, objective.killCount());
                if (progress.getQuestKillCount(questId, objective.id() != null ? objective.id() : "") < required) {
                    return false;
                }
                continue;
            }
            if (hasId(objective) && !progress.isQuestObjectiveComplete(questId, objective.id())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static QuestObjective currentObjective(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldNpcPlayerProgress progress,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null) {
            return null;
        }
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (isTownOnlyObjective(objective)) {
                continue;
            }
            if (isKillObjective(objective)) {
                int required = Math.max(1, objective.killCount());
                if (progress.getQuestKillCount(questId, objective.id() != null ? objective.id() : "") < required) {
                    return objective;
                }
                continue;
            }
            if (hasId(objective) && !progress.isQuestObjectiveComplete(questId, objective.id())) {
                return objective;
            }
        }
        return null;
    }

    private static void warnUnsupportedObjectives(@Nonnull QuestDefinition def) {
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (isTownOnlyObjective(objective)) {
                LOGGER.atWarning().log(
                    "World quest %s has town only objective kind %s; it will be skipped",
                    def.idOrEmpty(),
                    objective.kind() != null ? objective.kind() : ""
                );
            }
        }
    }

    private static boolean isTownOnlyObjective(@Nonnull QuestObjective objective) {
        String kind = objective.kind() != null ? objective.kind().toLowerCase(Locale.ROOT) : "";
        return switch (kind) {
            case QuestProgressionService.PLOT_TOKEN_RECEIVED,
                QuestProgressionService.PLOT_BLUEPRINT_RECEIVED,
                QuestProgressionService.PLOT_BLUEPRINT_LEARNED,
                QuestProgressionService.CONSTRUCTION_PLACED,
                QuestProgressionService.CONSTRUCTION_BUILT,
                QuestProgressionService.ASSIGN_HOUSE_RESIDENT -> true;
            default -> false;
        };
    }

    private static boolean isKillObjective(@Nonnull QuestObjective objective) {
        return objective.kind() != null && "entity_kills".equalsIgnoreCase(objective.kind());
    }

    private static boolean isKind(@Nonnull QuestObjective objective, @Nonnull String kind) {
        return objective.kind() != null && kind.equalsIgnoreCase(objective.kind());
    }

    private static boolean hasId(@Nonnull QuestObjective objective) {
        return objective.id() != null && !objective.id().isBlank();
    }
}
