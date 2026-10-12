package com.hexvane.aetherhaven.warehouse;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.equipment.GuardKit;
import com.hexvane.aetherhaven.equipment.GuardKitCatalog;
import com.hexvane.aetherhaven.equipment.GuardKitService;
import com.hexvane.aetherhaven.equipment.ProfessionKit;
import com.hexvane.aetherhaven.equipment.ProfessionKitCatalog;
import com.hexvane.aetherhaven.equipment.ProfessionKitService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
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

/**
 * What a press on a storage page does when the town kept working between the moment the page was drawn and the
 * moment the press arrived. Couriers, crafts and harvests change storage every few seconds, so "something changed"
 * is never a reason to refuse. A press is honoured when what it names can still be done, and refused only when it
 * truly cannot, with the reason, and with nothing lost or made twice. Test items stack to 100.
 */
@Tag("town")
class WarehousePressTest {
    private static final TownWarehouse.Page GENERAL = TownWarehouse.Page.GENERAL, OUTPUT = TownWarehouse.Page.OUTPUT;
    private static final TownWarehouse.Amount STACK = TownWarehouse.Amount.STACK, ONE = TownWarehouse.Amount.ONE;
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
    private static int total(SimpleItemContainer bag, String id) {
        int total = 0;
        for (short i = 0; i < bag.getCapacity(); i++) {
            var stack = bag.getItemStack(i);
            if (!ItemStack.isEmpty(stack) && id.equals(stack.getItemId())) total += stack.getQuantity();
        }
        return total;
    }
    /** What a courier or a finished craft does to storage while a page is open. */
    private static void townKeepsWorking(TownWarehouse town) {
        long before = town.revision();
        assertTrue(town.acceptGuardSpoils(stock("spoils", 3)));
        assertTrue(town.acceptCompletedOutput(stock("crafted", 1)));
        assertTrue(town.revision() > before, "Background work moves the revision the old check compared");
    }
    private static void supply(TownWarehouse town, ItemStack... stacks) {
        var bag = bag(stacks);
        for (short i = 0; i < stacks.length; i++) assertEquals(stacks[i].getQuantity(), town.deposit(GENERAL, bag, i, stacks[i], STACK).moved());
    }

    // ------------------------------------------------------------------ deposits

    @Test void aDepositIsHonouredAlthoughStorageChangedSinceThePageWasDrawn() {
        var town = new TownWarehouse();
        var bag = bag(stock("ore", 40), null);
        var drawn = bag.getItemStack((short) 0);
        townKeepsWorking(town);
        var result = town.deposit(GENERAL, bag, (short) 0, drawn, STACK);
        assertEquals(40, result.moved());
        assertNull(result.refusal());
        assertNull(result.error());
        assertEquals(40, town.count(GENERAL, "ore"));
        assertEquals(0, total(bag, "ore"), "Nothing is left behind and nothing is made twice");
    }

    @Test void aDepositStoresWhatTheSlotHoldsNowWhenTheStackGrewOrShrank() {
        var town = new TownWarehouse();
        var bag = bag(stock("ore", 40));
        var drawn = bag.getItemStack((short) 0);
        // The player picked up more of it, or dropped some, after the page was drawn.
        bag.setItemStackForSlot((short) 0, stock("ore", 55), false);
        assertEquals(55, town.deposit(GENERAL, bag, (short) 0, drawn, STACK).moved());
        assertEquals(55, town.count(GENERAL, "ore"));
        bag.setItemStackForSlot((short) 0, stock("ore", 7), false);
        assertEquals(1, town.deposit(GENERAL, bag, (short) 0, drawn, ONE).moved());
        assertEquals(6, total(bag, "ore"));
        assertEquals(56, town.count(GENERAL, "ore"));
    }

