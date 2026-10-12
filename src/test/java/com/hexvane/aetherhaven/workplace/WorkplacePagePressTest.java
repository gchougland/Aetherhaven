package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.ui.PageDelta;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Presses on workplace pages name what they act on and are judged by the town as it is at that moment. A list that
 * moved under the player never makes a press land on another row.
 */
@Tag("town")
class WorkplacePagePressTest {
    private static Object previousStore;

    /** Item stacks look their item up when they are made, so the tests need an item store, as the warehouse tests do. */
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

    private static Map<Short, ItemStack> store(Object... slotThenStack) {
        var held = new HashMap<Short, ItemStack>();
        for (int i = 0; i < slotThenStack.length; i += 2) held.put(((Integer) slotThenStack[i]).shortValue(), (ItemStack) slotThenStack[i + 1]);
        return held;
    }

    @Test void collectingFinishedGoodsFollowsTheItemNotTheSlotItWasDrawnIn() {
        // Drawn: planks in slot 3. Since then a courier emptied slot 3, bread took its place and planks sit in slot 7.
        var crafted = store(3, new ItemStack("bread", 4), 7, new ItemStack("plank", 20));
        assertEquals(7, WorkplaceOrdersPage.heldSlot(40, (short) 3, "plank", crafted::get));
        // Still where it was drawn: that slot is used.
        assertEquals(3, WorkplaceOrdersPage.heldSlot(40, (short) 3, "bread", crafted::get));
        // The same item in two slots: the drawn slot wins while it still holds it.
        crafted.put((short) 1, new ItemStack("plank", 5));
        assertEquals(7, WorkplaceOrdersPage.heldSlot(40, (short) 7, "plank", crafted::get));
        assertEquals(1, WorkplaceOrdersPage.heldSlot(40, (short) 3, "plank", crafted::get));
        // Gone: nothing is collected, and the bread in the old slot is never handed over instead.
        assertEquals(-1, WorkplaceOrdersPage.heldSlot(40, (short) 3, "nails", crafted::get));
        assertEquals(-1, WorkplaceOrdersPage.heldSlot(40, (short) 3, null, crafted::get));
        // A slot number from an older, larger store is simply not there.
        assertEquals(7, WorkplaceOrdersPage.heldSlot(8, (short) 39, "plank", store(7, new ItemStack("plank", 1))::get));
    }

    @Test void theCourierFoodButtonCarriesTheAmountItMovesTo() {
        assertEquals(List.of(10, 25, 50, 75, 0), List.of(0, 10, 25, 50, 75).stream().map(RancherHerdPage::nextCourierTarget).toList());
        var herd = new com.hexvane.aetherhaven.production.HerdProductionState();
        int drawnTarget = RancherHerdPage.nextCourierTarget(herd.courierFeedTarget());
        assertTrue(herd.configureCourierFeed(drawnTarget));
        // The same press seen twice: it asked for that amount, which is already the setting, so nothing skips ahead.
        assertFalse(herd.configureCourierFeed(drawnTarget));
        assertEquals(drawnTarget, herd.courierFeedTarget());
    }

    @Test void anUpgradeTierIsRefusedOnlyWhenItIsAlreadyOwnedOrOutOfTurn() {
        assertEquals("This tier is already unlocked. Nothing was spent.", WorkplaceUpgradesPage.tierIssue(2, 2));
        assertEquals("This tier is already unlocked. Nothing was spent.", WorkplaceUpgradesPage.tierIssue(3, 1));
        assertEquals("Unlock the earlier tier first.", WorkplaceUpgradesPage.tierIssue(0, 2));
    }

    @Test void theCareClockAndProgressBarsMoveInStepsSoTimedPagesHaveLittleToSend() {
        // A day of care counted down a game minute at a time, as the food page's timer sees it.
        int changes = 0;
        long last = -1;
        for (long minutes = WorkplaceFood.NEGLECT_MINUTES; minutes >= 0; minutes--) {
            long shown = FoodSitePage.shownCare(minutes);
            if (shown != last) changes++;
            last = shown;
        }
        assertEquals(WorkplaceFood.NEGLECT_MINUTES / FoodSitePage.CARE_STEP_MINUTES + 1, changes, "One change every ten game minutes, not every minute");
        assertEquals(FoodSitePage.CARE_STEP_MINUTES, FoodSitePage.shownCare(1), "Care that is left never reads as none");
        assertEquals(0, FoodSitePage.shownCare(0));
        assertEquals(200, FoodSitePage.shownCare(209));
        // A station upgrade looked at four times a second for 30 seconds moves its bar twenty times.
        changes = 0;
        float bar = -1;
        for (int tick = 0; tick <= 120; tick++) {
            float shown = PageDelta.stepped(tick / 120.0, WorkplaceStationPage.PROGRESS_STEPS);
            if (shown != bar) changes++;
            bar = shown;
        }
        assertEquals(WorkplaceStationPage.PROGRESS_STEPS + 1, changes);
        assertEquals(0f, PageDelta.stepped(-3, 20));
        assertEquals(1f, PageDelta.stepped(4, 20));
        assertEquals(0f, PageDelta.stepped(Double.NaN, 20));
        assertEquals(.5f, PageDelta.stepped(.5, 10));
        assertEquals(.9f, PageDelta.stepped(.999, 10));
    }

    @Test void noWorkplacePageRefusesAPressBecauseSomethingChanged() throws Exception {
        var root = Path.of("src/main/java/com/hexvane/aetherhaven");
        for (String file : List.of("workplace/WorkplaceOrders.java", "workplace/WorkplaceOrdersPage.java", "workplace/BuildingOrders.java",
            "workplace/BuildingProjectsPage.java", "workplace/RancherHerdPage.java", "production/HerdProductionState.java",
            "workplace/WorkplaceUpgradesPage.java", "warehouse/TownWarehouse.java", "warehouse/WarehousePage.java",
            "warehouse/SmeltingOutputStore.java", "equipment/ProfessionKitService.java", "equipment/GuardKitService.java",
            "ui/ProfessionKitPage.java", "ui/GuardKitPage.java", "mastery/CoreMasteryPage.java", "clinic/ClinicPage.java", "hunter/HuntPage.java")) {
            String source = Files.readString(root.resolve(file));
            for (String old : List.of("The task list changed", "The project queue changed", "Storage changed", "The barn changed", "The pen changed",
                "The animal list changed", "Equipment changed", "Mastery or equipment changed", "This upgrade has changed", "The medicine supply changed",
                "The run changed", "seenRevision", "expectedRevision", "expected!=revision", "expected != revision"))
                assertFalse(source.contains(old), file + " still says or checks: " + old);
        }
        // The pages bind the state or the row a press asks for.
        String orders = Files.readString(root.resolve("workplace/WorkplaceOrdersPage.java"));
        assertTrue(orders.contains("\"Pause\".equals(action)&&order.paused()?\"Resume\":action"));
        assertTrue(orders.contains("slot+\"|\"+stack.getItemId()"));
        String projects = Files.readString(root.resolve("workplace/BuildingProjectsPage.java"));
        assertTrue(projects.contains("plan.paused()?\"Resume\":\"Pause\""));
        String barn = Files.readString(root.resolve("workplace/RancherHerdPage.java"));
        assertTrue(barn.contains("i+\"|\"+(animal==null?\"\":animal.id())"));
    }
}
