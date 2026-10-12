package com.hexvane.aetherhaven.production;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** How much of each crop a farm keeps before the Farmer stops harvesting it. */
@Tag("town")
class FarmStorageTest {
    private static final String FARM = "plot_farm", WHEAT = "Plant_Crop_Wheat_Item";

    @Test void everyFarmCropStartsAtTwentyFiveAndLifeEssenceKeepsItsHundred() {
        var catalog = ProductionCatalog.loadFromClasspath(getClass().getClassLoader());
        var unlocks = WorkplaceUnlockCatalog.loadFromClasspath(getClass().getClassLoader());
        var base = catalog.get(FARM);
        assertNotNull(base);
        int crops = 0;
        for (int i = 0; i < base.catalogSize(); i++) {
            String item = base.itemAtCursor(i);
            assertEquals(NativeFarmCrops.LIFE_ESSENCE.equals(item) ? 100 : 25, base.maxStorageAtCursor(i), item);
        }
        for (var line : unlocks.linesForWorkplace(FARM)) {
            boolean essence = NativeFarmCrops.LIFE_ESSENCE.equals(line.itemId());
            assertEquals(essence ? 100 : 25, line.maxStorage(), line.itemId());
            if (!essence) { assertTrue(line.itemId().startsWith("Plant_Crop_") && line.itemId().endsWith("_Item"), line.itemId()); crops++; }
        }
        assertEquals(14, crops, "Every crop the farm can unlock");
    }

    @Test void capacityUpgradesStillMultiplyTheSmallerStart() {
        var catalog = ProductionCatalog.loadFromClasspath(getClass().getClassLoader());
        var unlocks = WorkplaceUnlockCatalog.loadFromClasspath(getClass().getClassLoader());
        var state = new PlotProductionState();
        var entry = ProductionEffectiveCatalog.effective(catalog, unlocks, FARM, state);
        assertNotNull(entry);
        for (int tier = 0; tier <= WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL; tier++) {
            state.setAdamantineLevel(tier);
            assertEquals(25L * (tier + 1), WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, WHEAT), "Capacity tier " + tier);
            assertEquals(100L * (tier + 1), WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, NativeFarmCrops.LIFE_ESSENCE));
        }
    }

    @Test void aFarmAlreadyHoldingMoreKeepsItAndTakesNoMoreUntilItDropsBelow() {
        var catalog = ProductionCatalog.loadFromClasspath(getClass().getClassLoader());
        var unlocks = WorkplaceUnlockCatalog.loadFromClasspath(getClass().getClassLoader());
        var state = new PlotProductionState();
        var entry = ProductionEffectiveCatalog.effective(catalog, unlocks, FARM, state);
        state.addAmount(WHEAT, 50, 50);
        long limit = WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, WHEAT);
        assertEquals(25, limit);
        assertEquals(50, state.getAmount(WHEAT), "Nothing is thrown away");
        state.addAmount(WHEAT, 1, limit);
        assertEquals(50, state.getAmount(WHEAT), "Adding against a smaller limit must not shrink saved stock");
        // A player takes all of it with a full pack: it is put back whole.
        assertEquals(50, state.removeAmountUpTo(WHEAT, 50));
        state.restoreAmount(WHEAT, 50);
        assertEquals(50, state.getAmount(WHEAT));
        var bed = new FieldProductionState.Bed(new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(1, 100, 1), "Plant_Crop_Wheat_Block", WHEAT);
        assertNull(state.field().adopt(0, java.util.List.of(bed)));
        assertTrue(state.field().harvest(bed, 2, () -> true));
        var cargo = state.field().cargo();
        assertEquals(0, state.field().deliver(state, cargo.id(), limit), "A full farm takes no more");
        assertEquals(50, state.getAmount(WHEAT));
        assertEquals(30, state.removeAmountUpTo(WHEAT, 30));
        assertEquals(2, state.field().deliver(state, cargo.id(), limit));
        assertEquals(22, state.getAmount(WHEAT));
    }
}
