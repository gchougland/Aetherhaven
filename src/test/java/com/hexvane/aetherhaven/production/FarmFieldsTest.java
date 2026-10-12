package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.production.FarmFieldWork.Action;
import com.hexvane.aetherhaven.production.FarmFieldWork.View;
import com.hexvane.aetherhaven.production.FarmUpgrades.Track;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.FarmFieldsState.*;

/** The Scarecrow Fields without a server: the plan, the Farmer's choices, soil, compost, seeds, the Almanac, upgrades and old saves. */
@Tag("town")
class FarmFieldsTest {
    private static final String WHEAT = "Plant_Crop_Wheat_Item", LETTUCE = "Plant_Crop_Lettuce_Item", CORN = "Plant_Crop_Corn_Item";
    private static final String WHEAT_SEED = "Plant_Seeds_Wheat", ESSENCE = NativeFarmCrops.LIFE_ESSENCE, GOLDEN = "Aetherhaven_Golden_Harvest";
    private static final Point SCARECROW = new Point(40, 100, 40);
    private final Gson gson = new Gson();
    private final FarmFieldRules rules = FarmFieldRules.get();

    private static PlotProductionState farm() {
        var production = new PlotProductionState(); assertTrue(FarmUpgrades.mapLegacy(production)); return production;
    }
    private static com.hexvane.aetherhaven.town.TownRecord town() {
        return new com.hexvane.aetherhaven.town.TownRecord(UUID.randomUUID(), UUID.randomUUID(), "world", 0, 100, 0, 1, 1, 0);
    }
    private static ProductionCatalog.Entry catalog(PlotProductionState production) {
        return ProductionEffectiveCatalog.effective(ProductionCatalog.loadFromClasspath(FarmFieldsTest.class.getClassLoader()),
            WorkplaceUnlockCatalog.loadFromClasspath(FarmFieldsTest.class.getClassLoader()), FarmFields.CONSTRUCTION, production);
    }
    /** One harvest of the planned crop with nothing left to chance: no Golden Harvest and no seed back unless asked for. */
    private Harvest harvest(FarmFieldsState fields, int x, String crop, int rolled, boolean favoured, boolean golden, boolean seed) {
        return fields.harvest(HOME, x, 0, crop, NativeFarmCrops.seedFor(crop), rolled, 2, favoured, 0.5, golden ? 0.0 : 0.999, seed ? 0.0 : 0.999, rules);
    }

    // The plan --------------------------------------------------------------------------------------------------

    @Test void paintingACropIsTheOnlyStepThatStartsFieldWork() {
        var production = farm(); var fields = production.farm();
        assertFalse(production.usesFarmFields(), "A farm with no plan keeps its automatic production");
        assertFalse(production.usesPhysicalField());
        assertNotNull(fields.field(HOME), "Every farm has its own field from the start");
        assertEquals(0, fields.plannedCount());
        assertTrue(fields.paint(HOME, 5, 101, 7, WHEAT));
        assertTrue(production.usesFarmFields()); assertTrue(production.usesPhysicalField(), "Automatic production stops once a plan exists");
        assertFalse(fields.paint(HOME, 5, 101, 7, WHEAT), "Painting the same crop again changes nothing");
        assertEquals(new Point(5, 101, 7), fields.cell(HOME, 5, 7).point()); assertEquals(GOOD, fields.soil(HOME, 5, 7));
        assertTrue(fields.paint(HOME, 5, 101, 7, LETTUCE)); assertEquals(LETTUCE, fields.cell(HOME, 5, 7).crop());
        assertEquals(1, fields.plannedCount()); assertEquals(HOME, fields.owner(5, 7)); assertEquals(-1, fields.owner(6, 7));
        assertFalse(fields.paint(HOME, 6, 101, 7, null), "Clearing a cell that was never planned changes nothing");
        assertFalse(fields.paint(HOME, 6, 101, 7, " "));
        assertTrue(fields.paint(HOME, 5, 101, 7, null)); assertEquals(0, fields.plannedCount());
        assertNull(fields.cell(HOME, 5, 7), "A cleared cell with ordinary soil is forgotten");
        assertTrue(production.usesFarmFields(), "A farm that once had a plan does not slip back to automatic production");
        assertFalse(fields.paint(3, 1, 101, 1, WHEAT), "A field that no Scarecrow claims cannot be painted");
        production = gson.fromJson(gson.toJson(production), PlotProductionState.class);
        assertTrue(production.usesFarmFields()); assertNotNull(production.farm().field(HOME));
    }

    @Test void aFieldPlansNoMoreCellsThanItsLimitAndClearingKeepsWhatTheSoilRemembers() {
        var fields = farm().farm();
        for (int i = 0; i < MAX_CELLS; i++) assertTrue(fields.paint(HOME, i, 101, 0, WHEAT));
        assertFalse(fields.paint(HOME, MAX_CELLS, 101, 0, WHEAT), "One field plans at most " + MAX_CELLS + " cells");
        assertTrue(fields.paint(HOME, 0, 101, 0, LETTUCE), "A planned cell can still change its crop at the limit");
        // Wear one cell, then clear the whole field and paint it again: the soil is as it was.
        for (int i = 0; i < rules.soil().wearEvery(); i++) assertNotNull(harvest(fields, 1, WHEAT, 1, false, false, false));
        assertEquals(WORN, fields.soil(HOME, 1, 0));
        assertTrue(fields.clear(HOME)); assertFalse(fields.clear(HOME)); assertEquals(0, fields.plannedCount());
        assertEquals(1, fields.field(HOME).cells().size(), "Only the cell whose soil has a story is remembered");
        assertEquals(WORN, fields.soil(HOME, 1, 0));
        assertTrue(fields.paint(HOME, 1, 101, 0, WHEAT)); assertEquals(WORN, fields.soil(HOME, 1, 0), "Clearing and painting again mends nothing");
    }

    @Test void scarecrowsClaimFieldsUpToTheAllowanceAndABrokenScarecrowGivesItsFieldUp() {
        var production = farm(); var fields = production.farm();
        assertEquals(1, FarmUpgrades.scarecrows(production), "A new farm tends one Scarecrow field beside its own");
        assertEquals(1, fields.freeSlot(1)); assertEquals(-1, fields.fieldAt(SCARECROW));
        assertEquals("anchor", fields.claim(1, 1, null)); assertEquals("full", fields.claim(0, 1, SCARECROW));
        assertEquals("full", fields.claim(2, 1, SCARECROW), "A Scarecrow beyond the allowance claims nothing");
        assertEquals("full", fields.claim(MAX_FIELDS, MAX_FIELDS, SCARECROW));
        assertNull(fields.claim(1, 1, SCARECROW)); assertEquals(1, fields.fieldAt(SCARECROW)); assertEquals(-1, fields.freeSlot(1));
        assertEquals("taken", fields.claim(1, 3, new Point(60, 100, 60))); assertEquals("taken", fields.claim(2, 3, SCARECROW), "One Scarecrow, one field");
        assertEquals(2, fields.freeSlot(3)); assertNull(fields.claim(2, 3, new Point(60, 100, 60)));
        assertTrue(fields.paint(1, 41, 100, 40, CORN)); assertTrue(fields.paint(2, 61, 100, 60, WHEAT));
        assertEquals(1, fields.owner(41, 40)); assertEquals(2, fields.owner(61, 60));
        assertEquals(5, FarmFields.scarecrowBounds(SCARECROW, FarmUpgrades.reach(production)).width(), "Reach two makes a field five cells across");
        assertTrue(FarmFields.scarecrowBounds(SCARECROW, 2).contains(38, 42)); assertFalse(FarmFields.scarecrowBounds(SCARECROW, 2).contains(37, 40));
        production = gson.fromJson(gson.toJson(production), PlotProductionState.class); fields = production.farm();
        assertEquals(1, fields.fieldAt(SCARECROW)); assertEquals(CORN, fields.cell(1, 41, 40).crop());
        assertFalse(production.releaseScarecrowAt(new Point(1, 1, 1)));
        assertTrue(production.releaseScarecrowAt(SCARECROW)); assertEquals(-1, fields.fieldAt(SCARECROW)); assertEquals(-1, fields.owner(41, 40));
        assertEquals(1, fields.plannedCount(), "The other field keeps its plan"); assertEquals(1, fields.freeSlot(3));
        assertFalse(fields.release(HOME), "The farm's own field is never given up"); assertNotNull(fields.field(HOME));
    }

