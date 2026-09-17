package com.hexvane.aetherhaven.economy;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.*;

@Tag("economy")
class ItemCoinAccountTest {
    private static ItemStore itemStore;
    private static Object previousItemStore;

    // Supply an asset map so real ItemStacks and inventory transactions work without starting a server.
    private static class ItemStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        ItemStore(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() {
                super(String.class, Item.class, new DefaultAssetMap<>());
                setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId);
            }
            @Override public ItemStore build() { return new ItemStore(this); }
        }
    }

    @BeforeAll static void setUpAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousItemStore = field.get(null);
        itemStore = new ItemStore.Config().build();
        // Avoid changing the global registry used by other test fixtures.
        field.set(null, itemStore);
    }

    @AfterAll static void restoreAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousItemStore);
    }

    private static SimpleItemContainer slots(int... stacks) {
        var container = new SimpleItemContainer((short) 4);
        for (short i = 0; i < stacks.length; i++) {
            container.setItemStackForSlot(i, new ItemStack(ItemCoinEconomy.coinItemId(), stacks[i]));
        }
        return container;
    }

    private static ItemCoinEconomy.ItemCoinAccount account(SimpleItemContainer container) {
        return new ItemCoinEconomy.ItemCoinAccount(new CombinedItemContainer(container));
    }

    private static long coins(SimpleItemContainer container) {
        return container.countItemStacks(s -> ItemCoinEconomy.coinItemId().equals(s.getItemId()));
    }

    @Test void balanceCountsEveryStack() {
        assertEquals(27, account(slots(12, 15)).balance());
        assertEquals(0, account(slots()).balance());
    }

    @Test void withdrawTakesAcrossStacks() {
        var container = slots(5, 10);
        assertTrue(account(container).withdraw(12));
        assertEquals(3, coins(container));
    }

    @Test void withdrawRefusesWithoutMovingAnything() {
        var container = slots(5, 10);
        assertFalse(account(container).withdraw(16));
        assertEquals(15, coins(container));
    }

    @Test void withdrawingNothingSucceeds() {
        assertTrue(account(slots()).withdraw(0));
    }

    @Test void depositAddsCoins() {
        var container = slots(5);
        assertTrue(account(container).deposit(7));
        assertEquals(12, coins(container));
    }

    @Test void depositRefusesWhenFull() {
        var container = new SimpleItemContainer((short) 1);
        container.setItemStackForSlot((short) 0, new ItemStack("Tool_Hammer_Iron", 1));
        assertFalse(account(container).deposit(1));
        assertEquals(0, coins(container));
    }

    @Test void splitCutsAtMaxStack() {
        // No asset registered here, so the max stack is unbounded: one stack.
        List<ItemStack> stacks = ItemCoinEconomy.split(ItemCoinEconomy.coinItemId(), 25_000);
        assertEquals(1, stacks.size());
        assertEquals(25_000, stacks.get(0).getQuantity());
    }

    @Test void lootItemsIsEmptyForAnUnknownItemOrNothing() {
        assertTrue(ItemCoinEconomy.INSTANCE.lootItems("Not_An_Item", 5).isEmpty());
        assertTrue(ItemCoinEconomy.INSTANCE.lootItems(ItemCoinEconomy.coinItemId(), 0).isEmpty());
    }
}
