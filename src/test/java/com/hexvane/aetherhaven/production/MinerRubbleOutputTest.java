package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.mastery.CoreProductionRequirements;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Stone Rubble is a Miner output mined out of a gravel vein. Adding it must not move any saved slot choice. */
@Tag("town")
class MinerRubbleOutputTest {
    private static final String MINE = AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT;
    private static final String RUBBLE = "Rubble_Stone";
    private static final String COBBLE = "Rock_Stone_Cobble";
    private static final List<String> ORES = List.of("Ore_Iron", "Ore_Silver", "Ore_Gold", "Ore_Thorium", "Ore_Cobalt",
        "Ore_Adamantite", "Ore_Mithril", "Ore_Prisma", "Ore_Onyxium");

    private final ProductionCatalog catalog = ProductionCatalog.loadFromClasspath(getClass().getClassLoader());
    private final WorkplaceUnlockCatalog unlocks = WorkplaceUnlockCatalog.loadFromClasspath(getClass().getClassLoader());

    private ProductionCatalog.Entry outputs(PlotProductionState state) {
        return ProductionEffectiveCatalog.effective(catalog, unlocks, MINE, state);
    }

    private static List<String> items(ProductionCatalog.Entry entry) {
        var out = new ArrayList<String>();
        for (int i = 0; i < entry.catalogSize(); i++) out.add(entry.itemAtCursor(i));
        return out;
    }

    @Test void rubbleIsSelectableFromANewMineWithoutBuyingAnything() {
        var state = new PlotProductionState();
        var entry = outputs(state);
        assertNotNull(entry);
        assertTrue(entry.containsItemId(RUBBLE));
        var line = unlocks.findLine(MINE, RUBBLE);
        assertNotNull(line);
        assertTrue(line.defaultUnlocked());
        assertEquals(0L, WorkplaceUnlockCatalog.goldCoinsForRarityTier(line.rarityTier()));
        assertNull(PhysicalProductionOutputPolicy.selectionIssue(state, 0, RUBBLE));
        assertTrue(state.setSlotCursorToItem(0, entry, RUBBLE));
        assertEquals(RUBBLE, state.itemAtSlotCursor(0, entry));
        var reloaded = new Gson().fromJson(new Gson().toJson(state), PlotProductionState.class);
        assertEquals(RUBBLE, reloaded.itemAtSlotCursor(0, outputs(reloaded)));
    }

    @Test void rubbleIsMinedOutOfANativeGravelVeinAndLeavesPlainStone() {
        assertEquals("Soil_Gravel", MiningNodeBlocks.blockFor(RUBBLE));
        // Gravel itself stays its own output and its own block.
        assertEquals("Soil_Gravel", MiningNodeBlocks.blockFor("Soil_Gravel"));
        assertEquals(COBBLE, MiningNodeBlocks.blockFor(COBBLE));
        assertTrue(MiningNodeBlocks.ownedMatches("Soil_Gravel", MiningNodeBlocks.blockFor(RUBBLE)));
        assertEquals("Rock_Stone", MiningNodeBlocks.DEPLETED);
    }