    @Test void rotatingMovesEveryCropOnToTheNextOne() {
        var fields = farm().farm();
        assertTrue(fields.paint(HOME, 0, 101, 0, WHEAT)); assertFalse(fields.rotate(HOME), "One crop cannot rotate");
        assertTrue(fields.paint(HOME, 1, 101, 0, LETTUCE)); assertTrue(fields.paint(HOME, 2, 101, 0, CORN)); assertTrue(fields.paint(HOME, 3, 101, 0, WHEAT));
        assertTrue(fields.rotate(HOME));
        assertEquals(List.of(LETTUCE, CORN, WHEAT, LETTUCE), fields.planned(HOME).stream().map(Cell::crop).toList());
        assertTrue(fields.rotate(HOME)); assertTrue(fields.rotate(HOME));
        assertEquals(List.of(WHEAT, LETTUCE, CORN, WHEAT), fields.planned(HOME).stream().map(Cell::crop).toList(), "Three turns bring three crops round");
    }

    // The Farmer's choices --------------------------------------------------------------------------------------

    private static FarmFieldWork.Needs needs(boolean onDuty, int basket, Set<String> seeds, Set<Cell> skip) {
        return new FarmFieldWork.Needs(onDuty, basket, crop -> true, seeds::contains, crop -> true, false, skip::contains, cell -> cell.x());
    }

    @Test void theFarmerGathersRipeCropsThenSowsThenWaters() {
        var production = farm(); var fields = production.farm();
        for (int x = 0; x < 6; x++) assertTrue(fields.paint(HOME, x, 101, 0, x == 5 ? CORN : WHEAT));
        var views = new HashMap<Integer, View>();
        views.put(0, new View(true, true, null, false, false));         // empty, to sow
        views.put(1, new View(true, false, WHEAT, false, true));        // growing and thirsty
        views.put(2, new View(true, false, WHEAT, true, true));         // ripe
        views.put(3, new View(true, false, WHEAT, true, false));        // ripe, further away
        views.put(4, View.FAR);                                         // in an unloaded chunk
        views.put(5, new View(true, true, null, false, false));         // empty, but no Corn seed
        FarmFieldWork.Look look = (slot, cell) -> views.get(cell.x());
        var seeds = Set.of(WHEAT);
        var job = FarmFieldWork.next(fields, look, needs(true, 24, seeds, Set.of()));
        assertEquals(Action.HARVEST, job.action()); assertEquals(2, job.cell().x(), "The nearest ripe crop first"); assertEquals(WHEAT, job.crop());
        job = FarmFieldWork.next(fields, look, needs(true, 24, seeds, Set.of(fields.cell(HOME, 2, 0))));
        assertEquals(3, job.cell().x(), "A cell she could not reach is passed over for now");
        views.put(2, new View(true, false, WHEAT, false, false)); views.put(3, new View(true, false, WHEAT, false, false));
        job = FarmFieldWork.next(fields, look, needs(true, 24, seeds, Set.of()));
        assertEquals(Action.PLANT, job.action()); assertEquals(0, job.cell().x()); assertEquals(WHEAT, job.crop());
        assertEquals(Action.PLANT, FarmFieldWork.next(fields, look, needs(true, 24, Set.of(WHEAT, CORN), Set.of(fields.cell(HOME, 0, 0)))).action());
        views.put(0, new View(true, false, WHEAT, false, false));
        job = FarmFieldWork.next(fields, look, needs(true, 24, seeds, Set.of()));
        assertEquals(Action.WATER, job.action()); assertEquals(1, job.cell().x(), "With no seed for the Corn she waters instead of waiting");
        views.put(1, new View(true, false, WHEAT, false, false));
        assertNull(FarmFieldWork.next(fields, look, needs(true, 24, seeds, Set.of())), "Nothing to do: she goes back to her own day");
        // The cell planned for a new crop keeps its old crop until that is ripe, then it is gathered like any other.
        assertTrue(fields.paint(HOME, 3, 101, 0, LETTUCE));
        assertNull(FarmFieldWork.next(fields, look, needs(true, 24, seeds, Set.of())), "An unripe crop from before the plan changed is left to ripen");
        views.put(3, new View(true, false, WHEAT, true, false));
        job = FarmFieldWork.next(fields, look, needs(true, 24, seeds, Set.of()));
        assertEquals(Action.HARVEST, job.action()); assertEquals(WHEAT, job.crop(), "She gathers what stands there, not what is planned");
        views.put(3, new View(true, false, WHEAT, false, false));
        // A crop she is not wanted to gather, because the stores and the compost heap are full, stays standing.
        views.put(2, new View(true, false, WHEAT, true, false));
        assertNull(FarmFieldWork.next(fields, look, new FarmFieldWork.Needs(true, 24, crop -> true, seeds::contains, crop -> false, false, cell -> false, cell -> cell.x())));
        views.put(2, new View(true, false, WHEAT, false, false));
        // A crop she may not grow is never sown.
        views.put(0, new View(true, true, null, false, false));
        assertNull(FarmFieldWork.next(fields, look, new FarmFieldWork.Needs(true, 24, crop -> false, seeds::contains, crop -> true, false, cell -> false, cell -> cell.x())));
    }

    @Test void aFullBasketGoesHomeAndOffDutySheOnlyCarriesWhatSheHolds() {
        var production = farm(); var fields = production.farm();
        for (int x = 0; x < 3; x++) assertTrue(fields.paint(HOME, x, 101, 0, WHEAT));
        FarmFieldWork.Look ripe = (slot, cell) -> new View(true, false, WHEAT, true, false);
        assertNull(FarmFieldWork.next(fields, ripe, needs(false, 4, Set.of(), Set.of())), "Off duty with empty hands she does nothing");
        assertNotNull(harvest(fields, 0, WHEAT, 1, false, false, false));
        assertEquals(3, fields.carried(), "One wheat and two Life Essence");
        assertEquals(Action.HARVEST, FarmFieldWork.next(fields, ripe, needs(true, 4, Set.of(), Set.of())).action(), "She fills the basket before walking home");
        assertEquals(Action.DELIVER, FarmFieldWork.next(fields, ripe, needs(false, 4, Set.of(), Set.of())).action(), "A harvest in her basket goes home after her shift");
        assertNotNull(harvest(fields, 1, WHEAT, 1, false, false, false));
        assertEquals(Action.DELIVER, FarmFieldWork.next(fields, ripe, needs(true, 4, Set.of(), Set.of())).action(), "A full basket goes home first");
        FarmFieldWork.Look growing = (slot, cell) -> new View(true, false, WHEAT, false, false);
        assertEquals(Action.DELIVER, FarmFieldWork.next(fields, growing, needs(true, 99, Set.of(), Set.of())).action(), "With nothing else to do she empties a part full basket");
    }

    // Soil ------------------------------------------------------------------------------------------------------