    @Test void aDepositNeverStoresAnotherItemThatNowSitsInThatSlot() {
        var town = new TownWarehouse();
        var bag = bag(stock("ore", 40), stock("gem", 2));
        var drawn = bag.getItemStack((short) 0);
        // The inventory moved under the player: the slot the press names now holds something else.
        bag.setItemStackForSlot((short) 0, stock("sword", 1), false);
        long before = town.revision();
        var result = town.deposit(GENERAL, bag, (short) 0, drawn, STACK);
        assertEquals(0, result.moved());
        assertEquals(TownWarehouse.Refusal.GONE, result.refusal());
        assertEquals(1, total(bag, "sword"), "The sword stays with the player");
        assertEquals(0, town.count(GENERAL, "sword"));
        assertEquals(before, town.revision(), "A refused press changes nothing");
        // The same item with other properties is another item too.
        var marked = new ItemStack("ore", 40, BsonDocument.parse("{special:true}"));
        bag.setItemStackForSlot((short) 0, marked, false);
        assertEquals(TownWarehouse.Refusal.GONE, town.deposit(GENERAL, bag, (short) 0, drawn, STACK).refusal());
        assertEquals(marked, bag.getItemStack((short) 0));
        // An emptied slot has nothing to store.
        bag.setItemStackForSlot((short) 0, null, false);
        assertEquals(TownWarehouse.Refusal.GONE, town.deposit(GENERAL, bag, (short) 0, drawn, STACK).refusal());
    }

    @Test void aDepositIsRefusedOnlyForRoomWhenStorageIsFullAndKeepsEveryItem() {
        var town = new TownWarehouse();
        for (int i = 0; i < TownWarehouse.BASE_GENERAL_SLOTS; i++) supply(town, stock("filler" + i, 100));
        var bag = bag(stock("ore", 40));
        var result = town.deposit(GENERAL, bag, (short) 0, bag.getItemStack((short) 0), STACK);
        assertEquals(0, result.moved());
        assertEquals(TownWarehouse.Refusal.NO_ROOM, result.refusal());
        assertEquals(40, total(bag, "ore"));
        // Someone collects a stack, so there is room. The press drawn while it was full now works.
        var other = bag((ItemStack) null);
        assertEquals(100, town.withdraw(GENERAL, other, (short) 0, stock("filler0", 100), STACK).moved());
        assertEquals(40, town.deposit(GENERAL, bag, (short) 0, stock("ore", 40), STACK).moved());
        assertEquals(40, town.count(GENERAL, "ore"));
    }

    @Test void craftedGoodsNeverTakeDeposits() {
        var town = new TownWarehouse();
        var bag = bag(stock("ore", 40));
        assertEquals(TownWarehouse.Refusal.OUTPUT_ONLY, town.deposit(OUTPUT, bag, (short) 0, stock("ore", 40), STACK).refusal());
        assertEquals(40, total(bag, "ore"));
    }

    // ------------------------------------------------------------------ withdrawals

    @Test void aWithdrawalIsHonouredAlthoughStorageChangedSinceThePageWasDrawn() {
        var town = new TownWarehouse();
        supply(town, stock("ore", 60));
        var drawn = town.stack(GENERAL, (short) 0);
        townKeepsWorking(town);
        // A courier also added to the very pile the player pointed at.
        assertTrue(town.acceptGuardSpoils(stock("ore", 15)));
        var bag = bag((ItemStack) null, null);
        var result = town.withdraw(GENERAL, bag, (short) 0, drawn, STACK);
        assertEquals(75, result.moved(), "One stack of what is there now");
        assertNull(result.refusal());
        assertEquals(75, total(bag, "ore"));
        assertEquals(0, town.count(GENERAL, "ore"));
    }