    @Test void rubbleIsGatedTimedAndStoredLikeCobble() {
        assertEquals(CoreProductionRequirements.requirement("miner", COBBLE), CoreProductionRequirements.requirement("miner", RUBBLE));
        assertEquals(new CoreProductionRequirements.Requirement(1, 0), CoreProductionRequirements.requirement("miner", RUBBLE));
        // A brand new town with the starting Crude Pickaxe may mine it.
        assertNull(CoreProductionRequirements.issueForKind(new TownRecord(), "miner", RUBBLE));
        assertNull(CoreProductionRequirements.issue(new TownRecord(), MINE, RUBBLE));

        var state = new PlotProductionState();
        var entry = outputs(state);
        int rubble = items(entry).indexOf(RUBBLE), cobble = items(entry).indexOf(COBBLE);
        assertEquals(entry.ticksAtCursor(cobble), entry.ticksAtCursor(rubble));
        assertEquals(entry.maxStorageAtCursor(cobble), entry.maxStorageAtCursor(rubble));
        assertEquals(WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, COBBLE),
            WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, RUBBLE));
        long cap = WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, RUBBLE);
        state.addAmount(RUBBLE, cap + 500, cap);
        assertEquals(cap, state.getAmount(RUBBLE));
        assertFalse(state.clampAmountsToCatalogEntry(entry, WorkplaceProductionUpgrades.capacityMultiplier(state)));
        // The storage upgrade grows both the same way.
        state.setAdamantineLevel(1);
        assertEquals(WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, COBBLE),
            WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, RUBBLE));
        assertTrue(WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, RUBBLE) > cap);
    }

    @Test void rubbleOnlyComesFromPhysicalWorkLikeEveryOtherVein() {
        var state = new PlotProductionState();
        var entry = outputs(state);
        int cursor = items(entry).indexOf(RUBBLE);
        double period = Math.max(1, entry.ticksAtCursor(cursor) / AetherhavenConstants.PRODUCTION_ENTITY_TICKS_PER_SECOND
            - MiningCycle.EXTRACTION_SECONDS);
        var cycle = state.mining().cycle(0);
        assertTrue(cycle.select(RUBBLE, period));
        cycle.simulate(10000);
        assertFalse(cycle.ready(), "Waiting never matures a vein in one step");
        for (int i = 0; i < 400 && !cycle.ready(); i++) cycle.simulate(.25);
        assertTrue(cycle.ready());
        assertFalse(cycle.extract(RUBBLE, period, true, () -> true), "No rubble without the Miner's swings");
        for (int i = 0; i < 16; i++) cycle.work(.25, true, true);
        assertFalse(cycle.extract(RUBBLE, period, true, () -> false), "No rubble when the gravel vein could not be depleted");
        assertTrue(cycle.extract(RUBBLE, period, true, () -> true));
        assertFalse(cycle.extract(RUBBLE, period, true, () -> fail("duplicate award")));
    }

    @Test void addingRubbleMovesNoSavedSlotChoice() {
        // Slot choices are saved as positions in this list, so every earlier position must still name the same item.
        var base = catalog.get(MINE);
        assertEquals(23, base.catalogSize(), "A new base line would shift every unlocked ore in saved mines");
        assertFalse(base.containsItemId(RUBBLE));
        var lines = unlocks.linesForWorkplace(MINE);
        assertEquals(RUBBLE, lines.getLast().itemId(), "Rubble stays the last Miner line so nothing saved sits after it");

        var none = new PlotProductionState();
        var some = new PlotProductionState();
        some.addWorkplaceOutputUnlock("Ore_Silver");
        some.addWorkplaceOutputUnlock("Ore_Cobalt");
        var all = new PlotProductionState();
        ORES.forEach(all::addWorkplaceOutputUnlock);
        for (var state : List.of(none, some, all)) {
            var now = items(outputs(state));
            assertEquals(RUBBLE, now.getLast());
            assertEquals(1, now.stream().filter(RUBBLE::equals).count());
            var before = new ArrayList<>(items(base));
            for (var line : lines)
                if (!line.itemId().equals(RUBBLE) && !base.containsItemId(line.itemId())
                    && (line.defaultUnlocked() || state.isWorkplaceOutputUnlocked(line.itemId()))) before.add(line.itemId());
            assertEquals(before, now.subList(0, now.size() - 1));
        }
    }

    @Test void buyingAnOreKeepsEverySlotOnItsMaterial() {
        var state = new PlotProductionState();
        state.addWorkplaceOutputUnlock("Ore_Silver");
        var before = outputs(state);
        assertTrue(state.setSlotCursorToItem(0, before, RUBBLE));
        assertTrue(state.setSlotCursorToItem(1, before, "Ore_Silver"));
        assertTrue(state.setSlotCursorToItem(2, before, COBBLE));
        int cobble = state.getSlotCursor(2);

        // Iron is listed ahead of Silver and Rubble, so its line pushes both down by one.
        state.addWorkplaceOutputUnlock("Ore_Iron");
        var after = outputs(state);
        assertEquals("Ore_Iron", state.itemAtSlotCursor(1, after), "Without the correction the Silver slot would mine Iron");
        assertTrue(state.keepSlotItems(before, after));
        assertEquals(RUBBLE, state.itemAtSlotCursor(0, after));
        assertEquals("Ore_Silver", state.itemAtSlotCursor(1, after));
        assertEquals(COBBLE, state.itemAtSlotCursor(2, after));
        assertEquals(cobble, state.getSlotCursor(2));
        // Unused slots stay on the first line, and a second pass changes nothing.
        assertEquals(0, state.getSlotCursor(3));
        assertEquals(0, state.getSlotCursor(4));
        assertFalse(state.keepSlotItems(after, after));
        var reloaded = new Gson().fromJson(new Gson().toJson(state), PlotProductionState.class);
        assertEquals(RUBBLE, reloaded.itemAtSlotCursor(0, outputs(reloaded)));
        assertEquals("Ore_Silver", reloaded.itemAtSlotCursor(1, outputs(reloaded)));
    }
}
