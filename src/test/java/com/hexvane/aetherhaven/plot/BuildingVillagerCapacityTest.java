package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
final class BuildingVillagerCapacityTest {
    private static final Gson GSON = new Gson();

    @Test
    void aLargeHouseSupportsAllItsResidents() {
        BuildingVillagerCapacity capacity = capacityOf(
            "{\"id\":\"big_house\",\"countsAsConstructionId\":\"plot_house\",\"maxHomeResidents\":4}"
        );
        assertEquals(4, capacity.homeResidents());
        assertEquals(List.of(), capacity.workerKinds());
        assertFalse(capacity.isEmpty());
    }

    @Test
    void aBuildingCountingAsBothSupportsAWorkerAndAResident() {
        BuildingVillagerCapacity capacity = capacityOf(
            "{\"id\":\"smithy_home\",\"countsAsConstructionId\":[\"plot_blacksmith_shop\",\"plot_house\"]}"
        );
        assertEquals(List.of(TownVillagerBinding.KIND_BLACKSMITH), capacity.workerKinds());
        assertEquals(1, capacity.homeResidents());
    }

    @Test
    void residentSlotsOnlyCountOnHouses() {
        BuildingVillagerCapacity capacity = capacityOf(
            "{\"id\":\"smithy\",\"countsAsConstructionId\":\"plot_blacksmith_shop\",\"maxHomeResidents\":4}"
        );
        assertEquals(0, capacity.homeResidents());
        assertEquals(List.of(TownVillagerBinding.KIND_BLACKSMITH), capacity.workerKinds());
    }

    @Test
    void theGuildHallStaffsTwoAndAPlayerShopOneClerk() {
        assertEquals(
            List.of(TownVillagerBinding.KIND_GUILD_MASTER, TownVillagerBinding.KIND_BARD),
            capacityOf("{\"id\":\"plot_guild_hall\"}").workerKinds()
        );
        assertTrue(capacityOf("{\"id\":\"plot_player_shop\"}").shopClerk());
    }

    @Test
    void decorationsAndParksSupportNobody() {
        assertTrue(capacityOf(
            "{\"id\":\"cottage\",\"countsAsConstructionId\":\"plot_house\",\"decorationPlot\":true,\"maxHomeResidents\":4}"
        ).isEmpty());
        assertTrue(capacityOf("{\"id\":\"plot_park\"}").isEmpty());
    }

    private static BuildingVillagerCapacity capacityOf(String json) {
        ConstructionDefinition def = GSON.fromJson(json, ConstructionDefinition.class);
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(def.getId(), def));
        return BuildingVillagerCapacity.of(catalog, def);
    }
}