    @Test void aWithdrawalTakesTheNamedItemFromWhereItIsNowAndNeverWhatReplacedIt() {
        var town = new TownWarehouse();
        supply(town, stock("ore", 10), stock("gem", 5));
        var drawnOre = town.stack(GENERAL, (short) 0);
        // The ore is carried off and wood is put in its slot; more ore arrives in another slot.
        var courier = bag((ItemStack) null);
        assertEquals(10, town.withdraw(GENERAL, courier, (short) 0, drawnOre, STACK).moved());
        supply(town, stock("wood", 8), stock("ore", 4));
        assertEquals("wood", town.stack(GENERAL, (short) 0).getItemId());
        short now = town.locate(GENERAL, (short) 0, drawnOre);
        assertNotEquals(0, now);
        assertEquals("ore", town.stack(GENERAL, now).getItemId());
        var bag = bag((ItemStack) null, null);
        var result = town.withdraw(GENERAL, bag, (short) 0, drawnOre, STACK);
        assertEquals(4, result.moved());
        assertEquals(4, total(bag, "ore"));
        assertEquals(0, total(bag, "wood"), "The row that moved into its place is never taken");
        assertEquals(8, town.count(GENERAL, "wood"));
        assertEquals(5, town.count(GENERAL, "gem"));
    }

    @Test void aWithdrawalIsRefusedWithItsReasonWhenTheItemIsGoneReservedOrHasNoRoom() {
        var town = new TownWarehouse();
        supply(town, stock("ore", 10), stock("gem", 5));
        var ore = town.stack(GENERAL, (short) 0);
        var gem = town.stack(GENERAL, (short) 1);
        // Gone: someone took the last of it.
        var other = bag((ItemStack) null);
        assertEquals(10, town.withdraw(GENERAL, other, (short) 0, ore, STACK).moved());
        var bag = bag((ItemStack) null);
        long before = town.revision();
        var gone = town.withdraw(GENERAL, bag, (short) 0, ore, STACK);
        assertEquals(0, gone.moved());
        assertEquals(TownWarehouse.Refusal.GONE, gone.refusal());
        // Reserved: a courier has set every gem aside.
        assertNotNull(town.reserve(UUID.randomUUID(), UUID.randomUUID(), "recipe",
            List.of(new MaterialQuantity("gem", null, null, 5, null)), List.of(stock("ring", 1))));
        long reservedAt = town.revision();
        var reserved = town.withdraw(GENERAL, bag, (short) 1, gem, STACK);
        assertEquals(0, reserved.moved());
        assertEquals(TownWarehouse.Refusal.RESERVED, reserved.refusal());
        assertEquals(5, town.count(GENERAL, "gem"));
        assertEquals(reservedAt, town.revision());
        // No room: the player's inventory is full of something else.
        supply(town, stock("wood", 8));
        var full = bag(stock("stone", 100));
        var noRoom = town.withdraw(GENERAL, full, (short) 2, stock("wood", 8), STACK);
        assertEquals(0, noRoom.moved());
        assertEquals(TownWarehouse.Refusal.NO_ROOM, noRoom.refusal());
        assertEquals(8, town.count(GENERAL, "wood"));
        assertEquals(100, total(full, "stone"));
        assertTrue(before <= town.revision());
    }

    @Test void onlyTheFreePartOfAPartlyReservedPileIsHandedOver() {
        var town = new TownWarehouse();
        supply(town, stock("ore", 90));
        var drawn = town.stack(GENERAL, (short) 0);
        // The reservation is made after the page was drawn.
        assertNotNull(town.reserve(UUID.randomUUID(), UUID.randomUUID(), "recipe",
            List.of(new MaterialQuantity("ore", null, null, 40, null)), List.of(stock("bar", 1))));
        var bag = bag((ItemStack) null);
        assertEquals(50, town.withdraw(GENERAL, bag, (short) 0, drawn, STACK).moved());
        assertEquals(40, town.count(GENERAL, "ore"));
        assertEquals(40, town.reserved(GENERAL, (short) 0), "The courier's share is untouched");
        assertEquals(TownWarehouse.Refusal.RESERVED, town.withdraw(GENERAL, bag, (short) 0, drawn, ONE).refusal());
    }