    @Test void theSameCropAgainAndAgainWearsTheSoilAndAChangeOfCropMendsIt() {
        var fields = farm().farm(); int wear = rules.soil().wearEvery();
        assertEquals(6, wear, "Six harvests of one crop in a row wear a cell one step: about two days of one crop");
        assertTrue(fields.paint(HOME, 0, 101, 0, WHEAT));
        assertFalse(fields.planted(HOME, 0, 0, WHEAT), "A first planting has nothing to mend");
        assertEquals(new Harvest(2, 2, false, false, GOOD, GOOD), harvest(fields, 0, WHEAT, 2, false, false, false), "One harvest of a crop does not wear the soil");
        assertFalse(fields.planted(HOME, 0, 0, WHEAT), "The same crop again mends nothing");
        for (int i = 2; i < wear; i++) assertEquals(GOOD, harvest(fields, 0, WHEAT, 1, false, false, false).soilAfter());
        var last = harvest(fields, 0, WHEAT, 2, false, false, false);
        assertEquals(GOOD, last.soilBefore()); assertEquals(WORN, last.soilAfter(), "The same crop " + wear + " times running wears the soil one step");
        for (int i = 0; i < 2 * wear; i++) assertEquals(WORN, harvest(fields, 0, WHEAT, 1, false, false, false).soilAfter(), "Worn is as far as soil wears");
        // A different crop from the one last harvested mends one step, once for each harvest.
        assertTrue(fields.paint(HOME, 0, 101, 0, LETTUCE));
        assertTrue(fields.planted(HOME, 0, 0, LETTUCE)); assertEquals(GOOD, fields.soil(HOME, 0, 0));
        assertFalse(fields.planted(HOME, 0, 0, LETTUCE), "Sowing twice after one harvest mends once");
        assertEquals(GOOD, harvest(fields, 0, LETTUCE, 1, false, false, false).soilAfter(), "The first harvest of the new crop wears nothing");
        assertTrue(fields.paint(HOME, 0, 101, 0, WHEAT)); assertTrue(fields.planted(HOME, 0, 0, WHEAT));
        assertEquals(RICH, fields.soil(HOME, 0, 0), "Two changes of crop take Worn soil to Rich");
        assertNotNull(harvest(fields, 0, WHEAT, 1, false, false, false));
        assertTrue(fields.paint(HOME, 0, 101, 0, LETTUCE)); assertFalse(fields.planted(HOME, 0, 0, LETTUCE), "Rich is as good as soil gets");
        assertEquals(RICH, fields.soil(HOME, 0, 0));
        // Rich soil wears like any other: the same crop again and again takes it back to Good.
        for (int i = 1; i < wear; i++) assertEquals(RICH, harvest(fields, 0, LETTUCE, 1, false, false, false).soilAfter());
        assertEquals(GOOD, harvest(fields, 0, LETTUCE, 1, false, false, false).soilAfter());
        var saved = gson.fromJson(gson.toJson(fields), FarmFieldsState.class);
        assertEquals(fields.cell(HOME, 0, 0), saved.cell(HOME, 0, 0), "Soil and its memory survive a save");
    }

    @Test void richSoilDoublesTheHarvestAndOnlyRichSoilGivesAGoldenHarvest() {
        var fields = farm().farm();
        assertTrue(fields.paint(HOME, 0, 101, 0, WHEAT)); assertTrue(fields.paint(HOME, 1, 101, 0, WHEAT));
        assertEquals(2, rules.soil().richYield()); assertEquals(1, rules.almanac().extraYield());
        assertTrue(rules.soil().goldenChance() > 0 && rules.soil().goldenChance() <= 0.05, "A Golden Harvest is rare");
        var plain = harvest(fields, 0, WHEAT, 2, false, true, false);
        assertEquals(2, plain.crops()); assertFalse(plain.golden(), "Good soil never gives a Golden Harvest, whatever the roll");
        assertEquals(3, harvest(fields, 1, WHEAT, 2, true, false, false).crops(), "A favoured crop gives one extra");
        // Compost twice makes neither cell Rich on its own account here, so set the soil by rotation instead.
        var rich = farm().farm();
        assertTrue(rich.paint(HOME, 0, 101, 0, WHEAT)); assertNotNull(harvest(rich, 0, WHEAT, 1, false, false, false));
        assertTrue(rich.paint(HOME, 0, 101, 0, LETTUCE)); assertTrue(rich.planted(HOME, 0, 0, LETTUCE)); assertEquals(RICH, rich.soil(HOME, 0, 0));
        int before = rich.carried();
        var doubled = rich.harvest(HOME, 0, 0, LETTUCE, NativeFarmCrops.seedFor(LETTUCE), 2, 3, true, 0.5, rules.soil().goldenChance() / 2, 0.999, rules);
        assertEquals(5, doubled.crops(), "Rich soil doubles the two the plant gave, and the Almanac adds one");
        assertEquals(3, doubled.essence(), "Life Essence is never doubled"); assertTrue(doubled.golden());
        assertEquals(before + 5 + 3 + 1, rich.carried()); assertEquals(1, rich.basket().get(GOLDEN));
        assertFalse(rich.harvest(HOME, 0, 0, LETTUCE, null, 1, 1, false, 0.5, rules.soil().goldenChance(), 0.0, rules).golden(),
            "A roll that only equals the chance gives no Golden Harvest");
        assertNull(rich.harvest(HOME, 9, 9, LETTUCE, null, 1, 1, false, 0.5, 0, 0, rules), "A cell outside the plan gives nothing");
        assertNull(rich.harvest(HOME, 0, 0, LETTUCE, null, -1, 1, false, 0.5, 0, 0, rules));
    }

    @Test void aHarvestGivesItsSeedBackByTheSeedSavingChance() {
        var production = farm(); var fields = production.farm();
        assertEquals(0.5, FarmUpgrades.seedChance(production), 1e-9);
        assertTrue(fields.paint(HOME, 0, 101, 0, WHEAT));
        int seeds = fields.seeds(WHEAT_SEED);
        assertFalse(fields.harvest(HOME, 0, 0, WHEAT, WHEAT_SEED, 1, 2, false, 0.5, 0.999, 0.5, rules).seedReturned(), "A roll at the chance returns nothing");
        assertTrue(fields.harvest(HOME, 0, 0, WHEAT, WHEAT_SEED, 1, 2, false, 0.5, 0.999, 0.49, rules).seedReturned());
        assertEquals(seeds + 1, fields.seeds(WHEAT_SEED), "The seed goes straight back into the seed store");
        fields.setLevel(Track.SEEDS.name(), FarmUpgrades.maxLevel(Track.SEEDS));
        assertEquals(1.0, FarmUpgrades.seedChance(production), 1e-9, "With seed saving every harvest gives its seed back");
        assertTrue(fields.harvest(HOME, 0, 0, WHEAT, WHEAT_SEED, 1, 2, false, FarmUpgrades.seedChance(production), 0.999, 0.999, rules).seedReturned());
    }

    // Compost and stores ----------------------------------------------------------------------------------------

