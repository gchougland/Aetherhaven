package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("town")
class BuildingQuestResidentRecoveryTest {
    private final Gson gson = new Gson();

    @ParameterizedTest
    @CsvSource({"Aetherhaven_Blacksmith,plot_blacksmith_shop,q_blacksmith_shop",
                "CozyFishing_Fisherman,plot_fishermans_shop,q_fisherman_shop"})
    void completedWorkplaceQuestCanRecoverAnUnregisteredVillager(String role, String work, String questId) {
        var def = gson.fromJson("{\"npcRoleId\":\"" + role + "\",\"innPoolEligible\":true,"
            + "\"workConstructionId\":\"" + work + "\"}", VillagerDefinition.class);
        var quest = gson.fromJson("{\"id\":\"" + questId + "\",\"grantPlotTokenConstructionId\":\""
            + work + "\"}", QuestDefinition.class);
        var catalog = QuestCatalog.of(Map.of(questId, quest));
        var town = new TownRecord();
        var plot = gson.fromJson("{\"plotId\":\"" + UUID.randomUUID() + "\",\"constructionId\":\""
            + work + "\",\"state\":\"COMPLETE\"}", PlotInstance.class);
        town.getPlotInstances().add(plot);
        assertTrue(town.getResidentNpcRecords().isEmpty());
        assertTrue(town.getInnPoolNpcIds().isEmpty());
        assertNull(BuildingQuestResidentReconcileService.readyWorkplaceForQuest(
            catalog, ConstructionCatalog.empty(), town, def, questId));
        town.addActiveQuest(questId);
        assertSame(plot, BuildingQuestResidentReconcileService.readyWorkplaceForQuest(
            catalog, ConstructionCatalog.empty(), town, def, questId));
        town.completeQuest(questId);
        assertSame(plot, BuildingQuestResidentReconcileService.readyWorkplaceForQuest(
            catalog, ConstructionCatalog.empty(), town, def, questId));
        town.addActiveQuest("q_house_blacksmith");
        assertNull(BuildingQuestResidentReconcileService.readyWorkplaceForQuest(
            catalog, ConstructionCatalog.empty(), town, def, "q_house_blacksmith"));
        town.getPlotInstances().clear();
        assertNull(BuildingQuestResidentReconcileService.readyWorkplaceForQuest(
            catalog, ConstructionCatalog.empty(), town, def, questId));
    }

    @Test
    void orphanRecoveryDoesNotTakeVillagersFromAnotherTown() {
        UUID town = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String otherHandle = "Villager_blacksmith_" + other.toString().replace("-", "").substring(0, 8);
        assertTrue(BuildingQuestResidentReconcileService.recoveryBelongsToTown(town, null, null, true));
        assertFalse(BuildingQuestResidentReconcileService.recoveryBelongsToTown(town, null, null, false));
        assertFalse(BuildingQuestResidentReconcileService.recoveryBelongsToTown(town, other, null, true));
        assertFalse(BuildingQuestResidentReconcileService.recoveryBelongsToTown(town, null, otherHandle, true));
        assertTrue(BuildingQuestResidentReconcileService.recoveryBelongsToTown(town, town, null, false));
    }
}
