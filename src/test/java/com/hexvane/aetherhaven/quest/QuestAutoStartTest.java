package com.hexvane.aetherhaven.quest;

import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.NO_BUILDINGS;
import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.QUEST;
import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.current;
import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.quest.QuestAutoStartService.Decision;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceGuide;
import com.hexvane.aetherhaven.workplace.WorkplaceRules;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * When the first workplace quest is given to a town by itself: once a villager who can supervise workplaces lives in
 * a finished building, for any such villager, for towns from before the quest existed too, and never twice.
 */
@Tag("town")
class QuestAutoStartTest {
    private static final Function<QuestObjective, WorldProof> NOTHING_THERE = objective -> WorldProof.NO;
    private static final Function<QuestObjective, WorldProof> BENCH_THERE = objective -> WorldProof.YES;
    private static final Function<QuestObjective, WorldProof> OUT_OF_SIGHT = objective -> WorldProof.UNKNOWN;
    private final QuestDefinition quest = WorkplaceQuestFixture.shipped();

    private Decision decide(TownRecord town, Function<QuestObjective, WorldProof> world) {
        return QuestAutoStartService.decide(quest, town, NO_BUILDINGS, world);
    }

    @Test
    void theShippedQuestStartsByItselfAndIsPinned() {
        assertNotNull(quest.autoStart());
        assertEquals(QuestAutoStartService.WHEN_WORKPLACE_SUPERVISOR_READY, quest.autoStart().whenOrEmpty());
        assertTrue(QuestAutoStartService.isKnownCondition(quest.autoStart().whenOrEmpty()));
        assertTrue(quest.autoStart().skipWhenDone());
        assertTrue(quest.autoPin(), "The quest must end up on the HUD tracker with nobody pinning it");
        assertEquals("town", quest.category(), "Town quests are shared by every member");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.hexvane.aetherhaven.quest.WorkplaceQuestFixture#everyType")
    void itStartsWhenTheVillagerOfAnyWorkplaceTypeHasAFinishedBuilding(TownsfolkWorkType type) {
        var town = WorkplaceQuestFixture.emptyTown();
        assertEquals(Decision.NOTHING, decide(town, NOTHING_THERE), "An empty town has nobody to unlock a type");
        WorkplaceQuestFixture.moveIn(town, WorkplaceRules.supervisor(type));
        assertEquals(Decision.START, decide(town, NOTHING_THERE));
        assertTrue(QuestAutoStartService.offerable(quest, town, NO_BUILDINGS));
    }

    @Test
    void aVillagerWithNoFinishedBuildingOrABuildingWithNoVillagerIsNotEnough() {
        var visitor = WorkplaceQuestFixture.emptyTown();
        visitor.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Blacksmith", "blacksmith", null, UUID.randomUUID()));
        assertEquals(Decision.NOTHING, decide(visitor, NOTHING_THERE), "The Blacksmith lives in town but has no shop yet");

        var building = WorkplaceQuestFixture.emptyTown();
        var shop = new PlotInstance(UUID.randomUUID(), "plot_blacksmith_shop", PlotInstanceState.BLUEPRINTING,
            new PlotFootprintRecord(0, 100, 0, 8, 110, 8), 0, 100, 0, 0L);
        building.addPlotInstance(shop);
        building.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Blacksmith", "blacksmith", shop.getPlotId(), UUID.randomUUID()));
        assertEquals(Decision.NOTHING, decide(building, NOTHING_THERE), "The shop is still being built");
        shop.setState(PlotInstanceState.COMPLETE);
        assertEquals(Decision.START, decide(building, NOTHING_THERE), "The moment it is finished the quest is due");

        var empty = WorkplaceQuestFixture.emptyTown();
        empty.addPlotInstance(new PlotInstance(UUID.randomUUID(), "plot_blacksmith_shop", PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0, 100, 0, 8, 110, 8), 0, 100, 0, 0L));
        assertEquals(Decision.NOTHING, decide(empty, NOTHING_THERE), "A finished shop with nobody in it cannot unlock anything");
    }

