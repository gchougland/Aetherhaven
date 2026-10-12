package com.hexvane.aetherhaven.construction;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.construction.RebuildBenchCarry.Saved;
import com.hexvane.aetherhaven.construction.RebuildBenchCarry.Slot;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** P23: a rebuilt building keeps its workbench tiers. Saved bench state only, no world. */
@Tag("construction")
class RebuildBenchCarryTest {
    private static Object previousStore;

    private static class Items extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        Items(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() { super(String.class, Item.class, new DefaultAssetMap<>()); setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId); }
            @Override public Items build() { return new Items(this); }
        }
    }

    @BeforeAll static void assets() throws Exception {
        var f = Item.class.getDeclaredField("ASSET_STORE"); f.setAccessible(true); previousStore = f.get(null); f.set(null, new Items.Config().build());
    }
    @AfterAll static void restore() throws Exception {
        var f = Item.class.getDeclaredField("ASSET_STORE"); f.setAccessible(true); f.set(null, previousStore);
    }

    private static final String WORKBENCH = "Workbench", FURNACE = "Furnace";

    private static <T> Saved<T> saved(String bench, int x, int tier, T materials) { return new Saved<>(bench, x, 0, 0, tier, materials); }
    private static Slot slot(String bench, int x) { return new Slot(bench, x, 0, 0); }

    /** The paste replaces every bench with the block's default state: tier one, nothing stored. */
    private static Map<Slot, BenchBlock> pasted(Slot... slots) {
        var out = new LinkedHashMap<Slot, BenchBlock>();
        for (var slot : slots) out.put(slot, new BenchBlock());
        return out;
    }

    @Test void rebuiltBenchesKeepTheirTierAndTheirMaterialsExactlyOnce() {
        var ingots = new ItemStack("ingot", 4);
        var leather = new ItemStack("leather", 2);
        var old = new LinkedHashMap<Slot, BenchBlock>();
        old.put(slot(WORKBENCH, 1), new BenchBlock(3, new ItemStack[] {ingots, leather}));
        old.put(slot(WORKBENCH, 5), new BenchBlock(2, new ItemStack[] {new ItemStack("ingot", 1)}));
        old.put(slot(FURNACE, 9), new BenchBlock());

        var carried = RebuildBenchCarry.take(old);
        assertEquals(2, carried.size(), "a bench that was never upgraded has nothing to carry");
        // The engine drops whatever an old bench still stores when the paste removes it.
        for (var bench : old.values()) assertEquals(0, bench.getUpgradeItems().length);

        var rebuilt = pasted(slot(WORKBENCH, 1), slot(WORKBENCH, 5), slot(FURNACE, 9));
        for (var bench : rebuilt.values()) assertEquals(1, bench.getTierLevel(), "this is the reset the owner reported");
        var restored = new ArrayList<Slot>();
        var surplus = RebuildBenchCarry.give(carried, rebuilt, (s, tier) -> true, (s, tier) -> restored.add(s));

        assertTrue(surplus.isEmpty());
        assertEquals(3, rebuilt.get(slot(WORKBENCH, 1)).getTierLevel());
        assertEquals(List.of(ingots, leather), Arrays.asList(rebuilt.get(slot(WORKBENCH, 1)).getUpgradeItems()));
        assertEquals(2, rebuilt.get(slot(WORKBENCH, 5)).getTierLevel());
        assertEquals(1, rebuilt.get(slot(WORKBENCH, 5)).getUpgradeItems().length);
        assertEquals(1, rebuilt.get(slot(FURNACE, 9)).getTierLevel());
        assertEquals(0, rebuilt.get(slot(FURNACE, 9)).getUpgradeItems().length);
        assertEquals(List.of(slot(WORKBENCH, 1), slot(WORKBENCH, 5)), restored);
    }

    @Test void aBenchTheNewStyleMovedTakesItsTierToTheNearestBenchOfTheSameKind() {
        var plan = RebuildBenchCarry.plan(
            List.of(saved(WORKBENCH, 1, 3, "three"), saved(WORKBENCH, 10, 2, "two")),
            List.of(slot(WORKBENCH, 1), slot(WORKBENCH, 12), slot(WORKBENCH, 30), slot(FURNACE, 10)));
        assertEquals("three", plan.restore().get(slot(WORKBENCH, 1)).materials());
        assertEquals("two", plan.restore().get(slot(WORKBENCH, 12)).materials());
        assertNull(plan.restore().get(slot(WORKBENCH, 30)), "a bench with no history stays as pasted");
        assertNull(plan.restore().get(slot(FURNACE, 10)), "a tier never crosses to another kind of bench");
        assertTrue(plan.surplus().isEmpty());
    }

    @Test void whenTheNewStyleHasFewerBenchesTheBestTiersStayAndTheRestIsHandedBack() {
        var plan = RebuildBenchCarry.plan(
            List.of(saved(WORKBENCH, 1, 2, "low"), saved(WORKBENCH, 4, 4, "high"), saved(WORKBENCH, 8, 3, "middle"),
                saved(FURNACE, 6, 2, "furnace")),
            List.of(slot(WORKBENCH, 8), slot(WORKBENCH, 20)));
        assertEquals("middle", plan.restore().get(slot(WORKBENCH, 8)).materials(), "same cell wins first");
        assertEquals("high", plan.restore().get(slot(WORKBENCH, 20)).materials());
        assertEquals(List.of("low", "furnace"), plan.surplus().stream().map(Saved::materials).toList());
    }

    @Test void aBenchThatCannotReachTheSavedTierGetsItsBestLowerTierAndKeepsTheMaterials() {
        var paid = new ItemStack("ingot", 9);
        var old = new LinkedHashMap<Slot, BenchBlock>();
        old.put(slot(WORKBENCH, 1), new BenchBlock(4, new ItemStack[] {paid}));
        var rebuilt = pasted(slot(WORKBENCH, 1));
        var surplus = RebuildBenchCarry.give(RebuildBenchCarry.take(old), rebuilt, (s, tier) -> tier <= 2, (s, tier) -> assertEquals(2, tier));
        assertTrue(surplus.isEmpty());
        assertEquals(2, rebuilt.get(slot(WORKBENCH, 1)).getTierLevel());
        assertEquals(List.of(paid), Arrays.asList(rebuilt.get(slot(WORKBENCH, 1)).getUpgradeItems()));
    }

    @Test void surplusMaterialsAreReturnedOnceAndNeverAlsoLeftOnABench() {
        var paid = new ItemStack("ingot", 6);
        var old = new LinkedHashMap<Slot, BenchBlock>();
        old.put(slot(WORKBENCH, 1), new BenchBlock(2, new ItemStack[] {paid}));
        var rebuilt = pasted(slot(FURNACE, 1));
        var surplus = RebuildBenchCarry.give(RebuildBenchCarry.take(old), rebuilt, (s, tier) -> true, (s, tier) -> fail("nothing to restore"));
        assertEquals(1, surplus.size());
        assertEquals(List.of(paid), Arrays.asList(surplus.getFirst().materials()));
        assertEquals(0, old.get(slot(WORKBENCH, 1)).getUpgradeItems().length);
        assertEquals(0, rebuilt.get(slot(FURNACE, 1)).getUpgradeItems().length);
        assertEquals(1, rebuilt.get(slot(FURNACE, 1)).getTierLevel());
    }
}
