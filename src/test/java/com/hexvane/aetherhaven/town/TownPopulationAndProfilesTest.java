package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TownPopulationAndProfilesTest {
    private final Gson gson = new Gson();

    @Test void foundersConsumeTwoPlacesAndMarketplaceFoodSourcesWorkWithoutUpdatingTheBuilding() {
        TownRecord town = founders();
        town.getPlotInstances().add(plot("community_inn"));
        var alias = gson.fromJson("{\"id\":\"community_inn\",\"countsAsConstructionId\":\"plot_inn\"}", ConstructionDefinition.class);
        var catalog = ConstructionCatalog.forTests(Map.of("community_inn", alias));
        var snapshot = TownPopulation.snapshot(town, catalog);
        assertEquals(2, snapshot.residents());
        assertEquals(10, snapshot.foodPoints());
        assertEquals(5, snapshot.capacity());
        assertTrue(snapshot.canAdmit());
        town.getPlotInstances().add(plot("community_inn"));
        assertEquals(10, TownPopulation.snapshot(town, catalog).foodPoints(), "Extra Inn buildings must not multiply food support");
    }

    @Test void lastFoodPlaceIsConsumedAndDeathDoesNotFreeIt() {
        TownRecord town = founders();
        town.getPlotInstances().add(plot("plot_inn"));
        for (int i = 0; i < 3; i++) town.getResidentNpcRecords().add(new ResidentNpcRecord("Core_" + i, "worker", null, UUID.randomUUID()));
        assertFalse(TownPopulation.snapshot(town, ConstructionCatalog.empty()).canAdmit());
        town.getResidentNpcRecords().getLast().setPendingDawnRevival(true);
        assertEquals(5, TownPopulation.count(town));
        assertEquals(2, TownPopulation.snapshot(town, ConstructionCatalog.empty()).foodShortfallForNextResident());
    }

    @Test void repeatedResidentAndHomeReferencesCountOnlyOnceAndVisitorsDoNotCount() {
        TownRecord town = founders();
        UUID elder = town.getElderEntityUuid();
        town.getResidentNpcRecords().add(new ResidentNpcRecord(AetherhavenConstants.ELDER_NPC_ROLE_ID, TownVillagerBinding.KIND_ELDER, null, elder));
        var house = plot("plot_house");
        house.setHomeResidentAt(0, elder);
        town.getPlotInstances().add(house);
        UUID visitor = UUID.randomUUID();
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Visitor", "visitor_farmer", null, visitor));
        town.getInnPoolNpcIds().add(visitor.toString());
        assertEquals(2, TownPopulation.count(town));
    }

    @Test void populationBandsAndProgressDoNotOverflow() {
        assertEquals(1, TownPopulation.levelFor(3));
        assertEquals(2, TownPopulation.levelFor(4));
        assertEquals(3, TownPopulation.levelFor(7));
        assertEquals(4, TownPopulation.levelFor(11));
        assertEquals(5, TownPopulation.levelFor(16));
        assertEquals(6, TownPopulation.levelFor(Integer.MAX_VALUE));
        var snapshot = TownPopulation.snapshot(founders(), ConstructionCatalog.empty());
        assertEquals(4, snapshot.nextLevelPopulation());
        assertEquals(.5f, snapshot.levelProgress());
    }

    @Test void deathRegistryKeepsSeparateGuardsAndGaiaPreservesUnloadedRecovery() {
        TownRecord town = founders();
        var alive = new ResidentNpcRecord(AetherhavenConstants.ELDER_NPC_ROLE_ID, TownVillagerBinding.KIND_ELDER, null, UUID.randomUUID());
        town.getResidentNpcRecords().add(alive);
        for (String id : List.of("first", "second")) {
            var dead = new ResidentNpcRecord("Aetherhaven_Guard_Knight", TownVillagerBinding.KIND_GUARD, null, UUID.randomUUID());
            dead.setTownsfolkIdentity(new TownsfolkCharacterBinding(id, "", "guard", "Model", List.of("gardener")));
            dead.setPendingDawnRevival(true);
            town.getResidentNpcRecords().add(dead);
        }
        assertEquals(2, ResidentRegistryService.deadResidents(town).size());
        assertEquals(4, TownPopulation.count(town));
        var loaded = gson.fromJson(gson.toJson(town), TownRecord.class);
        assertEquals(2, ResidentRegistryService.deadResidents(loaded).size());
        assertEquals("first", ResidentRegistryService.deadResidents(loaded).getFirst().getTownsfolkIdentity().getCharacterId());
        assertFalse(VillagerRevivalService.appliesUnloadedRevivalGuard(alive), "Preserve recovery for missing or unloaded villagers");
    }

    @Test void trainingSurvivesTownSaveAndExplicitIncapabilityCannotBeTrained() {
        var progress = new TownsfolkProgress();
        progress.applyStartingValues(Map.of("intelligence", 8), Map.of("farming", 5), false);
        progress.applyStartingValues(Map.of(), Map.of("farming", 0), true);
        assertFalse(progress.train(TownsfolkSkill.FARMING));
        assertEquals(0, progress.skillCost(TownsfolkSkill.FARMING));
        assertTrue(progress.train(TownsfolkSkill.COOKING));
        assertEquals(2, progress.skill(TownsfolkSkill.COOKING));
        var town = founders();
        town.getTownsfolkProgress().put("person", progress);
        var restored = gson.fromJson(gson.toJson(town), TownRecord.class).getTownsfolkProgress().get("person");
        assertEquals(2, restored.skill(TownsfolkSkill.COOKING));
        assertEquals(8, restored.attribute(TownsfolkAttribute.INTELLIGENCE));
        assertEquals(0, restored.skill(TownsfolkSkill.FARMING));
    }

    @Test void oldSavesGetDefaultsAndCorruptValuesAreBounded() {
        var old = gson.fromJson("{}", TownsfolkProgress.class);
        assertEquals(1, old.skill(TownsfolkSkill.ALCHEMY));
        var corrupt = gson.fromJson("{\"skills\":{\"alchemy\":999},\"attributes\":null}", TownsfolkProgress.class);
        assertEquals(10, corrupt.skill(TownsfolkSkill.ALCHEMY));
        assertFalse(corrupt.train(TownsfolkSkill.ALCHEMY));
        assertEquals(5, corrupt.attribute(TownsfolkAttribute.STRENGTH));
    }

    @Test void authoredStartingAttributesLeaveUnspecifiedAttributesAtDefault() {
        var mage = gson.fromJson("{\"equipmentProfileId\":\"guard_mage\",\"startingAttributes\":{\"intelligence\":8}}", TownsfolkCharacterDefinition.class);
        var archer = gson.fromJson("{\"equipmentProfileId\":\"guard_archer\",\"startingAttributes\":{\"dexterity\":8}}", TownsfolkCharacterDefinition.class);
        assertEquals(8, TownsfolkProgressService.startingProgress(mage).attribute(TownsfolkAttribute.INTELLIGENCE));
        assertEquals(8, TownsfolkProgressService.startingProgress(archer).attribute(TownsfolkAttribute.DEXTERITY));
        assertEquals(5, TownsfolkProgressService.startingProgress(archer).attribute(TownsfolkAttribute.STRENGTH));
    }

    private TownRecord founders() {
        TownRecord town = new TownRecord();
        town.setElderEntityUuid(UUID.randomUUID());
        town.setInnkeeperEntityUuid(UUID.randomUUID());
        return town;
    }

    private PlotInstance plot(String construction) {
        return gson.fromJson("{\"plotId\":\"" + UUID.randomUUID() + "\",\"constructionId\":\"" + construction + "\",\"state\":\"COMPLETE\"}", PlotInstance.class);
    }
}