    @Test
    void aBuildingIsFoundByWhatItIsWhenTheVillagerHasNoPlotOnRecord() {
        // Saves from before villagers recorded their plot: the finished shop is still recognised as the Blacksmith's.
        var town = WorkplaceQuestFixture.emptyTown();
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Blacksmith", "blacksmith", null, UUID.randomUUID()));
        town.addPlotInstance(new PlotInstance(UUID.randomUUID(), "plot_blacksmith_shop", PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0, 100, 0, 8, 110, 8), 0, 100, 0, 0L));
        var buildings = ConstructionCatalog.loadFromAssetPacksOrClasspath(getClass().getClassLoader());
        assertEquals(Decision.START, QuestAutoStartService.decide(quest, town, buildings, NOTHING_THERE));
        assertEquals("plot_blacksmith_shop", WorkplaceGuide.supervisors(town, buildings).getFirst().constructionId());
        // A house is nobody's workplace.
        var houses = WorkplaceQuestFixture.emptyTown();
        houses.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Blacksmith", "blacksmith", null, UUID.randomUUID()));
        houses.addPlotInstance(new PlotInstance(UUID.randomUUID(), "plot_house", PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0, 100, 0, 8, 110, 8), 0, 100, 0, 0L));
        assertEquals(Decision.NOTHING, QuestAutoStartService.decide(quest, houses, buildings, NOTHING_THERE));
    }

    @Test
    void villagersWhoSuperviseNoWorkplaceDoNotStartIt() {
        var town = WorkplaceQuestFixture.emptyTown();
        for (String kind : List.of("elder", "innkeeper", "warehouse_manager", "logger", "priestess", "guild_master", "townsfolk"))
            WorkplaceQuestFixture.moveIn(town, kind);
        assertEquals(Decision.NOTHING, decide(town, NOTHING_THERE));
    }

    /** A town as an older version wrote it: the save knows nothing of quests that start by themselves. */
    private TownRecord oldSave(TownRecord town) {
        var json = new Gson().toJsonTree(town).getAsJsonObject();
        json.remove("autoStartedQuestIds");
        return new Gson().fromJson(json, TownRecord.class);
    }

    @Test
    void anOlderSaveWithASupervisingBuildingAndNoWorkplaceIsGivenTheQuestOnLoad() {
        var town = oldSave(WorkplaceQuestFixture.townFor(TownsfolkWorkType.FARMING));
        assertFalse(town.wasQuestAutoStarted(QUEST));
        assertEquals(Decision.START, decide(town, OUT_OF_SIGHT), "No chunk needs to be loaded to know nothing was done");
    }

    @Test
    void anOlderSaveThatUnlockedATypeButMarkedNothingIsGivenTheQuestWithThatStepDone() {
        var saved = WorkplaceQuestFixture.townFor(TownsfolkWorkType.FARMING);
        assertNull(saved.getApprenticeWorkplaces().unlock(saved, TownsfolkWorkType.FARMING));
        var town = oldSave(saved);
        assertEquals(Decision.START, decide(town, OUT_OF_SIGHT));
        town.addActiveQuest(QUEST);
        QuestProgressionService.reconcile(quest, NO_BUILDINGS, town, QUEST);
        assertEquals(List.of("unlock"), done(quest, town));
        assertEquals("mark", current(quest, town));
    }

    @Test
    void anOlderSaveWithAWorkingWorkplaceIsNotOfferedTheQuest() {
        var saved = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
        assertNull(saved.getApprenticeWorkplaces().unlock(saved, TownsfolkWorkType.SMELTING));
        WorkplaceQuestFixture.save(saved, TownsfolkWorkType.SMELTING, "Old smeltery");
        var town = oldSave(saved);
        // Its chunks are not loaded while the world starts: nothing is decided, and nothing is shown.
        assertEquals(Decision.WAIT, decide(town, OUT_OF_SIGHT));
        assertFalse(town.hasQuestActive(QUEST));
        // A member walks up to it and the furnace is seen: the town knows how, so the quest is marked done quietly.
        assertEquals(Decision.COMPLETE_SILENTLY, decide(town, BENCH_THERE));
    }

    @Test
    void anOlderSaveWithAWorkplaceThatHasNoBenchIsGivenTheQuestAtItsLastStep() {
        var saved = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
        assertNull(saved.getApprenticeWorkplaces().unlock(saved, TownsfolkWorkType.SMELTING));
        WorkplaceQuestFixture.save(saved, TownsfolkWorkType.SMELTING, "Bare floor");
        var town = oldSave(saved);
        assertEquals(Decision.START, decide(town, NOTHING_THERE));
        town.addActiveQuest(QUEST);
        QuestProgressionService.reconcile(quest, NO_BUILDINGS, town, QUEST);
        assertEquals(List.of("unlock", "mark", "choose"), done(quest, town));
        assertEquals("bench", current(quest, town));
    }

    @Test
    void itIsGivenOnceAndNeverPushedAgain() {
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.MINING);
        assertEquals(Decision.START, decide(town, NOTHING_THERE));
        town.addActiveQuest(QUEST);
        assertTrue(town.markQuestAutoStarted(QUEST));
        assertFalse(town.markQuestAutoStarted(QUEST));
        assertEquals(Decision.NOTHING, decide(town, NOTHING_THERE), "It is running");

        // The player leaves the quest in the journal.
        town.clearActiveQuest(QUEST);
        assertEquals(Decision.NOTHING, decide(town, NOTHING_THERE), "An abandoned quest is not pushed on the town again");
        assertTrue(QuestAutoStartService.offerable(quest, town, NO_BUILDINGS), "Lyren can still offer it when asked");

        // Taken again from Lyren and finished: gone for good, however many buildings follow.
        town.addActiveQuest(QUEST);
        town.completeQuest(QUEST);
        WorkplaceQuestFixture.moveIn(town, "blacksmith");
        assertEquals(Decision.NOTHING, decide(town, NOTHING_THERE));
        assertFalse(QuestAutoStartService.offerable(quest, town, NO_BUILDINGS));

        // The mark survives a save.
        var reloaded = new Gson().fromJson(new Gson().toJson(town), TownRecord.class);
        assertTrue(reloaded.wasQuestAutoStarted(QUEST));
        assertTrue(reloaded.hasQuestCompleted(QUEST));
    }

    @Test
    void aQuestWithoutTheSkipRuleAlwaysStartsAndOneWithAnUnknownConditionNeverDoes() {
        var always = new Gson().fromJson(
            """
            {"schemaVersion": 1, "id": "always", "autoStart": {"when": "workplace_supervisor_ready"},
             "objectives": [{"id": "unlock", "kind": "workplace_type_unlocked"}]}
            """,
            QuestDefinition.class
        );
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        assertEquals(Decision.START, QuestAutoStartService.decide(always, town, NO_BUILDINGS, BENCH_THERE));

        var typo = new Gson().fromJson(
            "{\"schemaVersion\": 1, \"id\": \"typo\", \"autoStart\": {\"when\": \"workplace_superviser_ready\"}}",
            QuestDefinition.class
        );
        assertFalse(QuestAutoStartService.isKnownCondition(typo.autoStart().whenOrEmpty()));
        assertEquals(Decision.NOTHING, QuestAutoStartService.decide(typo, town, NO_BUILDINGS, BENCH_THERE));

        var plain = new Gson().fromJson("{\"schemaVersion\": 1, \"id\": \"plain\"}", QuestDefinition.class);
        assertNull(plain.autoStart());
        assertFalse(plain.autoPin());
        assertEquals(Decision.NOTHING, QuestAutoStartService.decide(plain, town, NO_BUILDINGS, BENCH_THERE));
    }

    @Test
    void aQuestWaitingOnAnEarlierQuestDoesNotStartBeforeIt() {
        var later = new Gson().fromJson(
            """
            {"schemaVersion": 1, "id": "later", "autoStart": {"when": "workplace_supervisor_ready"},
             "prerequisites": {"completedQuestIds": ["q_build_inn"]}}
            """,
            QuestDefinition.class
        );
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
        assertEquals(Decision.NOTHING, QuestAutoStartService.decide(later, town, NO_BUILDINGS, NOTHING_THERE));
        town.completeQuest("q_build_inn");
        assertEquals(Decision.START, QuestAutoStartService.decide(later, town, NO_BUILDINGS, NOTHING_THERE));
    }
}
