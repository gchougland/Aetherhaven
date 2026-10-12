package com.hexvane.aetherhaven.production;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** P44: one full store must never stop the Farmer's other crops, and a carried harvest must never get stuck. */
@Tag("town")
class FieldFullStorageTest {
    private static final String WHEAT = "Plant_Crop_Wheat_Item", CARROT = "Plant_Crop_Carrot_Item";
    private final FieldProductionState.Bed wheatBed =
        new FieldProductionState.Bed(new WarehouseBatch.Point(1, 100, 2), "Plant_Crop_Wheat_Block", WHEAT);
    private final FieldProductionState.Bed carrotBed =
        new FieldProductionState.Bed(new WarehouseBatch.Point(3, 100, 2), "Plant_Crop_Carrot_Block", CARROT);
    private final ProductionCatalog.Entry entry = ProductionCatalog.Entry.create(
        List.of(WHEAT, NativeFarmCrops.LIFE_ESSENCE, CARROT), new int[] {1, 1, 1}, new long[] {10, 20, 10});

    /** A farm with three chosen outputs: wheat, Life Essence and carrot. */
    private PlotProductionState farm() {
        var p = new PlotProductionState();
        p.setIronUpgrade(1);
        p.setCobaltLevel(1);
        assertEquals(3, WorkplaceProductionUpgrades.slotCount(p));
        p.setSlotCursor(0, 0);
        p.setSlotCursor(1, 1);
        p.setSlotCursor(2, 2);
        assertNull(p.field().adopt(0, List.of(wheatBed, carrotBed)));
        return p;
    }

    private long cap(PlotProductionState p, String item) {
        return WorkplaceProductionUpgrades.effectiveMaxStorage(p, entry, item);
    }

    private void fill(PlotProductionState p, String item) {
        p.addAmount(item, cap(p, item), cap(p, item));
        assertEquals(cap(p, item), p.getAmount(item));
    }

    @Test
    void aFullLifeEssenceStoreDoesNotStopTheCrops() {
        var p = farm();
        assertTrue(NativeFarmCrops.canTend(p, entry, wheatBed));
        fill(p, NativeFarmCrops.LIFE_ESSENCE);
        // Before: every chosen output of a bed needed room, so full essence stopped every bed on the farm.
        assertTrue(NativeFarmCrops.canTend(p, entry, wheatBed), "Wheat still has room");
        assertTrue(NativeFarmCrops.canTend(p, entry, carrotBed), "Carrot still has room");
    }

    @Test
    void aFullCropStopsOnlyItsOwnBeds() {
        var p = farm();
        fill(p, WHEAT);
        assertFalse(NativeFarmCrops.canTend(p, entry, wheatBed), "Ripe wheat is left standing while wheat is full");
        assertTrue(NativeFarmCrops.canTend(p, entry, carrotBed), "The other crop is still harvested");
        fill(p, CARROT);
        assertFalse(NativeFarmCrops.canTend(p, entry, carrotBed));
    }

    @Test
    void aBedGrownOnlyForEssenceStopsWhenEssenceIsFull() {
        var p = farm();
        p.setSlotCursor(2, 1); // carrot is no longer a chosen output; its beds are worked for essence alone
        assertTrue(NativeFarmCrops.canTend(p, entry, carrotBed));
        fill(p, NativeFarmCrops.LIFE_ESSENCE);
        assertFalse(NativeFarmCrops.canTend(p, entry, carrotBed));
        assertTrue(NativeFarmCrops.canTend(p, entry, wheatBed));
    }

    /** A harvest of four wheat and three essence, settled into the worker's hands, with room for one wheat. */
    private PlotProductionState carryingMoreThanFits() {
        var p = farm();
        p.addAmount(WHEAT, cap(p, WHEAT) - 1, cap(p, WHEAT));
        assertTrue(NativeFarmCrops.canTend(p, entry, wheatBed), "One free place is enough to start a harvest");
        var yields = new LinkedHashMap<String, Integer>();
        yields.put(WHEAT, 4);
        yields.put(NativeFarmCrops.LIFE_ESSENCE, 3);
        assertTrue(p.field().beginHarvest(wheatBed, "Regrowing", yields, () -> true));
        assertTrue(p.field().settleHarvest(p.field().pending().id(), "Regrowing"));
        assertEquals(7, p.field().heldQuantity());
        return p;
    }

    @Test
    void theOldPartialDeliveryLeftTheRestInHerHandsWithNowhereToPutIt() {
        var p = carryingMoreThanFits();
        var field = p.field();
        assertEquals(1, field.deliver(p, field.cargo().id(), cap(p, WHEAT)));
        // This is the stall the playtest saw: wheat is full, three wheat are still carried, and a worker
        // who is carrying something cannot harvest any bed, whatever it grows.
        assertEquals(cap(p, WHEAT), p.getAmount(WHEAT));
        assertEquals(WHEAT, field.cargo().output());
        assertEquals(3, field.cargo().quantity());
        assertEquals(0, field.deliver(p, field.cargo().id(), cap(p, WHEAT)));
        assertFalse(field.beginHarvest(carrotBed, "Regrowing", () -> fail("Nothing is harvested while cargo is held")));
    }

    @Test
    void oneTripStoresWhatFitsLeavesTheRestAndFreesTheNextBed() {
        var p = carryingMoreThanFits();
        var field = p.field();
        var claim = field.cargo().id();

        assertEquals(-1, field.deliverAll(p, UUID.randomUUID(), item -> cap(p, item)), "A stale claim changes nothing");
        assertEquals(7, field.heldQuantity());

        assertEquals(4, field.deliverAll(p, claim, item -> cap(p, item)), "One wheat and all three essence fit");
        assertEquals(cap(p, WHEAT), p.getAmount(WHEAT));
        assertEquals(3, p.getAmount(NativeFarmCrops.LIFE_ESSENCE));
        assertNull(field.cargo());
        assertEquals(0, field.heldQuantity());
        assertEquals(-1, field.deliverAll(p, claim, item -> cap(p, item)), "The same harvest cannot be stored twice");

        var saved = new Gson().fromJson(new Gson().toJson(p), PlotProductionState.class);
        assertNull(saved.field().cargo());
        assertEquals(0, saved.field().heldQuantity());
        assertTrue(NativeFarmCrops.canTend(saved, entry, carrotBed));
        assertTrue(saved.field().beginHarvest(carrotBed, "Regrowing", () -> true), "The carrot bed is harvested next");
    }

    @Test
    void aTripToCompletelyFullStoresStillEmptiesHerHands() {
        var p = carryingMoreThanFits();
        fill(p, WHEAT);
        fill(p, NativeFarmCrops.LIFE_ESSENCE);
        long wheat = p.getAmount(WHEAT), essence = p.getAmount(NativeFarmCrops.LIFE_ESSENCE);
        assertEquals(0, p.field().deliverAll(p, p.field().cargo().id(), item -> cap(p, item)));
        assertNull(p.field().cargo());
        assertEquals(wheat, p.getAmount(WHEAT), "Nothing is stored above capacity");
        assertEquals(essence, p.getAmount(NativeFarmCrops.LIFE_ESSENCE));
        assertTrue(NativeFarmCrops.canTend(p, entry, carrotBed));
    }
}
