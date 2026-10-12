package com.hexvane.aetherhaven.warehouse;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Path;
import java.util.*;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;

/** Deep shelves (several stacks in one general storage slot) and storing matching items. Test items stack to 100. */
@Tag("town")
class WarehouseShelvesTest {
    private static final TownWarehouse.Page GENERAL = TownWarehouse.Page.GENERAL;
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
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousStore = field.get(null);
        field.set(null, new Items.Config().build());
    }

    @AfterAll static void restore() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousStore);
    }

    private static SimpleItemContainer bag(ItemStack... stacks) {
        var bag = new SimpleItemContainer((short) stacks.length);
        for (short i = 0; i < stacks.length; i++) bag.setItemStackForSlot(i, stacks[i], false);
        return bag;
    }
    private static ItemStack stock(String id, int count) { return new ItemStack(id, count); }
    private static int held(SimpleItemContainer bag, int slot) {
        var stack = bag.getItemStack((short) slot);
        return ItemStack.isEmpty(stack) ? 0 : stack.getQuantity();
    }
    private static int total(SimpleItemContainer bag, String id) {
        int total = 0;
        for (short i = 0; i < bag.getCapacity(); i++) {
            var stack = bag.getItemStack(i);
            if (!ItemStack.isEmpty(stack) && id.equals(stack.getItemId())) total += stack.getQuantity();
        }
        return total;
    }
    /** Stores every stack of the bag by clicking it, as a player would. */
    private static void supply(TownWarehouse town, ItemStack... stacks) {
        var bag = bag(stacks);
        for (short i = 0; i < stacks.length; i++)
            assertEquals(stacks[i].getQuantity(), town.deposit(GENERAL, bag, i, stacks[i], TownWarehouse.Amount.STACK).moved());
    }
    private static TownWarehouse shelves(int stacksPerSlot) {
        var town = new TownWarehouse();
        while (town.stacksPerSlot() < stacksPerSlot) assertTrue(town.upgradeStacks(town.stackUpgrades()));
        return town;
    }
    private static TownWarehouse reload(TownWarehouse town) { return new Gson().fromJson(new Gson().toJson(town), TownWarehouse.class); }
    private static TownWarehouse.Transfer take(TownWarehouse town, SimpleItemContainer bag, int slot, TownWarehouse.Amount amount) {
        return town.withdraw(GENERAL, bag, (short) slot, town.stack(GENERAL, (short) slot), amount);
    }

    @Test void savesFromBeforeTheUpgradeLoadWithOneStackPerSlot() {
        var legacy = bag(stock("ore", 100), stock("ore", 40));
        var saved = new com.google.gson.JsonObject();
        saved.addProperty("storageUpgrades", 1);
        saved.addProperty("generalData", SimpleItemContainer.CODEC.encode(legacy, new com.hypixel.hytale.codec.ExtraInfo()).asDocument().toJson());
        var town = new Gson().fromJson(saved, TownWarehouse.class);
        assertEquals(1, town.stacksPerSlot());
        assertEquals(0, town.stackUpgrades());
        assertEquals(60, town.slots(GENERAL));
        assertEquals(100, town.stack(GENERAL, (short) 0).getQuantity());
        assertEquals(40, town.stack(GENERAL, (short) 1).getQuantity());
        // Without the upgrade a third stack still needs its own slot, exactly as before.
        supply(town, stock("ore", 100));
        assertEquals(3, town.used(GENERAL));
        assertEquals(100, town.slotLimit(GENERAL, stock("ore", 1)));
    }

    @Test void deepShelvesFillOneSlotBeforeOpeningAnotherAndSurviveReload() {
        var town = shelves(3);
        supply(town, stock("ore", 100), stock("ore", 100), stock("ore", 100), stock("ore", 50));
        assertEquals(300, town.stack(GENERAL, (short) 0).getQuantity());
        assertEquals(50, town.stack(GENERAL, (short) 1).getQuantity());
        assertEquals(2, town.used(GENERAL));
        assertEquals(300, town.slotLimit(GENERAL, stock("ore", 1)));
        assertEquals(100, town.slotLimit(TownWarehouse.Page.OUTPUT, stock("ore", 1)), "Crafted goods keep single stacks");
        var restored = reload(town);
        assertEquals(3, restored.stacksPerSlot());
        assertEquals(300, restored.stack(GENERAL, (short) 0).getQuantity());
        assertEquals(350, restored.count(GENERAL, "ore"));
    }

    @Test void theUpgradeIsTieredPricedInFivesAndOneOfferIsNeverBoughtTwice() {
        var town = new TownWarehouse();
        for (int tier = 1; tier <= TownWarehouse.MAX_STACK_UPGRADES; tier++) {
            assertEquals(TownWarehouse.STACK_UPGRADE_GOLD * tier, town.stackUpgradeCost());
            assertEquals(0, town.stackUpgradeCost() % 5);
            int seen = town.stackUpgrades();
            // Stock moving between the draw and the press is no reason to refuse the upgrade.
            supply(town, stock("courier" + tier, 1));
            assertTrue(town.upgradeStacks(seen));
            assertFalse(town.upgradeStacks(seen), "A second press on the same offer buys nothing");
            assertEquals(tier + 1, town.stacksPerSlot());
        }
        assertFalse(town.upgradeStacks(town.stackUpgrades()), "It is tiers, not unlimited");
        assertEquals(1 + TownWarehouse.MAX_STACK_UPGRADES, reload(town).stacksPerSlot());
        // Slot upgrades are a separate purchase and keep working.
        assertTrue(town.upgrade(town.upgrades()));
        assertEquals(60, town.capacity());
    }

    @Test void aClickTakesOneStackFromADeepShelfAndMetadataSurvives() {
        var town = shelves(2);
        var special = new ItemStack("ore", 100, BsonDocument.parse("{engraved:'Mara'}"));
        supply(town, special, special);
        assertEquals(1, town.used(GENERAL));
        var bag = bag(null, null, null);
        assertEquals(1, take(town, bag, 0, TownWarehouse.Amount.ONE).moved());
        assertEquals(100, take(town, bag, 0, TownWarehouse.Amount.STACK).moved());
        assertEquals(99, town.stack(GENERAL, (short) 0).getQuantity());
        assertEquals(special.withQuantity(1), bag.getItemStack((short) 0).withQuantity(1));
        assertEquals(99, take(town, bag, 0, TownWarehouse.Amount.ALL_MATCHING).moved());
        assertEquals(0, town.used(GENERAL));
        assertEquals(200, total(bag, "ore"));
    }

    /** The game's own slot move throws away what does not fit when the source slot is above one stack. */
    @Test void aNearlyFullInventoryNeverLosesTheOverflowOfADeepShelf() {
        var town = shelves(3);
        supply(town, stock("ore", 100), stock("ore", 100), stock("ore", 100));
        var bag = bag(stock("ore", 60));
        assertEquals(40, take(town, bag, 0, TownWarehouse.Amount.STACK).moved());
        assertEquals(100, held(bag, 0));
        assertEquals(260, town.stack(GENERAL, (short) 0).getQuantity());
        long seen = town.revision();
        var refused = take(town, bag, 0, TownWarehouse.Amount.ALL_MATCHING);
        assertEquals(0, refused.moved());
        assertNotNull(refused.error());
        assertEquals(seen, town.revision());
        assertEquals(360, town.count(GENERAL, "ore") + total(bag, "ore"), "Every item is still in one of the two places");
    }

    @Test void aFullShelfRefusesTheRestAndLeavesItWithThePlayer() {
        var town = shelves(2);
        for (int i = 0; i < TownWarehouse.BASE_GENERAL_SLOTS - 1; i++) supply(town, stock("filler" + i, 1));
        supply(town, stock("ore", 100), stock("ore", 70));
        assertEquals(TownWarehouse.BASE_GENERAL_SLOTS, town.used(GENERAL));
        var bag = bag(stock("ore", 50));
        assertEquals(30, town.deposit(GENERAL, bag, (short) 0, bag.getItemStack((short) 0), TownWarehouse.Amount.STACK).moved());
        assertEquals(20, held(bag, 0));
        assertEquals(0, town.deposit(GENERAL, bag, (short) 0, bag.getItemStack((short) 0), TownWarehouse.Amount.STACK).moved());
        assertEquals(20, held(bag, 0));
        assertEquals(200, town.count(GENERAL, "ore"));
    }

    @Test void courierReservationsOnADeepShelfAreHeldBackAndCollectedInFull() {
        var town = shelves(3);
        supply(town, stock("ore", 100), stock("ore", 100), stock("ore", 50));
        var batch = town.reserve(UUID.randomUUID(), UUID.randomUUID(), "recipe",
            List.of(new MaterialQuantity("ore", null, null, 150, null)), List.of(stock("kit", 1)));
        assertNotNull(batch);
        assertEquals(150, town.reserved(GENERAL, (short) 0));
        var bag = bag(null, null, null);
        assertEquals(100, take(town, bag, 0, TownWarehouse.Amount.ALL_MATCHING).moved(), "Only the unreserved part leaves");
        var held = take(town, bag, 0, TownWarehouse.Amount.STACK);
        assertEquals(0, held.moved());
        assertEquals("Those supplies are reserved for a workplace.", held.error());
        // Two stacks of cargo space carry the 150 in one trip.
        assertEquals(150, town.pickup(batch.id(), "courier", 2));
        assertEquals(0, town.count(GENERAL, "ore"));
        assertEquals(150, town.batch(batch.id()).cargoItems().stream().mapToInt(ItemStack::getQuantity).sum());
        // A cancelled delivery comes back onto the same deep shelf rather than needing two free slots.
        assertTrue(town.cancel(batch.id()));
        for (int i = 0; i < TownWarehouse.BASE_GENERAL_SLOTS - 1; i++) supply(town, stock("filler" + i, 1));
        supply(town, stock("ore", 10));
        assertEquals(TownWarehouse.BASE_GENERAL_SLOTS, town.used(GENERAL));
        assertTrue(town.returnCargo(batch.id(), "courier"));
        assertEquals(160, town.count(GENERAL, "ore"));
        assertTrue(town.batches().isEmpty());
    }

    @Test void returnedCargoThatDoesNotFitStaysWholeWithTheCourier() {
        var town = shelves(2);
        supply(town, stock("ore", 100), stock("ore", 60));
        var batch = town.reserve(UUID.randomUUID(), UUID.randomUUID(), "recipe",
            List.of(new MaterialQuantity("ore", null, null, 60, null)), List.of(stock("kit", 1)));
        assertEquals(60, town.pickup(batch.id(), "courier", 1));
        assertTrue(town.cancel(batch.id()));
        for (int i = 0; i < TownWarehouse.BASE_GENERAL_SLOTS - 1; i++) supply(town, stock("filler" + i, 1));
        supply(town, stock("ore", 50));
        // The one ore slot now holds 150 of its 200: 60 more do not fit and nothing may be split off.
        assertFalse(town.returnCargo(batch.id(), "courier"));
        assertEquals(60, town.batch(batch.id()).cargoItems().getFirst().getQuantity());
        assertEquals(150, town.count(GENERAL, "ore"));
    }

    @Test void guardSpoilsJoinADeepShelf() {
        var town = shelves(2);
        for (int i = 0; i < TownWarehouse.BASE_GENERAL_SLOTS - 1; i++) supply(town, stock("filler" + i, 1));
        supply(town, stock("hide", 100));
        assertTrue(town.acceptGuardSpoils(stock("hide", 80)));
        assertEquals(180, town.stack(GENERAL, (short) (TownWarehouse.BASE_GENERAL_SLOTS - 1)).getQuantity());
        assertFalse(town.acceptGuardSpoils(stock("hide", 21)), "All or nothing, as before");
        assertEquals(180, town.count(GENERAL, "hide"));
    }

    @Test void storingMatchingItemsMovesOnlyWhatIsAlreadyStoredAndSkipsTheHotbar() {
        var town = new TownWarehouse();
        supply(town, stock("ore", 5), stock("gem", 1));
        var marked = new ItemStack("ore", 3, BsonDocument.parse("{special:true}"));
        // Slot 0 stands for the hotbar.
        var bag = bag(stock("ore", 10), stock("ore", 20), stock("wood", 5), marked, stock("gem", 7), stock("ore", 30));
        var result = town.depositMatching(bag, 1, bag.getCapacity());
        assertEquals(57, result.moved());
        assertTrue(result.matched());
        assertEquals(10, held(bag, 0), "The hotbar is left alone");
        assertEquals(0, held(bag, 1));
        assertEquals(5, held(bag, 2), "Wood is not stored here yet");
        assertEquals(marked, bag.getItemStack((short) 3), "A stack with different properties is not a match");
        assertEquals(0, held(bag, 4));
        assertEquals(0, held(bag, 5));
        assertEquals(55, town.count(GENERAL, "ore"));
        assertEquals(8, town.count(GENERAL, "gem"));
        assertEquals(0, town.count(GENERAL, "wood"));
    }

    @Test void storingMatchingItemsRespectsRoomAndReservations() {
        var town = new TownWarehouse();
        for (int i = 0; i < TownWarehouse.BASE_GENERAL_SLOTS - 1; i++) supply(town, stock("filler" + i, 1));
        supply(town, stock("ore", 90));
        var batch = town.reserve(UUID.randomUUID(), UUID.randomUUID(), "recipe",
            List.of(new MaterialQuantity("ore", null, null, 40, null)), List.of(stock("kit", 1)));
        assertNotNull(batch);
        var bag = bag(stock("ore", 30), stock("wood", 5));
        var partly = town.depositMatching(bag, 0, 2);
        assertEquals(10, partly.moved());
        assertEquals(20, held(bag, 0), "What does not fit stays with the player");
        assertEquals(5, held(bag, 1));
        assertEquals(40, town.reserved(GENERAL, (short) (TownWarehouse.BASE_GENERAL_SLOTS - 1)), "The courier's share is untouched");
        var full = town.depositMatching(bag, 0, 2);
        assertEquals(0, full.moved());
        assertTrue(full.matched(), "Ore is stored here, there is simply no room");
        assertEquals(20, held(bag, 0));
        var nothing = town.depositMatching(bag(stock("wood", 5)), 0, 1);
        assertEquals(0, nothing.moved());
        assertFalse(nothing.matched());
        assertEquals(120, town.count(GENERAL, "ore") + held(bag, 0));
    }

    @Test void storingMatchingItemsFillsDeepShelves() {
        var town = shelves(4);
        supply(town, stock("ore", 100));
        var bag = bag(stock("ore", 100), stock("ore", 100), stock("ore", 100), stock("ore", 100));
        assertEquals(400, town.depositMatching(bag, 0, 4).moved());
        assertEquals(400, town.stack(GENERAL, (short) 0).getQuantity());
        assertEquals(100, town.stack(GENERAL, (short) 1).getQuantity());
        assertEquals(2, town.used(GENERAL));
    }
}