    @Test void cropsTheStoresHaveNoRoomForBecomeCompostAndCompostMendsWornSoilFirst() {
        var production = farm(); var fields = production.farm(); var entry = catalog(production);
        int each = FarmUpgrades.cropsPerCompost(production), wear = rules.soil().wearEvery();
        assertEquals(3, each, "Three spare crops make one compost"); assertEquals(25, FarmUpgrades.capacity(production, entry, WHEAT));
        assertEquals(rules.golden().keep(), FarmUpgrades.capacity(production, entry, GOLDEN));
        assertFalse(FarmUpgrades.compostEnriches(production), "At first compost only mends Worn soil");
        for (int x = 0; x < 3; x++) assertTrue(fields.paint(HOME, x, 101, 0, WHEAT));
        production.addAmount(WHEAT, 20, 25); production.addAmount(ESSENCE, 99, 100);
        // Ten harvests of two wheat: five fit the stores, fifteen are spare.
        for (int i = 0; i < 10; i++) assertNotNull(harvest(fields, 0, WHEAT, 2, false, false, false));
        assertEquals(WORN, fields.soil(HOME, 0, 0)); assertEquals(20, fields.basket().get(WHEAT));
        var delivery = FarmFieldJobs.deliver(town(), production, entry);
        assertEquals(5, delivery.stored().get(WHEAT)); assertEquals(1, delivery.stored().get(ESSENCE), "Life Essence fills its last place");
        assertEquals(15, delivery.composted(), "Only crops are composted. Spare Life Essence is simply left");
        assertEquals(5, delivery.compostMade()); assertEquals(5, fields.compost()); assertEquals(0, fields.compostProgress());
        assertEquals(25, production.getAmount(WHEAT)); assertEquals(100, production.getAmount(ESSENCE)); assertFalse(fields.carrying());
        assertTrue(FarmFieldJobs.deliver(town(), production, entry).stored().isEmpty(), "A basket is emptied once");
        // Worn soil is mended. Good soil takes no compost until compost enriches.
        var target = fields.compostTarget(false); assertEquals(0, target.cell().x()); assertEquals(WORN, target.cell().soil());
        assertFalse(fields.spread(HOME, 9, 9, false)); assertTrue(fields.spread(HOME, 0, 0, false));
        assertEquals(GOOD, fields.soil(HOME, 0, 0)); assertEquals(4, fields.compost());
        assertNull(fields.compostTarget(false), "With no Worn soil the compost waits");
        assertFalse(fields.spread(HOME, 0, 0, false), "Good soil is made Rich by a change of crop, not by plain compost");
        FarmFieldWork.Look growing = (slot, cell) -> new View(true, false, WHEAT, false, false);
        assertNull(FarmFieldWork.next(fields, growing, needs(true, 24, Set.of(), Set.of())));
        // With the last level of richer compost it lifts Good soil to Rich as well.
        fields.setLevel(Track.COMPOST.name(), 2); assertTrue(FarmUpgrades.compostEnriches(production));
        assertEquals(0, fields.compostTarget(true).cell().x());
        assertTrue(fields.spread(HOME, 0, 0, true)); assertEquals(RICH, fields.soil(HOME, 0, 0));
        assertFalse(fields.spread(HOME, 0, 0, true), "Rich soil takes no more");
        assertTrue(fields.spread(HOME, 1, 0, true)); assertEquals(2, fields.compostTarget(true).cell().x()); assertEquals(2, fields.compost());
        // The Farmer herself spreads compost before anything else, and on the worn cell before a nearer Good one.
        assertTrue(fields.paint(HOME, 3, 101, 0, WHEAT));
        for (int i = 0; i < wear; i++) assertNotNull(harvest(fields, 3, WHEAT, 6, false, false, false));
        assertEquals(WORN, fields.soil(HOME, 3, 0));
        FarmFieldJobs.deliver(town(), production, entry); assertTrue(fields.compost() >= 3);
        FarmFieldWork.Look ripe = (slot, cell) -> new View(true, false, WHEAT, true, false);
        var enriching = new FarmFieldWork.Needs(true, 24, crop -> true, crop -> true, crop -> true, true, cell -> false, cell -> cell.x());
        var job = FarmFieldWork.next(fields, ripe, enriching);
        assertEquals(Action.COMPOST, job.action(), "Compost is spread before the harvest, so tired soil is mended at once");
        assertEquals(3, job.cell().x(), "Worn soil before Good soil, however near the Good soil is");
        assertTrue(fields.spread(HOME, 3, 0, true));
        assertEquals(2, FarmFieldWork.next(fields, ripe, enriching).cell().x(), "Among Good cells the nearest");
        assertTrue(fields.spread(HOME, 2, 0, true)); assertTrue(fields.spread(HOME, 3, 0, true));
        assertNull(fields.compostTarget(true), "All the planned soil is Rich, so the rest of the compost waits");
        assertEquals(Action.HARVEST, FarmFieldWork.next(fields, ripe, enriching).action());
        var none = farm().farm(); assertTrue(none.paint(HOME, 0, 101, 0, WHEAT));
        assertFalse(none.spread(HOME, 0, 0, true), "No compost, no spreading");
    }

    @Test void theCompostHeapIsNeverOverfilledAndRicherCompostNeedsFewerCrops() {
        var production = farm(); var fields = production.farm(); var entry = catalog(production);
        assertTrue(fields.paint(HOME, 0, 101, 0, WHEAT));
        production.addAmount(WHEAT, 25, 25);
        for (int i = 0; i < 2000; i++) assertNotNull(fields.harvest(HOME, 0, 0, WHEAT, null, 1, 0, false, 0, 0.999, 0.999, rules));
        assertEquals(2000, FarmFieldJobs.deliver(town(), production, entry).composted());
        assertEquals(rules.compost().keep(), fields.compost(), "The heap holds " + rules.compost().keep() + " and no more");
        assertTrue(fields.compostProgress() < FarmUpgrades.cropsPerCompost(production));
        assertTrue(FarmFieldJobs.harvestWanted(production, entry, LETTUCE), "A crop the stores have room for is always worth gathering");
        assertFalse(FarmFieldJobs.harvestWanted(production, entry, WHEAT), "Full stores and a full heap: the wheat is left standing");
        assertTrue(fields.spread(HOME, 0, 0, false));
        assertTrue(FarmFieldJobs.harvestWanted(production, entry, WHEAT), "Room on the heap makes the harvest worth it again");
        fields.setLevel(Track.COMPOST.name(), 1);
        assertEquals(2, FarmUpgrades.cropsPerCompost(production), "Richer compost needs fewer crops"); assertFalse(FarmUpgrades.compostEnriches(production));
        fields.setLevel(Track.COMPOST.name(), 2);
        assertEquals(2, FarmUpgrades.cropsPerCompost(production)); assertTrue(FarmUpgrades.compostEnriches(production), "and at its last level makes Good soil Rich");
    }

    @Test void seedsComeFromTheSeedStoreFirstAndThenFromSpareLifeEssence() {
        var production = farm(); var fields = production.farm();
        assertEquals(16, fields.seeds(WHEAT_SEED), "A farm is given a first handful of seeds"); assertEquals(16, fields.seeds("Plant_Seeds_Lettuce"));
        assertEquals(0, fields.seeds("Plant_Seeds_Corn"));
        assertTrue(FarmFieldWork.seedReady(fields, WHEAT_SEED, 2, 0)); assertFalse(FarmFieldWork.seedReady(fields, "Plant_Seeds_Corn", 3, 2));
        assertTrue(FarmFieldWork.seedReady(fields, "Plant_Seeds_Corn", 3, 3)); assertFalse(FarmFieldWork.seedReady(fields, null, 1, 99));
        assertEquals("store", FarmFieldWork.takeSeed(production, WHEAT_SEED, ESSENCE, 2, 0)); assertEquals(15, fields.seeds(WHEAT_SEED));
        production.addAmount(ESSENCE, 7, 100);
        assertNull(FarmFieldWork.takeSeed(production, "Plant_Seeds_Corn", ESSENCE, 3, 5), "Life Essence promised to the warehouse is not hers to spend");
        assertEquals(7, production.getAmount(ESSENCE));
        assertEquals("essence", FarmFieldWork.takeSeed(production, "Plant_Seeds_Corn", ESSENCE, 3, 4)); assertEquals(4, production.getAmount(ESSENCE));
        assertEquals("essence", FarmFieldWork.takeSeed(production, "Plant_Seeds_Corn", ESSENCE, 3, 0));
        assertNull(FarmFieldWork.takeSeed(production, "Plant_Seeds_Corn", ESSENCE, 3, 0)); assertEquals(1, production.getAmount(ESSENCE));
        assertEquals(64, rules.seeds().keep());
        assertEquals(49, fields.addSeeds(WHEAT_SEED, 500, rules.seeds().keep()), "The seed store keeps " + rules.seeds().keep() + " of each seed");
        assertEquals(0, fields.addSeeds(WHEAT_SEED, 1, rules.seeds().keep())); assertEquals(0, fields.addSeeds(null, 5, 64)); assertEquals(0, fields.addSeeds(" ", 5, 64));
    }

    @Test void missingSeedsAreCountedCropByCropForTheStatusLine() {
        var production = farm(); var fields = production.farm();
        for (int x = 0; x < 20; x++) assertTrue(fields.paint(HOME, x, 101, 0, x < 9 ? CORN : WHEAT));
        // Sixteen wheat seeds in the store, eleven empty wheat cells: nothing is missing. Corn has no seeds at all.
        FarmFieldWork.Look empty = (slot, cell) -> new View(true, true, null, false, false);
        var missing = FarmFieldWork.missingSeeds(fields, empty, crop -> true, NativeFarmCrops::seedFor, crop -> crop.equals(CORN) ? 3 : 2, 9);
        assertEquals(Map.of(CORN, 6), missing, "Nine Life Essence make three Corn seeds, so six are still needed");
        assertTrue(FarmFieldWork.missingSeeds(fields, empty, crop -> true, NativeFarmCrops::seedFor, crop -> 3, 27).isEmpty());
        assertEquals(Map.of(CORN, 9), FarmFieldWork.missingSeeds(fields, empty, crop -> true, NativeFarmCrops::seedFor, crop -> 3, 0));
        // Cells that already hold their crop, or cannot be sown, need no seed.
        FarmFieldWork.Look grown = (slot, cell) -> cell.x() < 4 ? new View(true, false, CORN, false, false) : cell.x() < 6 ? View.FAR : new View(true, true, null, false, false);
        assertEquals(Map.of(CORN, 3), FarmFieldWork.missingSeeds(fields, grown, crop -> true, NativeFarmCrops::seedFor, crop -> 3, 0));
        assertTrue(FarmFieldWork.missingSeeds(fields, empty, crop -> false, NativeFarmCrops::seedFor, crop -> 3, 0).isEmpty(), "A crop she cannot grow is not asked for");
    }

