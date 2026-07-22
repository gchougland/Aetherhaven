package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Ordered, town-wide story quest progression. Objective array order is authoritative: event objectives advance only
 * when they are current, while durable town state is reconciled so pre-existing buildings and assignments count.
 */
public final class QuestProgressionService {
    public static final String PLOT_TOKEN_RECEIVED = "plot_token_received";
    public static final String PLOT_BLUEPRINT_RECEIVED = "plot_blueprint_received";
    public static final String PLOT_BLUEPRINT_LEARNED = "plot_blueprint_learned";
    public static final String CONSTRUCTION_PLACED = "construction_placed";
    public static final String CONSTRUCTION_BUILT = "construction_built";
    public static final String ASSIGN_HOUSE_RESIDENT = "assign_house_resident";
    public static final String DIALOGUE_TURN_IN = "dialogue_turn_in";

    private QuestProgressionService() {}

    public static boolean initialize(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null) {
            return false;
        }
        town.initQuestObjectiveProgress(questId, def.trackableObjectiveIds());
        town.initQuestKillProgress(questId, def.entityKillObjectiveIds());
        return reconcile(plugin, town, questId);
    }

    /**
     * Reconciles objectives provable from durable town state. A later completed building/assignment proves earlier
     * setup steps too, which both supports pre-existing buildings and migrates active quests from old saves.
     */
    public static boolean reconcile(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null) {
            return false;
        }
        List<QuestObjective> objectives = def.objectivesOrEmpty();
        town.initQuestObjectiveProgress(questId, def.trackableObjectiveIds());
        town.initQuestKillProgress(questId, def.entityKillObjectiveIds());

        int furthestProven = -1;
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            if (isPersistedComplete(town, questId, objective) || isStateComplete(plugin, town, def, objective)) {
                furthestProven = i;
            }
        }

        boolean changed = false;
        for (int i = 0; i <= furthestProven; i++) {
            QuestObjective objective = objectives.get(i);
            if (!isKillObjective(objective) && hasId(objective)) {
                changed |= town.completeQuestObjective(questId, objective.id());
            }
        }

        // Legacy journal lines represented actions that happen while accepting the quest. Keep non-construction
        // quests progressing until their first semantic objective.
        for (QuestObjective objective : objectives) {
            if (!isKind(objective, "journal")) {
                break;
            }
            if (hasId(objective)) {
                changed |= town.completeQuestObjective(questId, objective.id());
            }
        }
        return changed;
    }

    @Nullable
    public static QuestObjective currentObjective(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null) {
            return null;
        }
        return currentObjective(def, town, questId);
    }

    @Nullable
    public static QuestObjective currentObjective(
        @Nonnull QuestDefinition def,
        @Nonnull TownRecord town,
        @Nonnull String questId
    ) {
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (isJournalObjective(objective)) {
                continue;
            }
            if (!isObjectiveComplete(town, questId, objective)) {
                return objective;
            }
        }
        return null;
    }

    static boolean isJournalObjective(@Nonnull QuestObjective objective) {
        return isKind(objective, "journal");
    }

    public static boolean allObjectivesComplete(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null) {
            return true;
        }
        return allObjectivesComplete(def, town, questId);
    }

    public static boolean allObjectivesComplete(
        @Nonnull QuestDefinition def,
        @Nonnull TownRecord town,
        @Nonnull String questId
    ) {
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (!isObjectiveComplete(town, questId, objective)) {
                return false;
            }
        }
        return true;
    }

    public static boolean markStartGrant(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId,
        @Nonnull String kind
    ) {
        return advanceCurrent(plugin, town, questId, kind, null, null, null);
    }

    public static boolean onBlueprintLearned(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String constructionId
    ) {
        return advanceAllMatching(plugin, town, PLOT_BLUEPRINT_LEARNED, constructionId, null, null);
    }

    public static boolean onConstructionPlaced(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String constructionId
    ) {
        return advanceAllMatching(plugin, town, CONSTRUCTION_PLACED, constructionId, null, null);
    }

    public static boolean onConstructionBuilt(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String constructionId
    ) {
        return advanceAllMatching(plugin, town, CONSTRUCTION_BUILT, constructionId, null, null);
    }

    public static boolean onResidentAssigned(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID residentUuid,
        @Nullable String npcRoleId
    ) {
        return advanceAllMatching(plugin, town, ASSIGN_HOUSE_RESIDENT, null, residentUuid, npcRoleId);
    }

    public static boolean advanceDialogueTurnIn(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId
    ) {
        reconcile(plugin, town, questId);
        QuestObjective current = currentObjective(plugin, town, questId);
        if (current == null) {
            return true;
        }
        if (!isKind(current, DIALOGUE_TURN_IN) || !hasId(current)) {
            return false;
        }
        town.completeQuestObjective(questId, current.id());
        return allObjectivesComplete(plugin, town, questId);
    }

    private static boolean advanceAllMatching(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String kind,
        @Nullable String constructionId,
        @Nullable UUID residentUuid,
        @Nullable String npcRoleId
    ) {
        boolean changed = false;
        for (String questId : town.getActiveQuestIdsSnapshot()) {
            changed |= advanceCurrent(plugin, town, questId, kind, constructionId, residentUuid, npcRoleId);
        }
        return changed;
    }

    private static boolean advanceCurrent(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId,
        @Nonnull String kind,
        @Nullable String constructionId,
        @Nullable UUID residentUuid,
        @Nullable String npcRoleId
    ) {
        boolean changed = reconcile(plugin, town, questId);
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        QuestObjective current = currentObjective(plugin, town, questId);
        if (def == null || current == null || !isKind(current, kind) || !hasId(current)) {
            return changed;
        }
        if (constructionId != null && !matchesConstruction(plugin.getConstructionCatalog(), def, current, constructionId)) {
            return changed;
        }
        if (residentUuid != null
            && !matchesResident(town, def, current, residentUuid, npcRoleId, plugin.getConstructionCatalog())) {
            return changed;
        }
        return town.completeQuestObjective(questId, current.id()) || changed;
    }

    public static boolean isObjectiveComplete(
        @Nonnull TownRecord town,
        @Nonnull String questId,
        @Nonnull QuestObjective objective
    ) {
        if (isKillObjective(objective)) {
            return hasId(objective)
                && town.getQuestKillCount(questId, objective.id()) >= Math.max(1, objective.killCount());
        }
        return isPersistedComplete(town, questId, objective);
    }

    private static boolean isPersistedComplete(
        @Nonnull TownRecord town,
        @Nonnull String questId,
        @Nonnull QuestObjective objective
    ) {
        return hasId(objective) && town.isQuestObjectiveComplete(questId, objective.id());
    }

    private static boolean isStateComplete(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull QuestDefinition def,
        @Nonnull QuestObjective objective
    ) {
        String constructionId = objectiveConstruction(def, objective);
        if (isKind(objective, CONSTRUCTION_PLACED)) {
            return constructionId != null && hasPlot(town, plugin.getConstructionCatalog(), constructionId, false);
        }
        if (isKind(objective, CONSTRUCTION_BUILT)) {
            return constructionId != null && town.hasCompletePlotWithConstruction(plugin.getConstructionCatalog(), constructionId);
        }
        if (isKind(objective, ASSIGN_HOUSE_RESIDENT)) {
            UUID resident = assigneeUuid(town, def, objective);
            return resident != null && town.isNpcHomeResidentOnHousePlot(resident, plugin.getConstructionCatalog());
        }
        if (isKillObjective(objective)) {
            return isObjectiveComplete(town, def.idOrEmpty(), objective);
        }
        return false;
    }

    private static boolean hasPlot(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String constructionId,
        boolean completeOnly
    ) {
        for (PlotInstance plot : town.getPlotInstances()) {
            if ((!completeOnly || plot.getState() == PlotInstanceState.COMPLETE)
                && catalog.matchesGameplayConstruction(plot.getConstructionId(), constructionId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesConstruction(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull QuestDefinition def,
        @Nonnull QuestObjective objective,
        @Nonnull String actualId
    ) {
        String expected = objectiveConstruction(def, objective);
        return expected != null && catalog.matchesGameplayConstruction(actualId, expected);
    }

    @Nullable
    private static String objectiveConstruction(
        @Nonnull QuestDefinition def,
        @Nonnull QuestObjective objective
    ) {
        if (objective.constructionId() != null && !objective.constructionId().isBlank()) {
            return objective.constructionId().trim();
        }
        String token = def.grantPlotTokenConstructionId();
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String blueprint = def.grantPlotBlueprintConstructionId();
        return blueprint != null && !blueprint.isBlank() ? blueprint.trim() : null;
    }

    private static boolean matchesResident(
        @Nonnull TownRecord town,
        @Nonnull QuestDefinition def,
        @Nonnull QuestObjective objective,
        @Nonnull UUID residentUuid,
        @Nullable String actualRoleId,
        @Nonnull ConstructionCatalog catalog
    ) {
        if (!def.assignByEntity()) {
            String expectedRole = objective.npcRoleId();
            if (expectedRole == null || expectedRole.isBlank()) {
                expectedRole = def.assignNpcRoleId();
            }
            if (expectedRole != null
                && actualRoleId != null
                && expectedRole.equalsIgnoreCase(actualRoleId.trim())
                && town.isNpcHomeResidentOnHousePlot(residentUuid, catalog)) {
                return true;
            }
        }
        UUID expected = assigneeUuid(town, def, objective);
        return residentUuid.equals(expected) && town.isNpcHomeResidentOnHousePlot(residentUuid, catalog);
    }

    @Nullable
    private static UUID assigneeUuid(
        @Nonnull TownRecord town,
        @Nonnull QuestDefinition def,
        @Nonnull QuestObjective objective
    ) {
        if (def.assignByEntity()) {
            return town.getQuestTargetEntityUuid(def.idOrEmpty());
        }
        String role = objective.npcRoleId();
        if (role == null || role.isBlank()) {
            role = def.assignNpcRoleId();
        }
        if (role == null || role.isBlank()) {
            return town.getQuestTargetEntityUuid(def.idOrEmpty());
        }
        for (ResidentNpcRecord record : town.getResidentNpcRecords()) {
            if (role.equalsIgnoreCase(record.getNpcRoleId())) {
                UUID uuid = record.getLastEntityUuid();
                if (!uuid.equals(new UUID(0L, 0L))) {
                    return uuid;
                }
            }
        }
        return null;
    }

    private static boolean hasId(@Nonnull QuestObjective objective) {
        return objective.id() != null && !objective.id().isBlank();
    }

    private static boolean isKillObjective(@Nonnull QuestObjective objective) {
        return isKind(objective, "entity_kills");
    }

    private static boolean isKind(@Nonnull QuestObjective objective, @Nonnull String kind) {
        return objective.kind() != null
            && objective.kind().trim().toLowerCase(Locale.ROOT).equals(kind.toLowerCase(Locale.ROOT));
    }
}