    @Test void pressesOverABusyStoreNeverLoseOrMakeItems() {
        var town = new TownWarehouse();
        var bag = bag(stock("ore", 100), stock("ore", 37), null, null);
        int everything = 137;
        var random = new Random(7);
        for (int press = 0; press < 400; press++) {
            // The page is drawn...
            short bagSlot = (short) random.nextInt(bag.getCapacity());
            var carried = bag.getItemStack(bagSlot);
            short storeSlot = (short) random.nextInt(4);
            var stored = town.stack(GENERAL, storeSlot);
            // ...the town works...
            if (random.nextBoolean()) town.acceptGuardSpoils(stock("spoils", 1));
            if (random.nextInt(4) == 0) town.acceptCompletedOutput(stock("crafted", 1));
            // ...and the press arrives.
            if (random.nextBoolean()) { if (!ItemStack.isEmpty(carried)) town.deposit(GENERAL, bag, bagSlot, carried, random.nextBoolean() ? STACK : ONE); }
            else if (!ItemStack.isEmpty(stored) && "ore".equals(stored.getItemId())) town.withdraw(GENERAL, bag, storeSlot, stored, random.nextBoolean() ? STACK : ONE);
            assertEquals(everything, total(bag, "ore") + town.count(GENERAL, "ore"), "After press " + press);
        }
        assertEquals(0, total(bag, "spoils"), "A press never hands over an item it did not name");
    }

    @Test void storingMatchingItemsWorksAlthoughStorageChangedSinceThePageWasDrawn() {
        var town = new TownWarehouse();
        supply(town, stock("ore", 5));
        var bag = bag(stock("sword", 1), stock("ore", 30), stock("wood", 5));
        townKeepsWorking(town);
        var result = town.depositMatching(bag, 1, bag.getCapacity());
        assertEquals(30, result.moved());
        assertTrue(result.matched());
        assertEquals(35, town.count(GENERAL, "ore"));
        assertEquals(5, total(bag, "wood"), "Wood is not stored here, so it stays");
        assertEquals(1, total(bag, "sword"), "The hotbar is left alone");
    }

    // ------------------------------------------------------------------ what the player is told

    @Test void aRefusalNamesTheItemAndThePlaceAndUsesNoDashesAsPauses() {
        for (var why : TownWarehouse.Refusal.values()) for (boolean deposit : new boolean[]{true, false}) {
            String text = WarehousePage.refusalText(why, "Iron Ingot", deposit, "General storage");
            assertTrue(text.contains("Iron Ingot"), text);
            assertFalse(text.contains(" - ") || text.contains("—") || text.contains("–"), text);
            assertFalse(text.toLowerCase(Locale.ROOT).contains("changed"), "Nothing blames a change: " + text);
            assertFalse(text.toLowerCase(Locale.ROOT).contains("try again"), text);
        }
        assertEquals("No Iron Ingot is left in General storage. Nothing was moved.",
            WarehousePage.refusalText(TownWarehouse.Refusal.GONE, "Iron Ingot", false, "General storage"));
        assertEquals("You no longer carry Iron Ingot in that slot. Nothing was moved.",
            WarehousePage.refusalText(TownWarehouse.Refusal.GONE, "Iron Ingot", true, "Your inventory"));
        assertEquals("Every Iron Ingot in Crafting output is set aside for a workplace delivery. Nothing was moved.",
            WarehousePage.refusalText(TownWarehouse.Refusal.RESERVED, "Iron Ingot", false, "Crafting output"));
        assertEquals("Your inventory has no room for Iron Ingot. Nothing was moved.",
            WarehousePage.refusalText(TownWarehouse.Refusal.NO_ROOM, "Iron Ingot", false, "General storage"));
        assertEquals("General storage has no room for Iron Ingot. Nothing was moved.",
            WarehousePage.refusalText(TownWarehouse.Refusal.NO_ROOM, "Iron Ingot", true, "Your inventory"));
    }

    // ------------------------------------------------------------------ upgrades

    @Test void anUpgradeIsBoughtAlthoughStockMovedAndOneOfferIsNeverBoughtTwice() {
        var town = new TownWarehouse();
        int owned = town.upgrades();
        townKeepsWorking(town);
        assertTrue(town.upgrade(owned));
        assertFalse(town.upgrade(owned), "A second press on the same offer buys nothing");
        assertEquals(owned + 1, town.upgrades());
        int shelves = town.stackUpgrades();
        townKeepsWorking(town);
        assertTrue(town.upgradeStacks(shelves));
        assertFalse(town.upgradeStacks(shelves));
        var smelting = new SmeltingOutputStore();
        assertTrue(smelting.reserve(UUID.randomUUID(), List.of(stock("ingot", 2))), "A furnace load moves the store's revision");
        assertTrue(smelting.upgrade(0));
        assertFalse(smelting.upgrade(0));
        assertTrue(smelting.upgrade(1));
    }