    // The Almanac -----------------------------------------------------------------------------------------------

    private static LocalDateTime clock(String instant) { return LocalDateTime.ofInstant(Instant.parse(instant), ZoneOffset.UTC); }

    @Test void theAlmanacCountsWeeksFromTheFirstDayOfYearOneWhereTheGameClockIsFarBelowZero() {
        assertTrue(Instant.parse("0001-01-01T05:30:00Z").getEpochSecond() < 0, "The game's clock starts about sixty two billion seconds below zero");
        assertEquals(0, FarmAlmanac.week(clock("0001-01-01T00:00:00Z"))); assertEquals(7, FarmAlmanac.daysLeft(clock("0001-01-01T00:00:00Z")));
        assertEquals(0, FarmAlmanac.week(clock("0001-01-07T23:59:59Z")), "The last second of the seventh day is still the first week");
        assertEquals(1, FarmAlmanac.daysLeft(clock("0001-01-07T23:59:59Z")));
        assertEquals(1, FarmAlmanac.week(clock("0001-01-08T00:00:00Z")), "Midnight on the eighth day starts the second week");
        assertEquals(7, FarmAlmanac.daysLeft(clock("0001-01-08T00:00:00Z")));
        assertEquals(3, FarmAlmanac.week(clock("0001-01-28T12:00:00Z"))); assertEquals(4, FarmAlmanac.week(clock("0001-01-29T00:00:00Z")));
        // Four weeks to a season and four seasons to a year, like the town calendar.
        var first = FarmAlmanac.describe(0);
        assertEquals("Spring", first.season().displayName()); assertEquals(1, first.weekOfSeason()); assertEquals(1, first.year());
        assertEquals(4, FarmAlmanac.describe(3).weekOfSeason()); assertEquals("Summer", FarmAlmanac.describe(4).season().displayName());
        assertEquals("Winter", FarmAlmanac.describe(15).season().displayName()); assertEquals(2, FarmAlmanac.describe(16).year());
        assertEquals("Spring", FarmAlmanac.describe(16).season().displayName());
        var summer = com.hexvane.aetherhaven.hud.AetherhavenCalendar.toLocalDateTime(com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season.SUMMER, 8, 3);
        var week = FarmAlmanac.describe(FarmAlmanac.week(summer));
        assertEquals("Summer", week.season().displayName()); assertEquals(2, week.weekOfSeason()); assertEquals(3, week.year(), "The Almanac and the town calendar agree");
        // A clock set to before year one still gives a week and never an error.
        assertEquals(-1, FarmAlmanac.week(clock("0000-12-31T23:00:00Z"))); assertEquals(1, FarmAlmanac.daysLeft(clock("0000-12-31T23:00:00Z")));
        assertEquals("Winter", FarmAlmanac.describe(-1).season().displayName()); assertEquals(4, FarmAlmanac.describe(-1).weekOfSeason());
        assertEquals(1, FarmAlmanac.favoured(-1, List.of(WHEAT, LETTUCE), 2).size());
    }

    @Test void favouredCropsAreTheSameForEveryoneAndEveryCropHasItsTurn() {
        var crops = List.of("a", "b", "c", "d", "e", "f", "g");
        assertEquals(List.of("a", "b"), FarmAlmanac.favoured(0, crops, 2)); assertEquals(List.of("c", "d"), FarmAlmanac.favoured(1, crops, 2));
        assertEquals(List.of("g", "a"), FarmAlmanac.favoured(3, crops, 2), "The turn wraps round");
        assertEquals(FarmAlmanac.favoured(12345, crops, 3), FarmAlmanac.favoured(12345, crops, 3), "The same week always gives the same crops");
        var seen = new HashSet<String>();
        for (long week = 100; week < 104; week++) {
            var chosen = FarmAlmanac.favoured(week, crops, 2);
            assertEquals(2, chosen.size()); assertEquals(2, new HashSet<>(chosen).size(), "No crop is named twice in a week");
            for (String crop : chosen) assertTrue(seen.add(crop) || seen.size() == crops.size(), "No crop has a second turn before the others had theirs");
        }
        assertEquals(crops.size(), seen.size(), "Four weeks of two reach all seven crops");
        // With few crops at most half are favoured, so favour always means something.
        assertEquals(List.of(WHEAT), FarmAlmanac.favoured(0, List.of(WHEAT, LETTUCE), 2)); assertEquals(List.of(LETTUCE), FarmAlmanac.favoured(1, List.of(WHEAT, LETTUCE), 3));
        assertEquals(List.of(WHEAT), FarmAlmanac.favoured(7, List.of(WHEAT), 3)); assertTrue(FarmAlmanac.favoured(7, List.of(), 3).isEmpty());
        assertEquals(2, FarmAlmanac.favouredCount(5, 2)); assertEquals(2, FarmAlmanac.favouredCount(5, 3)); assertEquals(3, FarmAlmanac.favouredCount(14, 3));
        var production = farm();
        assertEquals(2, FarmUpgrades.favouredCrops(production)); assertEquals(2, FarmUpgrades.weeksShown(production), "A new farm reads this week and the next");
        production.farm().setLevel(Track.ALMANAC.name(), 1); assertEquals(3, FarmUpgrades.favouredCrops(production)); assertEquals(2, FarmUpgrades.weeksShown(production));
        production.farm().setLevel(Track.ALMANAC.name(), 2); assertEquals(3, FarmUpgrades.favouredCrops(production));
        assertEquals(FarmAlmanac.WEEKS_PER_SEASON, FarmUpgrades.weeksShown(production), "Full insight reads a whole season");
    }

    // Upgrades --------------------------------------------------------------------------------------------------

    @Test void farmUpgradesArePaidInFullOrNotAtAllAndBoughtInOrder() {
        var production = farm();
        var purse = new HashMap<String, Long>(Map.of(ESSENCE, 14L, WHEAT, 10L));
        var wallet = new GardenUpgrades.Purse() {
            @Override public long available(String item) { return purse.getOrDefault(item, 0L); }
            @Override public boolean take(Map<String, Integer> cost) { cost.forEach((item, count) -> purse.merge(item, -(long) count, Long::sum)); return true; }
        };
        assertEquals(Map.of(ESSENCE, 15, WHEAT, 10), FarmUpgrades.cost(Track.SCARECROWS, 1, 1.0));
        assertEquals("order", FarmUpgrades.purchase(production, Track.SCARECROWS, 2, 1.0, wallet));
        assertEquals("short", FarmUpgrades.purchase(production, Track.SCARECROWS, 1, 1.0, wallet), "One Life Essence short");
        assertEquals(Map.of(ESSENCE, 14L, WHEAT, 10L), purse, "Nothing is taken for an upgrade that was not bought");
        purse.put(ESSENCE, 15L);
        assertNull(FarmUpgrades.purchase(production, Track.SCARECROWS, 1, 1.0, wallet));
        assertEquals(Map.of(ESSENCE, 0L, WHEAT, 0L), purse); assertEquals(2, FarmUpgrades.scarecrows(production));
        assertEquals("order", FarmUpgrades.purchase(production, Track.SCARECROWS, 1, 1.0, wallet), "A level is bought once");
        production.farm().setLevel(Track.SCARECROWS.name(), 2);
        assertEquals("maxed", FarmUpgrades.purchase(production, Track.SCARECROWS, 3, 1.0, wallet)); assertEquals(3, FarmUpgrades.scarecrows(production));
        assertTrue(FarmUpgrades.cost(Track.SCARECROWS, 3, 1.0).isEmpty()); assertTrue(FarmUpgrades.cost(Track.SCARECROWS, 0, 1.0).isEmpty());
        assertTrue(FarmUpgrades.cost(Track.REACH, 1, 2.0).get(ESSENCE) > FarmUpgrades.cost(Track.REACH, 1, 1.0).get(ESSENCE), "Costs follow the town's upgrade cost setting");
        production = gson.fromJson(gson.toJson(production), PlotProductionState.class);
        assertEquals(3, FarmUpgrades.scarecrows(production), "Upgrades survive a save");
    }

