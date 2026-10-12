package com.hexvane.aetherhaven.quest;

import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.NO_BUILDINGS;
import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.QUEST;
import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.current;
import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.GrowerCatalog;
import com.hexvane.aetherhaven.workplace.WorkplaceArea;
import com.hexvane.aetherhaven.workplace.WorkplaceQuestSteps;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The four workplace objectives, against the quest as it ships: each is ticked by the thing a player really does,
 * for every workplace type, in any order, and stays ticked when the workplace is taken away again.
 */
@Tag("town")
class WorkplaceQuestObjectivesTest {
    private static final GrowerCatalog GROWERS = GrowerCatalog.loadFromClasspath(WorkplaceQuestObjectivesTest.class.getClassLoader());
    private final QuestDefinition quest = WorkplaceQuestFixture.shipped();
    private final QuestCatalog quests = WorkplaceQuestFixture.catalog(quest);

    private TownRecord startedTown(TownsfolkWorkType type) {
        var town = WorkplaceQuestFixture.townFor(type);
        town.addActiveQuest(QUEST);
        QuestProgressionService.reconcile(quest, NO_BUILDINGS, town, QUEST);
        return town;
    }

    /** The look at the world the game takes, here at a pretend one. */
    private Function<QuestObjective, WorldProof> proofs(TownRecord town, WorkplaceQuestFixture.World world) {
        return objective -> QuestProgressionService.needsWorld(objective)
            ? WorkplaceQuestSteps.stationProof(world, town, WorkplaceQuestSteps.type(objective.workType()), GROWERS)
            : WorldProof.NO;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.hexvane.aetherhaven.quest.WorkplaceQuestFixture#everyType")
    void eachStepIsTickedByDoingItForEveryWorkplaceType(TownsfolkWorkType type) {
        var town = startedTown(type);
        assertEquals(List.of(), done(quest, town), "A new town has done nothing yet");
        assertEquals("unlock", current(quest, town));

        // 1. The type is bought in the villager's upgrades. The purchase leaves its proof in the town's save.
        assertNull(town.getApprenticeWorkplaces().unlock(town, type));
        assertTrue(QuestProgressionService.reconcileActive(quests, NO_BUILDINGS, town));
        assertEquals(List.of("unlock"), done(quest, town));
        assertEquals("mark", current(quest, town));

        // 2. Two corners are marked with the Building Staff. Nothing is saved yet: only the event says so.
        assertTrue(QuestProgressionService.completeWhereverItStands(quests, NO_BUILDINGS, town, QuestProgressionService.WORKPLACE_MARKED, null));
        assertEquals(List.of("unlock", "mark"), done(quest, town));
        assertEquals("choose", current(quest, town));

        // 3. The type is chosen on the workplace page and the workplace is saved.
        WorkplaceArea area = WorkplaceQuestFixture.save(town, type, "First " + type.id);
        assertTrue(QuestProgressionService.reconcileActive(quests, NO_BUILDINGS, town));
        assertEquals(List.of("unlock", "mark", "choose"), done(quest, town));
        assertEquals("bench", current(quest, town));

        // 4. An empty workplace is not enough. Its own station set down inside it is.
        var world = new WorkplaceQuestFixture.World();
        assertFalse(QuestProgressionService.observeWorld(quest, town, QUEST, proofs(town, world)));
        assertEquals("bench", current(quest, town));
        WorkplaceQuestFixture.placeStation(world, type, area);
        assertTrue(QuestProgressionService.observeWorld(quest, town, QUEST, proofs(town, world)));
        assertEquals(List.of("unlock", "mark", "choose", "bench"), done(quest, town));

        // All that is left is telling Lyren, which only his dialogue does.
        assertEquals("talk", current(quest, town));
        assertFalse(QuestProgressionService.allObjectivesComplete(quest, town, QUEST));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.hexvane.aetherhaven.quest.WorkplaceQuestFixture#everyType")
    void theBenchStepAlsoTicksWhenTheStationIsSetDownOrUsed(TownsfolkWorkType type) {
        var town = startedTown(type);
        assertNull(town.getApprenticeWorkplaces().unlock(town, type));
        WorkplaceArea area = WorkplaceQuestFixture.save(town, type, "Site");
        var world = new WorkplaceQuestFixture.World();
        WorkplaceQuestFixture.placeStation(world, type, area);
        // The block a player has just set down is looked at where it stands and found to be this workplace's station.
        var site = WorkplaceQuestSteps.stationSiteAt(world, town, GROWERS, area.minX() + 1, area.floorY(), area.minZ() + 1);
        assertEquals(type, site == null ? null : site.type());
        assertNull(WorkplaceQuestSteps.stationSiteAt(world, town, GROWERS, area.minX() + 2, area.floorY(), area.minZ() + 1), "An empty block is no station");
        assertTrue(QuestProgressionService.completeWhereverItStands(
            quests, NO_BUILDINGS, town, QuestProgressionService.WORKPLACE_BENCH_PLACED, type));
        assertEquals("talk", current(quest, town));
    }

    @Test
    void theWrongBenchOrABenchOutsideTheWorkplaceDoesNotCount() {
        var town = startedTown(TownsfolkWorkType.SMELTING);
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        WorkplaceArea area = WorkplaceQuestFixture.save(town, TownsfolkWorkType.SMELTING, "Smeltery");
        assertTrue(QuestProgressionService.reconcileActive(quests, NO_BUILDINGS, town));
        var world = new WorkplaceQuestFixture.World();
        // An anvil in a smeltery is the bench of another type.
        world.bench(area.minX() + 1, area.floorY(), area.minZ() + 1, "Weapon_Bench", "Bench_Weapon");
        // A furnace one block outside the marked area, and one above its five blocks of height.
        world.bench(area.maxX(), area.floorY(), area.minZ(), "Furnace", "Bench_Furnace");
        world.bench(area.minX(), area.floorY() + area.height(), area.minZ(), "Furnace", "Bench_Furnace");
        assertEquals(WorldProof.NO, WorkplaceQuestSteps.stationProof(world, town, null, GROWERS));
        assertFalse(QuestProgressionService.observeWorld(quest, town, QUEST, proofs(town, world)));
        assertEquals("bench", current(quest, town));
        // The top block of the work band still counts.
        world.bench(area.minX(), area.floorY() + area.height() - 1, area.minZ(), "Furnace", "Bench_Furnace");
        assertEquals(WorldProof.YES, WorkplaceQuestSteps.stationProof(world, town, null, GROWERS));
    }

    @Test
    void aRanchNeedsItsTroughAndAFarmNeedsItsBenchThoughBothAreOneKindOfBench() {
        var ranch = startedTown(TownsfolkWorkType.RANCHING);
        assertNull(ranch.getApprenticeWorkplaces().unlock(ranch, TownsfolkWorkType.RANCHING));
        WorkplaceArea pen = WorkplaceQuestFixture.save(ranch, TownsfolkWorkType.RANCHING, "Pen");
        var world = new WorkplaceQuestFixture.World().bench(pen.minX(), pen.floorY(), pen.minZ(), "Farmingbench", "Bench_Farming");
        assertEquals(WorldProof.NO, WorkplaceQuestSteps.stationProof(world, ranch, null, GROWERS), "A Farmer's Workbench is not a trough");
        world.bench(pen.minX() + 1, pen.floorY(), pen.minZ(), "Farmingbench", "Bench_Trough");
        assertEquals(WorldProof.YES, WorkplaceQuestSteps.stationProof(world, ranch, null, GROWERS));

        var farm = startedTown(TownsfolkWorkType.FARMING);
        assertNull(farm.getApprenticeWorkplaces().unlock(farm, TownsfolkWorkType.FARMING));
        WorkplaceArea field = WorkplaceQuestFixture.save(farm, TownsfolkWorkType.FARMING, "Field");
        var fields = new WorkplaceQuestFixture.World().bench(field.minX(), field.floorY(), field.minZ(), "Farmingbench", "Bench_Trough");
        assertEquals(WorldProof.NO, WorkplaceQuestSteps.stationProof(fields, farm, null, GROWERS), "A trough is not a Farmer's Workbench");
    }

    @Test
    void aWorkplaceNobodyIsNearCannotBeJudgedYet() {
        var town = startedTown(TownsfolkWorkType.CRAFTING);
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.CRAFTING));
        WorkplaceArea area = WorkplaceQuestFixture.save(town, TownsfolkWorkType.CRAFTING, "Workshop");
        var world = new WorkplaceQuestFixture.World().unloaded();
        WorkplaceQuestFixture.placeStation(world, TownsfolkWorkType.CRAFTING, area);
        assertEquals(WorldProof.UNKNOWN, WorkplaceQuestSteps.stationProof(world, town, null, GROWERS));
        assertFalse(QuestProgressionService.observeWorld(quest, town, QUEST, proofs(town, world)), "Out of sight is not done");
        assertTrue(QuestProgressionService.awaitsWorld(quests, town), "The game keeps looking");
        // A player walks up to it.
        assertTrue(QuestProgressionService.observeWorld(quest, town, QUEST, proofs(town, world.loaded())));
        assertFalse(QuestProgressionService.awaitsWorld(quests, town));
    }