    // ------------------------------------------------------------------ smelted goods

    @Test void smeltedGoodsAreCollectedAlthoughTheFurnaceAddedToThePile() {
        var store = new SmeltingOutputStore();
        var job = UUID.randomUUID(); var origin = UUID.randomUUID(); var point = new WarehouseBatch.Point(10, 123, 20);
        assertTrue(store.reserve(job, List.of(stock("ingot", 6))));
        var furnace = bag(stock("ingot", 2), null, null, null);
        assertEquals(2, store.collect(job, origin, point, furnace));
        var drawn = store.origin(origin).stack((short) 0);
        // The furnace finishes more bars after the page was drawn.
        furnace.setItemStackForSlot((short) 0, stock("ingot", 3), false);
        assertEquals(3, store.collect(job, origin, point, furnace));
        assertEquals(5, store.origin(origin).stack((short) 0).getQuantity());
        var bag = bag((ItemStack) null);
        assertEquals(5, store.withdraw(origin, (short) 0, drawn, 5, bag));
        assertEquals(5, total(bag, "ingot"));
        assertEquals(0, store.count("ingot"));
        // Gone now: nothing is handed over and nothing is made.
        assertEquals(-1, store.locate(origin, (short) 0, drawn));
        assertEquals(0, store.withdraw(origin, (short) 0, drawn, 1, bag));
        assertEquals(5, total(bag, "ingot"));
        // A full inventory keeps the goods at the furnace.
        furnace.setItemStackForSlot((short) 0, stock("ingot", 1), false);
        assertEquals(1, store.collect(job, origin, point, furnace));
        var full = bag(stock("stone", 100));
        assertEquals(0, store.withdraw(origin, (short) 0, drawn, 1, full));
        assertEquals(1, store.count("ingot"));
    }

    // ------------------------------------------------------------------ kits

    private static ProfessionKitCatalog kits() {
        return new ProfessionKitCatalog(List.of(new ProfessionKit("courierKit", "Courier satchel", "logistics", 0, 1),
            new ProfessionKit("smithKit", "Smith tools", "blacksmithing", .15, 0)));
    }
    private static GuardKitCatalog guardKits() {
        return new GuardKitCatalog(List.of(new GuardKit("ironKit", "Iron knight", "guard_knight", "guard_knight_iron", "Strength"),
            new GuardKit("thoriumKit", "Thorium knight", "guard_knight", "guard_knight_thorium", "Strength")));
    }

    @Test void aKitIsEquippedFromStorageAlthoughStorageChangedAndTheWornKitComesBackInItsPlace() {
        var town = new TownWarehouse(); var progress = new TownsfolkProgress();
        var smith = stock("smithKit", 1);
        var courier = new ItemStack("courierKit", 1, BsonDocument.parse("{signed:true}"));
        progress.setProfessionKitData(TownsfolkProgress.encodeProfessionKit(courier));
        assertTrue(town.acceptCompletedOutput(smith));
        townKeepsWorking(town);
        assertNull(town.equipProfessionKit(OUTPUT, (short) 0, smith, progress, kits()));
        assertEquals(smith, progress.professionKit());
        assertEquals(courier, town.stack(OUTPUT, (short) 0), "The kit that was worn is in storage, with its properties");
        // The same press again: that kit is no longer in storage, so nothing happens and the reason is given.
        long before = town.revision();
        assertEquals(ProfessionKitService.TAKEN, town.equipProfessionKit(OUTPUT, (short) 0, smith, progress, kits()));
        assertEquals(smith, progress.professionKit());
        assertEquals(courier, town.stack(OUTPUT, (short) 0));
        assertEquals(before, town.revision());
    }