    @Test void everyUpgradeLevelGivesMoreAndItsCostIsInFivesOfLifeEssenceAndCrops() {
        var production = farm();
        assertEquals(1, FarmUpgrades.scarecrows(production)); assertEquals(2, FarmUpgrades.reach(production)); assertEquals(0, FarmUpgrades.waterRadius(production));
        for (var track : Track.values()) {
            assertEquals(2, FarmUpgrades.maxLevel(track), track + " has two levels, so six tracks fill the tree without scrolling");
            for (int level = 1; level <= FarmUpgrades.maxLevel(track); level++) {
                var cost = FarmUpgrades.cost(track, level, 1.0);
                assertTrue(cost.containsKey(ESSENCE), track + " " + level + " costs Life Essence");
                assertTrue(cost.get(ESSENCE) >= 10 && cost.get(ESSENCE) <= 30, "In line with the Spirit Garden's 10 to 30");
                for (var part : cost.entrySet()) {
                    assertEquals(0, part.getValue() % 5, part.getKey() + " for " + track + " " + level + " is a multiple of five");
                    assertTrue(part.getKey().equals(ESSENCE) || NativeFarmCrops.byOutput(part.getKey()) != null, part.getKey() + " is something the farm grows");
                }
            }
        }
        production.farm().setLevel(Track.SCARECROWS.name(), 2); production.farm().setLevel(Track.REACH.name(), 2); production.farm().setLevel(Track.WATERING.name(), 2);
        assertEquals(MAX_FIELDS - 1, FarmUpgrades.scarecrows(production)); assertEquals(4, FarmUpgrades.reach(production)); assertEquals(2, FarmUpgrades.waterRadius(production));
        assertEquals(9, FarmFields.scarecrowBounds(SCARECROW, FarmUpgrades.reach(production)).width());
        assertTrue(9 * 9 - 1 <= MAX_CELLS, "The widest Scarecrow field fits one plan");
        production.farm().setLevel(Track.REACH.name(), 99); assertEquals(4, FarmUpgrades.reach(production), "A level beyond the last is the last");
    }

    @Test void theUpgradeTreeShowsSixTracksOfTwoWithoutScrolling() {
        var nodes = com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.farm();
        assertEquals(12, nodes.size());
        var ids = new HashSet<String>();
        for (var node : nodes) {
            assertTrue(ids.add(node.id()));
            assertEquals(com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Kind.FARM, node.kind());
            var track = Track.valueOf(node.branch());
            assertEquals(track.name() + ":" + node.tier(), node.id());
            assertEquals(32 + (track.ordinal() % 3) * 201, node.x(), "One column for each track"); assertTrue(node.x() + 174 <= 640, "The node fits the tree");
            assertEquals((track.ordinal() < 3 ? 38 : 310) + (node.tier() - 1) * 124, node.y());
            assertTrue(node.y() + 112 <= 555, "Every node shows without scrolling");
            assertEquals(node.tier() == 1 ? List.of() : List.of(track.name() + ":" + (node.tier() - 1)), node.parents());
            assertNotNull(node.item()); assertNull(node.icon()); assertEquals(1, node.level(), "The farm asks for no town level of its own");
            assertFalse(node.title().isBlank()); assertFalse(node.description().isBlank());
            assertFalse(node.description().contains(" - ")); assertFalse(node.description().contains("—")); assertFalse(node.description().contains("–"));
            assertTrue(node.description().length() <= 130, "One or two short lines: " + node.description());
        }
        var links = com.hexvane.aetherhaven.workplace.BuildingUpgradeConnections.build(nodes, node -> 0);
        assertFalse(links.isEmpty());
        for (var link : links) {
            assertEquals(2, link.width(), "Every link runs straight down from the first level to the second");
            assertTrue(nodes.stream().anyMatch(node -> node.tier() == 1 && node.x() + 87 == link.x() && link.y() >= node.y() + 100 && link.y() + link.height() <= node.y() + 124));
        }
        assertEquals(6, links.stream().map(link -> link.x() + ":" + (link.y() < 300)).distinct().count(), "One link for each of the six tracks");
        assertTrue(com.hexvane.aetherhaven.workplace.WorkplaceUpgradesPage.supports(FarmFields.KIND));
    }

    // Old saves -------------------------------------------------------------------------------------------------

    @Test void aFarmThatAlreadyDidFieldWorkKeepsWorkingWithItsBedsAsThePlan() {
        // A save from before the Scarecrow Fields: three adopted beds, a harvest in hand, a cut still settling,
        // stock in the stores, every ingot upgrade bought and one crop unlocked.
        var old = new PlotProductionState();
        var beds = List.of(new FieldProductionState.Bed(new Point(5, 101, 5), "Plant_Crop_Wheat_Block", WHEAT),
            new FieldProductionState.Bed(new Point(6, 101, 5), "Plant_Crop_Lettuce_Block", LETTUCE),
            new FieldProductionState.Bed(new Point(7, 101, 5), "Plant_Crop_Corn_Block", CORN));
        assertNull(old.field().adopt(0, beds));
        assertTrue(old.field().harvest(beds.get(0), 2, () -> true));
        old.addAmount(WHEAT, 21, 25); old.addAmount(ESSENCE, 40, 100); old.addWorkplaceOutputUnlock(CORN);
        old.setIronUpgrade(1); old.setCobaltLevel(2); old.setThoriumLevel(3); old.setAdamantineLevel(2);
        String saved = gson.toJson(old);
        assertFalse(saved.contains("\"farm\""), "The old save knows nothing of the Scarecrow Fields");

        var production = gson.fromJson(saved, PlotProductionState.class);
        assertTrue(production.usesPhysicalField()); assertFalse(production.usesFarmFields());
        // FarmFields.production does exactly these two steps for a real farm.
        assertTrue(FarmUpgrades.mapLegacy(production)); assertEquals(3, production.farm().adoptLegacy(production.peekField()));
        var fields = production.farm();
        assertTrue(production.usesFarmFields(), "It keeps working with no action from the player");
        assertEquals(List.of(WHEAT, LETTUCE, CORN), fields.planned(HOME).stream().map(Cell::crop).toList(), "Its beds are the plan, with the crops that were in them");
        assertEquals(new Point(6, 101, 5), fields.planned(HOME).get(1).point()); assertEquals(GOOD, fields.soil(HOME, 5, 5));
        assertEquals(Map.of(WHEAT, 2), fields.basket(), "What she was carrying is in her basket");
        assertNull(production.peekField().cargo(), "and nowhere else, so it is delivered once");
        assertEquals(21, production.getAmount(WHEAT)); assertEquals(40, production.getAmount(ESSENCE)); assertTrue(production.isWorkplaceOutputUnlocked(CORN));
        assertEquals(3, production.getThoriumLevel()); assertEquals(2, production.getAdamantineLevel());
        assertEquals(1.6, WorkplaceProductionUpgrades.speedMultiplier(production), 1e-9, "Speed bought with ingots still makes her quicker");
        assertEquals(75, FarmUpgrades.capacity(production, catalog(production), WHEAT), "Storage bought with ingots still widens the stores");
        assertEquals(2, FarmUpgrades.level(production, Track.SCARECROWS), "Three bought output slots become two Scarecrows");
        assertEquals(1, FarmUpgrades.level(production, Track.REACH), "and the third becomes reach");
        assertEquals(16, fields.seeds(WHEAT_SEED), "It is given a first handful of seeds");
        // The carry over happens once, and the save keeps everything.
        assertFalse(FarmUpgrades.mapLegacy(production)); assertEquals(0, production.farm().adoptLegacy(production.peekField()));
        assertEquals(3, fields.plannedCount()); assertEquals(Map.of(WHEAT, 2), fields.basket()); assertEquals(16, fields.seeds(WHEAT_SEED));
        production = gson.fromJson(gson.toJson(production), PlotProductionState.class);
        assertFalse(FarmUpgrades.mapLegacy(production)); assertEquals(0, production.farm().adoptLegacy(production.peekField()));
        assertEquals(3, production.farm().plannedCount()); assertEquals(Map.of(WHEAT, 2), production.farm().basket());
        var entry = catalog(production); var loaded = production;
        assertEquals(Map.of(WHEAT, 2), loaded.farm().deliverAll(loaded, item -> FarmUpgrades.capacity(loaded, entry, item), item -> true, 6, 20).stored());
        assertEquals(23, production.getAmount(WHEAT));
    }