    @Test
    void stepsDoneOutOfOrderTickAtOnceAndNeverTickAnEarlierStepForThePlayer() {
        var town = startedTown(TownsfolkWorkType.BLACKSMITHING);
        // The player takes out the Building Staff and marks an area before unlocking anything.
        assertTrue(QuestProgressionService.completeWhereverItStands(quests, NO_BUILDINGS, town, QuestProgressionService.WORKPLACE_MARKED, null));
        assertEquals(List.of("mark"), done(quest, town), "Marking must not pretend a type was unlocked");
        assertEquals("unlock", current(quest, town), "The tracker goes back to the step that is still open");
        // Reading the quest again changes nothing.
        assertFalse(QuestProgressionService.reconcile(quest, NO_BUILDINGS, town, QUEST));
        assertEquals(List.of("mark"), done(quest, town));
        // Unlocking then skips straight past the step already done.
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.BLACKSMITHING));
        assertTrue(QuestProgressionService.reconcileActive(quests, NO_BUILDINGS, town));
        assertEquals("choose", current(quest, town));
    }

    @Test
    void whatTheTownAlreadyDidIsTickedTheMomentTheQuestStarts() {
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.COOKING);
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.COOKING));
        town.addActiveQuest(QUEST);
        QuestProgressionService.reconcile(quest, NO_BUILDINGS, town, QUEST);
        assertEquals(List.of("unlock"), done(quest, town), "A type unlocked earlier counts");
        assertEquals("mark", current(quest, town));

        var further = WorkplaceQuestFixture.townFor(TownsfolkWorkType.COOKING);
        assertNull(further.getApprenticeWorkplaces().unlock(further, TownsfolkWorkType.COOKING));
        WorkplaceQuestFixture.save(further, TownsfolkWorkType.COOKING, "Kitchen");
        further.addActiveQuest(QUEST);
        QuestProgressionService.reconcile(quest, NO_BUILDINGS, further, QUEST);
        assertEquals(List.of("unlock", "mark", "choose"), done(quest, further), "A workplace saved earlier was marked and given its type");
        assertEquals("bench", current(quest, further));
    }

    @Test
    void removingTheWorkplaceKeepsTheFinishedStepsAndTheQuestCarriesOn() {
        var town = startedTown(TownsfolkWorkType.SMELTING);
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        WorkplaceQuestFixture.save(town, TownsfolkWorkType.SMELTING, "Smeltery");
        // The page writes down what the workplace proves before it removes it.
        assertTrue(QuestProgressionService.reconcileActive(quests, NO_BUILDINGS, town));
        var site = town.getApprenticeWorkplaces().sites().getFirst();
        assertNull(town.getApprenticeWorkplaces().remove(town, town.getOwnerUuid(), site.id()));
        assertTrue(town.getApprenticeWorkplaces().sites().isEmpty());

        assertFalse(QuestProgressionService.reconcile(quest, NO_BUILDINGS, town, QUEST));
        assertEquals(List.of("unlock", "mark", "choose"), done(quest, town), "Finished steps stay finished");
        assertEquals("bench", current(quest, town));
        assertTrue(town.hasQuestActive(QUEST));
        // With no workplace there is nothing to look at and nothing breaks.
        assertFalse(QuestProgressionService.observeWorld(quest, town, QUEST, proofs(town, new WorkplaceQuestFixture.World())));

        // A new workplace, of another type the same villager offers, with its own bench finishes the step.
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.ARMOR_CRAFTING));
        WorkplaceArea area = WorkplaceQuestFixture.save(town, TownsfolkWorkType.ARMOR_CRAFTING, "Armory");
        var world = new WorkplaceQuestFixture.World();
        WorkplaceQuestFixture.placeStation(world, TownsfolkWorkType.ARMOR_CRAFTING, area);
        assertTrue(QuestProgressionService.observeWorld(quest, town, QUEST, proofs(town, world)));
        assertEquals("talk", current(quest, town));
    }

    @Test
    void aWorkplaceRemovedBeforeAnythingReadItStillCountsBecauseThePageReadsFirst() {
        var town = startedTown(TownsfolkWorkType.SMELTING);
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        WorkplaceQuestFixture.save(town, TownsfolkWorkType.SMELTING, "Smeltery");
        // Nobody has the quest on screen, so nothing has read it since the workplace was saved. The removal hook does.
        assertTrue(QuestProgressionService.reconcileActive(quests, NO_BUILDINGS, town), "This is the call the page makes before removing");
        var site = town.getApprenticeWorkplaces().sites().getFirst();
        assertNull(town.getApprenticeWorkplaces().remove(town, town.getOwnerUuid(), site.id()));
        assertEquals(List.of("unlock", "mark", "choose"), done(quest, town));
    }

    @Test
    void aSecondWorkplaceDoesNotStartTheQuestAgain() {
        var town = startedTown(TownsfolkWorkType.SMELTING);
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        WorkplaceQuestFixture.save(town, TownsfolkWorkType.SMELTING, "First");
        town.markQuestAutoStarted(QUEST);
        town.completeQuest(QUEST);
        // A second workplace, as a town that has learned how will make.
        WorkplaceQuestFixture.save(town, TownsfolkWorkType.SMELTING, "Second");
        assertFalse(QuestProgressionService.reconcileActive(quests, NO_BUILDINGS, town));
        assertFalse(QuestProgressionService.completeWhereverItStands(quests, NO_BUILDINGS, town, QuestProgressionService.WORKPLACE_MARKED, null));
        assertEquals(QuestAutoStartService.Decision.NOTHING, QuestAutoStartService.decide(quest, town, NO_BUILDINGS, o -> WorldProof.NO));
        assertFalse(town.hasQuestActive(QUEST));
        assertTrue(town.hasQuestCompleted(QUEST));
        assertTrue(town.getQuestObjectiveProgressSnapshot(QUEST).isEmpty(), "Nothing is tracked for a finished quest");
    }

    @Test
    void aTunnelPlannedFromAMineIsNotAWorkplaceOfItsOwn() {
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.MINING);
        assertFalse(WorkplaceQuestSteps.siteExists(town, null));
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.MINING));
        assertTrue(WorkplaceQuestSteps.typeUnlocked(town, null));
        assertFalse(WorkplaceQuestSteps.typeUnlocked(town, TownsfolkWorkType.SMELTING));
        WorkplaceQuestFixture.save(town, TownsfolkWorkType.MINING, "Mine base");
        assertEquals(1, WorkplaceQuestSteps.markedSites(town, null).size());
        assertTrue(WorkplaceQuestSteps.siteExists(town, TownsfolkWorkType.MINING));
        assertFalse(WorkplaceQuestSteps.siteExists(town, TownsfolkWorkType.SMELTING));
    }

    @Test
    void aQuestMayAskForOneTypeAndThenOnlyThatTypeCounts() {
        var smelting = new Gson().fromJson(
            """
            {"schemaVersion": 1, "id": "smelt_only", "objectives": [
              {"id": "unlock", "kind": "workplace_type_unlocked", "workType": "smelting"},
              {"id": "choose", "kind": "workplace_type_chosen", "workType": "smelting"},
              {"id": "bench", "kind": "workplace_bench_placed", "workType": "smelting"}]}
            """,
            QuestDefinition.class
        );
        var only = WorkplaceQuestFixture.catalog(smelting);
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
        town.addActiveQuest("smelt_only");
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.BLACKSMITHING));
        WorkplaceArea forge = WorkplaceQuestFixture.save(town, TownsfolkWorkType.BLACKSMITHING, "Forge");
        var world = new WorkplaceQuestFixture.World();
        WorkplaceQuestFixture.placeStation(world, TownsfolkWorkType.BLACKSMITHING, forge);
        QuestProgressionService.reconcileActive(only, NO_BUILDINGS, town);
        assertEquals(List.of(), done(smelting, town), "A forge is not a smeltery");
        assertFalse(QuestProgressionService.completeWhereverItStands(
            only, NO_BUILDINGS, town, QuestProgressionService.WORKPLACE_BENCH_PLACED, TownsfolkWorkType.BLACKSMITHING));
        assertEquals(WorldProof.NO, WorkplaceQuestSteps.stationProof(world, town, TownsfolkWorkType.SMELTING, GROWERS));

        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        WorkplaceArea smeltery = WorkplaceQuestFixture.save(town, TownsfolkWorkType.SMELTING, "Smeltery");
        WorkplaceQuestFixture.placeStation(world, TownsfolkWorkType.SMELTING, smeltery);
        QuestProgressionService.reconcileActive(only, NO_BUILDINGS, town);
        assertEquals(List.of("unlock", "choose"), done(smelting, town));
        assertEquals(WorldProof.YES, WorkplaceQuestSteps.stationProof(world, town, TownsfolkWorkType.SMELTING, GROWERS));
    }

    @Test
    void olderQuestsAreReadExactlyAsBefore() {
        // A later step still proves the earlier ones for the kinds that always worked that way.
        var house = new Gson().fromJson(
            """
            {"schemaVersion": 1, "id": "house", "objectives": [
              {"id": "token", "kind": "plot_token_received"},
              {"id": "build", "kind": "construction_built", "constructionId": "plot_house"},
              {"id": "talk", "kind": "dialogue_turn_in"}]}
            """,
            QuestDefinition.class
        );
        var town = WorkplaceQuestFixture.emptyTown();
        town.addActiveQuest("house");
        assertFalse(QuestProgressionService.reconcile(house, NO_BUILDINGS, town, "house"));
        town.completeQuestObjective("house", "build");
        assertTrue(QuestProgressionService.reconcile(house, NO_BUILDINGS, town, "house"));
        assertEquals(List.of("token", "build"), done(house, town));
        assertTrue(QuestProgressionService.mayImputeObjectiveCompleteFromWorldState(house, house.objectivesOrEmpty().get(0)));
        for (var objective : quest.objectivesOrEmpty()) {
            boolean workplace = QuestProgressionService.isWorkplaceKind(objective);
            assertEquals(!workplace, QuestProgressionService.mayImputeObjectiveCompleteFromWorldState(quest, objective), objective.id());
        }
    }
}
