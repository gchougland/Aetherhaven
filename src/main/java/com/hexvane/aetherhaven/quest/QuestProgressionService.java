package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.tourist.TouristMoveInRequirements;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceQuestSteps;
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
    public static final String TOURIST_MOVE_IN_ITEMS = "tourist_move_in_items";
    public static final String ITEM_CRAFTED = "item_crafted";
    public static final String CHARTER_PLACED = "charter_placed";
    /** The town has unlocked a workplace type through its supervising villager. */
    public static final String WORKPLACE_TYPE_UNLOCKED = "workplace_type_unlocked";
    /** A player has marked out a workplace area with the Building Staff. */
    public static final String WORKPLACE_MARKED = "workplace_marked";
    /** A marked workplace has been given its type and saved. */
    public static final String WORKPLACE_TYPE_CHOSEN = "workplace_type_chosen";
    /** A workplace has the station of its type standing inside it. */
    public static final String WORKPLACE_BENCH_PLACED = "workplace_bench_placed";
    public static final String WORKPLACE_FOOD_TENDED = "workplace_food_tended";

    private QuestProgressionService() {}

    /** The four workplace steps can be done in any order, so each is ticked by its own proof and never by a later one. */
    static boolean isWorkplaceKind(@Nonnull QuestObjective objective) {
        return isKind(objective, WORKPLACE_TYPE_UNLOCKED)
            || isKind(objective, WORKPLACE_MARKED)
            || isKind(objective, WORKPLACE_TYPE_CHOSEN)
            || isKind(objective, WORKPLACE_BENCH_PLACED)
            || isKind(objective, WORKPLACE_FOOD_TENDED);
    }

    /** Whether an objective's proof lies in the world, where only loaded chunks can be read. */
    public static boolean needsWorld(@Nonnull QuestObjective objective) {
        return isKind(objective, WORKPLACE_BENCH_PLACED);
    }

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
        return reconcile(def, plugin.getConstructionCatalog(), town, questId);
    }

    /** The same reconciliation for callers that hold the definition, and for tests, which have no plugin. */
    static boolean reconcile(
        @Nonnull QuestDefinition def,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull TownRecord town,
        @Nonnull String questId
    ) {
        List<QuestObjective> objectives = def.objectivesOrEmpty();
        town.initQuestObjectiveProgress(questId, def.trackableObjectiveIds());
        town.initQuestKillProgress(questId, def.entityKillObjectiveIds());

        boolean changed = false;
        int furthestProven = -1;
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            boolean stateComplete = isStateComplete(catalog, town, def, objective);
            // What the town's own state proves is written down at once. The proof may go away later (a workplace
            // can be removed) and the step must stay done. For the older kinds this changes nothing: they were
            // already written by the loop below.
            if (stateComplete && !isKillObjective(objective) && hasId(objective)) {
                changed |= town.completeQuestObjective(questId, objective.id());
            }
            if (stateComplete || isPersistedComplete(town, questId, objective)) {
                furthestProven = i;
            }
        }

        for (int i = 0; i <= furthestProven; i++) {
            QuestObjective objective = objectives.get(i);
            if (!isKillObjective(objective)
                && hasId(objective)
                && mayImputeObjectiveCompleteFromWorldState(def, objective)) {
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
        changed |= migrateTouristMoveInGiftFromLegacyTokenProgress(town, questId, def);
        return changed;
    }

    /** Saves that started the house quest before move in items: token progress implies gift was satisfied. */
    private static boolean migrateTouristMoveInGiftFromLegacyTokenProgress(
        @Nonnull TownRecord town,
        @Nonnull String questId,
        @Nonnull QuestDefinition def
    ) {
        if (!AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK.equals(questId.trim())) {
            return false;
        }
        if (!deferPlotTokenOnQuestStart(def)) {
            return false;
        }
        if (!town.isQuestObjectiveComplete(questId, TouristMoveInRequirements.MOVE_IN_OBJECTIVE_ID)) {
            for (QuestObjective o : def.objectivesOrEmpty()) {
                if (isKind(o, PLOT_TOKEN_RECEIVED) && hasId(o) && town.isQuestObjectiveComplete(questId, o.id())) {
                    return town.completeQuestObjective(questId, TouristMoveInRequirements.MOVE_IN_OBJECTIVE_ID);
                }
            }
        }
        return false;
    }

    /**
     * Later durable progress (e.g. an existing house plot) must not skip player gated steps such as move in gifts or
     * deferred plot tokens.
     */
    static boolean mayImputeObjectiveCompleteFromWorldState(
        @Nonnull QuestDefinition def,
        @Nonnull QuestObjective objective
    ) {
        if (isKind(objective, TOURIST_MOVE_IN_ITEMS)) {
            return false;
        }
        if (isKind(objective, PLOT_TOKEN_RECEIVED) && deferPlotTokenOnQuestStart(def)) {
            return false;
        }
        // A player may mark an area before unlocking a type. A later step must not tick an earlier one for them.
        if (isWorkplaceKind(objective)) {
            return false;
        }
        return true;
    }

    /** When the first gameplay objective is move in items, plot tokens are granted after delivery. */
    public static boolean deferPlotTokenOnQuestStart(@Nullable QuestDefinition def) {
        if (def == null) {
            return false;
        }
        for (QuestObjective o : def.objectivesOrEmpty()) {
            if (isJournalObjective(o)) {
                continue;
            }
            return isKind(o, TOURIST_MOVE_IN_ITEMS);
        }
        return false;
    }

    public static boolean completeTouristMoveInItems(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId
    ) {
        reconcile(plugin, town, questId);
        QuestObjective current = currentObjective(plugin, town, questId);
        if (current == null || !isKind(current, TOURIST_MOVE_IN_ITEMS) || !hasId(current)) {
            return false;
        }
        if (!TouristMoveInRequirements.MOVE_IN_OBJECTIVE_ID.equalsIgnoreCase(current.id().trim())) {
            return false;
        }
        return town.completeQuestObjective(questId, current.id());
    }

    public static boolean isQuestObjectiveComplete(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId,
        @Nonnull String objectiveId
    ) {
        reconcile(plugin, town, questId);
        return town.isQuestObjectiveComplete(questId, objectiveId.trim());
    }

    public static boolean isQuestObjectiveIncomplete(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String questId,
        @Nonnull String objectiveId
    ) {
        QuestDefinition def = plugin.getQuestCatalog().get(questId);
        if (def == null || !town.hasQuestActive(questId)) {
            return false;
        }
        reconcile(plugin, town, questId);
        for (QuestObjective o : def.objectivesOrEmpty()) {
            if (o.id() != null && objectiveId.trim().equalsIgnoreCase(o.id().trim())) {
                return !isObjectiveComplete(town, questId, o);
            }
        }
        return false;
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

    static boolean isDialogueTurnIn(@Nonnull QuestObjective objective) {
        return isKind(objective, DIALOGUE_TURN_IN);
    }

    /**
     * Answers for the objectives whose proof stands in the world, read from the chunks that are loaded now. Each
     * question is looked up once however many quests ask it. Call it as a world task, never from inside a tick system.
     */
    @Nonnull
    public static java.util.function.Function<QuestObjective, WorldProof> worldProofs(
        @Nonnull com.hypixel.hytale.server.core.universe.world.World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        java.util.Map<String, WorldProof> answers = new java.util.HashMap<>();
        return objective -> {
            if (!isKind(objective, WORKPLACE_BENCH_PLACED)) {
                return WorldProof.NO;
            }
            String only = objective.workType() == null ? "" : objective.workType();
            return answers.computeIfAbsent(
                WORKPLACE_BENCH_PLACED + ':' + only,
                key -> WorkplaceQuestSteps.stationProof(
                    WorkplaceQuestSteps.of(world), town, WorkplaceQuestSteps.type(only), plugin.getGrowerPlans()
                )
            );
        };
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

    /**
     * A workplace type was bought, or a workplace was saved. Both leave their proof in the town's save, so every
     * active quest is simply read again, whatever step it is on.
     */
    public static boolean onWorkplacesChanged(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        return reconcileActive(plugin.getQuestCatalog(), plugin.getConstructionCatalog(), town);
    }

    /** A player marked out a workplace area. It counts at once, even when no type is unlocked yet. */
    public static boolean onWorkplaceAreaMarked(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        return completeWhereverItStands(
            plugin.getQuestCatalog(), plugin.getConstructionCatalog(), town, WORKPLACE_MARKED, null
        );
    }

    /** The station of a workplace of this type was seen standing inside it: set down, used, or found by a look. */
    public static boolean onWorkplaceStationFound(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownsfolkWorkType type
    ) {
        return completeWhereverItStands(
            plugin.getQuestCatalog(), plugin.getConstructionCatalog(), town, WORKPLACE_BENCH_PLACED, type
        );
    }

    /** Reads every active quest against the town's state. Call it before state that proves a step is taken away. */
    static boolean reconcileActive(
        @Nonnull QuestCatalog quests,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull TownRecord town
    ) {
        boolean changed = false;
        for (String questId : town.getActiveQuestIdsSnapshot()) {
            QuestDefinition def = quests.get(questId);
            if (def != null) {
                changed |= reconcile(def, catalog, town, questId);
            }
        }
        return changed;
    }

    /**
     * Ticks an objective a player may do at any time. Unlike {@code advanceCurrent}, it does not wait for the step
     * to be the current one: a player who does things out of order sees each one count when it happens.
     *
     * @param type the workplace type the event was about, or null when the event names none
     */
    static boolean completeWhereverItStands(
        @Nonnull QuestCatalog quests,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull TownRecord town,
        @Nonnull String kind,
        @Nullable TownsfolkWorkType type
    ) {
        boolean changed = false;
        for (String questId : town.getActiveQuestIdsSnapshot()) {
            QuestDefinition def = quests.get(questId);
            if (def == null) {
                continue;
            }
            changed |= reconcile(def, catalog, town, questId);
            for (QuestObjective objective : def.objectivesOrEmpty()) {
                if (!isKind(objective, kind) || !hasId(objective)) {
                    continue;
                }
                TownsfolkWorkType only = WorkplaceQuestSteps.type(objective.workType());
                if (only != null && type != null && only != type) {
                    continue;
                }
                changed |= town.completeQuestObjective(questId, objective.id());
            }
        }
        return changed;
    }

    /**
     * Looks at the loaded world for objectives whose proof stands there, in every active quest. Events tick these
     * steps in ordinary play; this catches what no event saw, such as a bench that stood there before the quest began.
     */
    public static boolean observeWorld(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull java.util.function.Function<QuestObjective, WorldProof> world
    ) {
        boolean changed = false;
        for (String questId : town.getActiveQuestIdsSnapshot()) {
            QuestDefinition def = plugin.getQuestCatalog().get(questId);
            if (def == null) {
                continue;
            }
            changed |= observeWorld(def, town, questId, world);
        }
        return changed;
    }

    static boolean observeWorld(
        @Nonnull QuestDefinition def,
        @Nonnull TownRecord town,
        @Nonnull String questId,
        @Nonnull java.util.function.Function<QuestObjective, WorldProof> world
    ) {
        boolean changed = false;
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            if (!needsWorld(objective) || !hasId(objective) || isPersistedComplete(town, questId, objective)) {
                continue;
            }
            if (world.apply(objective) == WorldProof.YES) {
                changed |= town.completeQuestObjective(questId, objective.id());
            }
        }
        return changed;
    }

    /** True when an active quest still waits on something only the world can show. */
    public static boolean awaitsWorld(@Nonnull QuestCatalog quests, @Nonnull TownRecord town) {
        for (String questId : town.getActiveQuestIdsSnapshot()) {
            QuestDefinition def = quests.get(questId);
            if (def == null) {
                continue;
            }
            for (QuestObjective objective : def.objectivesOrEmpty()) {
                if (needsWorld(objective) && hasId(objective) && !isPersistedComplete(town, questId, objective)) {
                    return true;
                }
            }
        }
        return false;
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

    /** Whether the town's own saved state proves an objective, with no event and no world needed. */
    static boolean isStateComplete(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull TownRecord town,
        @Nonnull QuestDefinition def,
        @Nonnull QuestObjective objective
    ) {
        if (isKind(objective, WORKPLACE_TYPE_UNLOCKED)) {
            return WorkplaceQuestSteps.typeUnlocked(town, WorkplaceQuestSteps.type(objective.workType()));
        }
        if (isKind(objective, WORKPLACE_FOOD_TENDED)) {
            return WorkplaceQuestSteps.markedSites(town, WorkplaceQuestSteps.type(objective.workType())).stream()
                .anyMatch(site -> com.hexvane.aetherhaven.workplace.WorkplaceFood.supports(site.type()) && site.food().completedVisits() > 0);
        }
        if (isKind(objective, WORKPLACE_MARKED) || isKind(objective, WORKPLACE_TYPE_CHOSEN)) {
            return WorkplaceQuestSteps.siteExists(town, WorkplaceQuestSteps.type(objective.workType()));
        }
        String constructionId = objectiveConstruction(def, objective);
        if (isKind(objective, CONSTRUCTION_PLACED)) {
            return constructionId != null && hasPlot(town, catalog, constructionId, false);
        }
        if (isKind(objective, CONSTRUCTION_BUILT)) {
            return constructionId != null && town.hasCompletePlotWithConstruction(catalog, constructionId);
        }
        if (isKind(objective, ASSIGN_HOUSE_RESIDENT)) {
            UUID resident = assigneeUuid(town, def, objective);
            return resident != null && town.isNpcHomeResidentOnHousePlot(resident, catalog);
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