    @Test void aHarvestThatWasStillSettlingIsNotLostEither() {
        var old = new PlotProductionState();
        var bed = new FieldProductionState.Bed(new Point(5, 101, 5), "Plant_Crop_Wheat_Block", WHEAT);
        assertNull(old.field().adopt(0, List.of(bed)));
        assertTrue(old.field().beginHarvest(bed, "Plant_Crop_Wheat_Block_Stage1", Map.of(WHEAT, 2, ESSENCE, 3), () -> true));
        var production = gson.fromJson(gson.toJson(old), PlotProductionState.class);
        FarmUpgrades.mapLegacy(production); production.farm().adoptLegacy(production.peekField());
        assertEquals(Map.of(WHEAT, 2, ESSENCE, 3), production.farm().basket()); assertNull(production.peekField().pending());
    }

    @Test void aFarmStillOnAutomaticProductionKeepsItUntilAPlanIsPainted() {
        var old = new PlotProductionState();
        old.addAmount(WHEAT, 25, 25); old.setSlotCursor(0, 1); old.setSlotTickAccum(0, 900); old.setThoriumLevel(1);
        var production = gson.fromJson(gson.toJson(old), PlotProductionState.class);
        assertTrue(FarmUpgrades.mapLegacy(production)); assertEquals(0, production.farm().adoptLegacy(production.peekField()));
        assertNull(production.peekField(), "Looking at an old farm never invents beds for it");
        assertFalse(production.usesFarmFields()); assertFalse(production.usesPhysicalField(), "Automatic production carries on");
        assertEquals(25, production.getAmount(WHEAT)); assertEquals(1, production.getSlotCursor(0)); assertEquals(900, production.getSlotTickAccum(0));
        assertEquals(1, production.getThoriumLevel()); assertEquals(0, FarmUpgrades.level(production, Track.SCARECROWS));
        assertEquals(16, production.farm().seeds(WHEAT_SEED));
        production = gson.fromJson(gson.toJson(production), PlotProductionState.class);
        assertFalse(production.usesFarmFields(), "Being looked at and saved does not start field work");
        assertFalse(FarmUpgrades.mapLegacy(production)); assertEquals(16, production.farm().seeds(WHEAT_SEED), "The first seeds are given once");
        assertTrue(production.farm().paint(HOME, 1, 101, 1, WHEAT));
        assertTrue(production.usesFarmFields()); assertTrue(production.usesPhysicalField(), "The first painted cell hands the farm to the Farmer");
        assertEquals(25, production.getAmount(WHEAT), "Nothing in the stores is lost");
    }

    @Test void bedsAdoptedTheOldWayAfterTheFarmWasFirstSeenStillReachThePlan() {
        var production = farm();
        assertEquals(0, production.farm().adoptLegacy(production.peekField()));
        var bed = new FieldProductionState.Bed(new Point(5, 101, 5), "Plant_Crop_Wheat_Block", WHEAT);
        assertNull(production.field().adopt(0, List.of(bed)));
        assertEquals(1, production.farm().adoptLegacy(production.peekField())); assertEquals(WHEAT, production.farm().cell(HOME, 5, 5).crop());
        assertEquals(0, production.farm().adoptLegacy(production.peekField()));
        // A plan the player already painted is not painted over.
        assertTrue(production.farm().paint(HOME, 5, 101, 5, LETTUCE));
        assertNull(production.field().adopt(production.field().revision(), List.of(bed)));
        assertEquals(0, production.farm().adoptLegacy(production.peekField())); assertEquals(LETTUCE, production.farm().cell(HOME, 5, 5).crop());
    }

    // Balance ---------------------------------------------------------------------------------------------------

    /**
     * A farm day by the second: the Farmer's real choices and the farm's real records over a field whose crops grow
     * by the game's own numbers. A day is 48 minutes, so one game second is a thirtieth of a real one. A crop takes
     * 86,400 game seconds unwatered in the dark, grows twice as fast in daylight (six tenths of the day) and two and
     * a half times as fast on watered soil. She walks three and a half blocks a second and loses a second between
     * jobs. Her shift is 6:00 to 16:30 and 18:00 to 20:00. Every plant gives one crop or two in turn, as wheat does.
     */
    private static final class Days {
        static final double DAY = 2880, GAME = 30, GROW = 86400, WALK = 3.5, GAP = 1.0;
        final PlotProductionState production; final FarmFieldsState fields; final FarmFieldRules rules = FarmFieldRules.get();
        final Map<Point, double[]> crops = new HashMap<>();   // growth 0 to 1, watered until (game seconds)
        final Map<Point, String> standing = new HashMap<>();
        final Point storage; final Set<String> favoured; final boolean rotateDaily, oldLoop, collectDaily; final long keep;
        double x, z, now; int harvests, gathered, stored;
        Days(PlotProductionState production, Point storage, Set<String> favoured, boolean rotateDaily, boolean oldLoop, long keep, boolean collectDaily) {
            this.production = production; fields = production.farm(); this.storage = storage; this.favoured = favoured;
            this.rotateDaily = rotateDaily; this.oldLoop = oldLoop; this.keep = keep; this.collectDaily = collectDaily; x = storage.x(); z = storage.z();
        }
        List<Cell> cells() { var all = new ArrayList<Cell>(); for (var field : fields.fields().values()) for (var cell : field.cells()) if (cell.planned()) all.add(cell); return all; }
        int slotOf(Cell cell) { return fields.owner(cell.x(), cell.z()); }
        boolean onDuty() { double hour = now * GAME % 86400 / 3600; return hour >= 6 && hour < 16.5 || hour >= 18 && hour < 20; }
        boolean daylight() { double hour = now * GAME % 86400 / 3600; return hour >= 4.8 && hour < 19.2; }
        void grow(double seconds) {
            for (var crop : crops.entrySet())
                crop.getValue()[0] = Math.min(1, crop.getValue()[0] + seconds * GAME * (daylight() ? 2 : 1) * (crop.getValue()[1] > now * GAME ? 2.5 : 1) / GROW);
        }
        void pass(double seconds) { for (double left = seconds; left > 0; left -= 1) { grow(Math.min(1, left)); now += Math.min(1, left); } }
        double walk(Point to) { double d = Math.abs(to.x() - x) + Math.abs(to.z() - z); x = to.x(); z = to.z(); return d / WALK + GAP; }
        View view(Cell cell) {
            var crop = crops.get(cell.point());
            return crop == null ? new View(true, true, null, false, false)
                : new View(true, false, standing.get(cell.point()), crop[0] >= 1, crop[1] < now * GAME + rules.work().rewaterHours() * 3600);
        }
        void sow(int slot, Cell cell, String crop) {
            fields.planted(slot, cell.x(), cell.z(), crop);
            crops.put(cell.point(), new double[] {favoured.contains(crop) ? 10500 / GROW : 0, fields.soil(slot, cell.x(), cell.z()) == WORN ? 0 : now * GAME + 86400});
            standing.put(cell.point(), crop);
        }
        /** The Scarecrow Fields: the real choice of job, with a basket. */
        void step() {
            var job = FarmFieldWork.next(fields, (slot, cell) -> view(cell), new FarmFieldWork.Needs(onDuty(), rules.work().basket(), crop -> true, crop -> true,
                crop -> production.getAmount(crop) + fields.basket().getOrDefault(crop, 0) < keep || fields.compost() < rules.compost().keep(),
                FarmUpgrades.compostEnriches(production), cell -> false,
                cell -> Math.abs(cell.x() - x) + Math.abs(cell.z() - z)));
            if (job == null) { pass(3); return; }
            var work = rules.work();
            switch (job.action()) {
                case DELIVER -> {
                    pass(walk(storage) + work.deliverSeconds());
                    var put = fields.deliverAll(production, item -> keep, item -> NativeFarmCrops.byOutput(item) != null, FarmUpgrades.cropsPerCompost(production), rules.compost().keep());
                    for (var part : put.stored().entrySet()) if (NativeFarmCrops.byOutput(part.getKey()) != null) stored += part.getValue();
                }
                case HARVEST -> {
                    pass(walk(job.cell().point()) + work.harvestSeconds() + work.plantSeconds());
                    var cut = fields.harvest(job.slot(), job.cell().x(), job.cell().z(), job.crop(), null, 1 + harvests % 2, 3, favoured.contains(job.crop()), 0, 0.999, 0.999, rules);
                    harvests++; gathered += cut.crops(); crops.remove(job.cell().point());
                    var cell = fields.cell(job.slot(), job.cell().x(), job.cell().z());
                    sow(job.slot(), cell, cell.crop());
                }
                case PLANT -> { pass(walk(job.cell().point()) + work.plantSeconds()); sow(job.slot(), job.cell(), job.crop()); }
                case WATER -> {
                    pass(walk(job.cell().point()) + work.waterSeconds()); int radius = FarmUpgrades.waterRadius(production);
                    for (var cell : cells()) if (cell.soil() != WORN && Math.abs(cell.x() - job.cell().x()) <= radius && Math.abs(cell.z() - job.cell().z()) <= radius
                        && crops.containsKey(cell.point())) crops.get(cell.point())[1] = now * GAME + 86400;
                }
                case COMPOST -> { pass(walk(job.cell().point()) + work.compostSeconds()); fields.spread(job.slot(), job.cell().x(), job.cell().z(), FarmUpgrades.compostEnriches(production)); }
            }
        }
        /** The farm before this change: six seconds to cut, a walk to the stores after every cut, four seconds to water, the crop growing back by itself. */
        void oldStep() {
            if (!onDuty()) { pass(3); return; }
            Cell ripe = null, dry = null;
            for (var cell : cells()) {
                var crop = crops.get(cell.point());
                if (crop[0] >= 1 && ripe == null) ripe = cell;
                if (crop[1] < now * GAME + 4 * 3600 && dry == null) dry = cell;
            }
            if (ripe != null) {
                pass(walk(ripe.point()) + 6); gathered += 1 + harvests % 2; stored += 1 + harvests % 2; harvests++; crops.get(ripe.point())[0] = 0;
                pass(walk(storage) + 1.5);
            } else if (dry != null) { pass(walk(dry.point()) + 4); crops.get(dry.point())[1] = now * GAME + 86400; }
            else pass(2);
        }
        /** Runs whole days. The first only gets the field going, so the counts are of the days after it. */
        Days run(int days) {
            if (oldLoop) for (var cell : cells()) { crops.put(cell.point(), new double[] {0, 0}); standing.put(cell.point(), cell.crop()); }
            for (int day = 0; day < days; day++) {
                if (day == 1) { harvests = 0; gathered = 0; stored = 0; }
                if (rotateDaily && day > 0) for (int slot : fields.fields().keySet()) fields.rotate(slot);
                if (collectDaily) for (String item : new ArrayList<>(production.getAmounts().keySet())) production.removeAmountUpTo(item, Long.MAX_VALUE);
                double end = (day + 1) * DAY;
                while (now < end) { if (oldLoop) oldStep(); else step(); }
            }
            this.days = days - 1;
            return this;
        }
        int days;
        double harvestsADay() { return harvests / (double) days; }
        double gatheredADay() { return gathered / (double) days; }
        double storedADay() { return stored / (double) days; }
        double soil() { return cells().stream().mapToInt(Cell::soil).average().orElse(GOOD); }
    }
    /** The farm building's own ground: three beds of sixteen, as its prefab lays them out, and its stores. */
    private static PlotProductionState plot(String... bedCrops) {
        var production = farm(); int bed = 0;
        for (int[] corner : new int[][] {{8, 2}, {2, 8}, {8, 8}}) {
            for (int dx = 0; dx < 4; dx++) for (int dz = 0; dz < 4; dz++)
                assertTrue(production.farm().paint(HOME, corner[0] + dx, 101, corner[1] + dz, bedCrops[bed % bedCrops.length]));
            bed++;
        }
        return production;
    }
    private static final Point STORES = new Point(2, 101, 3);
    private static void report(String name, Days run) {
        System.out.printf(Locale.ROOT, "FARM BALANCE %-34s harvests %5.1f  crops gathered %5.1f  crops stored %5.1f  soil %.2f%n",
            name, run.harvestsADay(), run.gatheredADay(), run.storedADay(), run.soil());
    }