    @Test void aKitThatMovedToAnotherSlotIsStillTheOneEquippedAndAnotherKitInItsOldSlotIsNot() {
        var progress = new TownsfolkProgress();
        var smith = new ItemStack("smithKit", 1, BsonDocument.parse("{maker:'Ada'}"));
        var plain = stock("smithKit", 1);
        // Drawn in slot 0. The player then moved it to slot 2 and put a plain kit of the same kind in slot 0.
        var bag = bag(plain, null, smith);
        assertEquals(2, ProfessionKitService.locate(bag, (short) 0, smith));
        assertNull(ProfessionKitService.equip(progress, kits(), bag, (short) 0, smith));
        assertEquals(smith, progress.professionKit());
        assertEquals(plain, bag.getItemStack((short) 0), "The kit that took its old slot is untouched");
        assertTrue(ItemStack.isEmpty(bag.getItemStack((short) 2)));
    }

    @Test void theResidentsKitChangingSinceThePageWasDrawnDoesNotRefuseTheSwapOrLoseAKit() {
        var progress = new TownsfolkProgress();
        var first = stock("courierKit", 1); var second = new ItemStack("courierKit", 1, BsonDocument.parse("{signed:true}"));
        var smith = stock("smithKit", 1);
        progress.setProfessionKitData(TownsfolkProgress.encodeProfessionKit(first));
        // The page drew "first" on the resident. Another player then gave them "second".
        var otherBag = bag(second);
        assertNull(ProfessionKitService.equip(progress, kits(), otherBag, (short) 0, second));
        assertEquals(first, otherBag.getItemStack((short) 0));
        var bag = bag(smith);
        assertNull(ProfessionKitService.equip(progress, kits(), bag, (short) 0, smith));
        assertEquals(smith, progress.professionKit());
        assertEquals(second, bag.getItemStack((short) 0), "What was worn at that moment comes back, so no kit is lost or copied");
        // Taking the kit off returns whatever is worn now, and says so when nothing is.
        var empty = bag((ItemStack) null);
        assertNull(ProfessionKitService.unequip(progress, empty));
        assertEquals(smith, empty.getItemStack((short) 0));
        assertNull(progress.professionKit());
        assertEquals(ProfessionKitService.NONE_WORN, ProfessionKitService.unequip(progress, empty));
        assertEquals(smith, empty.getItemStack((short) 0));
    }

    @Test void aGuardKitFollowsTheSameRuleAndAReservedKitStaysWhereItIs() {
        var town = new TownWarehouse(); var progress = new TownsfolkProgress();
        var iron = stock("ironKit", 1);
        supply(town, iron);
        var batch = town.reserve(UUID.randomUUID(), UUID.randomUUID(), "recipe",
            List.of(new MaterialQuantity("ironKit", null, null, 1, null)), List.of(stock("crate", 1)));
        assertNotNull(batch);
        assertEquals("That kit is reserved for a workplace.", town.equipGuardKit(GENERAL, (short) 0, iron, progress, guardKits(), "guard_knight"));
        assertNull(progress.guardKit());
        town.cancel(batch.id());
        townKeepsWorking(town);
        assertNull(town.equipGuardKit(GENERAL, (short) 0, iron, progress, guardKits(), "guard_knight"));
        assertEquals(iron, progress.guardKit());
        assertEquals(0, town.count(GENERAL, "ironKit"));
        assertEquals(ProfessionKitService.TAKEN, town.equipGuardKit(GENERAL, (short) 0, iron, progress, guardKits(), "guard_knight"));
        var bag = bag(stock("thoriumKit", 1));
        assertNotNull(GuardKitService.equip(progress, guardKits(), "guard_archer", bag, (short) 0, stock("thoriumKit", 1)), "Another class cannot wear it");
        assertNull(GuardKitService.equip(progress, guardKits(), "guard_knight", bag, (short) 0, stock("thoriumKit", 1)));
        assertEquals(iron, bag.getItemStack((short) 0));
    }
}