    @Test void aFullyPlannedFarmGivesAboutWhatItDidAndTheBestFarmAboutTwiceThat() {
        // Before: 48 beds planted by hand, with a walk to the stores after every harvest.
        var before = new Days(plot(WHEAT), STORES, Set.of(), false, true, 100_000, false).run(5);
        // Now, no upgrades, the same 48 cells of one crop, the stores emptied as fast as they fill: the soil wears.
        var worn = new Days(plot(WHEAT), STORES, Set.of(), false, false, 100_000, false).run(7);
        // Now, no upgrades, three crops, rotated once a day by the player.
        var rotated = new Days(plot(WHEAT, LETTUCE, CORN), STORES, Set.of(), true, false, 100_000, false).run(7);
        // Now, no upgrades, three crops, the stores keeping their 25 of each and emptied once a day: the spare feeds the soil.
        var kept = new Days(plot(WHEAT, LETTUCE, CORN), STORES, Set.of(), false, false, 25, true).run(9);
        // One Scarecrow field alone, at its first reach: the 24 cells around the Scarecrow.
        var small = farm(); assertNull(small.farm().claim(1, 1, new Point(20, 101, 20)));
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (dx != 0 || dz != 0) assertTrue(small.farm().paint(1, 20 + dx, 101, 20 + dz, WHEAT));
        var scarecrow = new Days(small, STORES, Set.of(), false, false, 100_000, false).run(7);
        // The best farm: every upgrade, crops rotated daily, and this week's favoured crops in two beds of three.
        var best = plot(WHEAT, LETTUCE, CORN);
        for (var track : Track.values()) best.farm().setLevel(track.name(), FarmUpgrades.maxLevel(track));
        var top = new Days(best, STORES, Set.of(WHEAT, LETTUCE), true, false, 100_000, false).run(7);
        report("before, 48 beds by hand", before); report("now, one crop, never rotated", worn); report("now, three crops rotated daily", rotated);
        report("now, stores keep 25, emptied daily", kept); report("now, one Scarecrow field of 24", scarecrow); report("now, every upgrade and favoured", top);
        // No upgrades, no attention, stores emptied all day: a little less than before, because the soil wears out.
        assertTrue(worn.gatheredADay() >= before.gatheredADay() * 0.7 && worn.gatheredADay() <= before.gatheredADay() * 1.1,
            "A farm left to wear out gives about what it did before: " + worn.gatheredADay() + " against " + before.gatheredADay());
        assertEquals(WORN, Math.round(worn.soil()), "One crop for ever with nothing spare wears the soil out");
        // No upgrades, no attention, collected once a day: the stores keep what they always kept, and the spare keeps the soil Good.
        assertTrue(kept.storedADay() > 70 && kept.storedADay() <= 75.5, "A farm keeps 25 of each crop, as it always did: " + kept.storedADay());
        assertTrue(kept.soil() >= 0.9, "Crops the stores cannot take keep the soil Good: " + kept.soil());
        assertTrue(kept.gatheredADay() >= before.gatheredADay() * 0.8 && kept.gatheredADay() <= before.gatheredADay() * 1.2,
            "A new farm with no upgrades gathers about what it did: " + kept.gatheredADay() + " against " + before.gatheredADay());
        // Rotating crops once a day makes most soil Rich and is worth about double.
        assertTrue(rotated.soil() >= 1.5, "A daily change of crop keeps most soil Rich: " + rotated.soil());
        assertTrue(rotated.gatheredADay() >= before.gatheredADay() * 1.6 && rotated.gatheredADay() <= before.gatheredADay() * 2.4,
            "Good rotation is worth about double: " + rotated.gatheredADay() + " against " + before.gatheredADay());
        // Every upgrade, daily rotation and favoured crops in two beds of three: more than double, far from ten times.
        assertTrue(top.gatheredADay() > rotated.gatheredADay() && top.gatheredADay() <= before.gatheredADay() * 3.0,
            "Every upgrade and good rotation are worth between two and three times a plain farm: " + top.gatheredADay() + " against " + before.gatheredADay());
        assertTrue(scarecrow.gatheredADay() < worn.gatheredADay(), "One small Scarecrow field gives less than the farm's own 48 cells");
    }
}
