package com.hexvane.aetherhaven.jewelry;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.economy.ItemCoinEconomy;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.*;

@Tag("economy")
class LootChestBonusApplierTest {
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
        field.set(null, itemStore);
    }

    @AfterAll static void restoreAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousItemStore);
    }

    private static int piles(SimpleItemContainer inv, String itemId) {
        int piles = 0;
        for (short slot = 0; slot < inv.getCapacity(); slot++) {
            ItemStack stack = inv.getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && itemId.equals(stack.getItemId())) {
                piles++;
            }
        }
        return piles;
    }

    @Test void coinsAreSpreadOverSeveralPiles() {
        var inv = new SimpleItemContainer((short) 27);
        String coin = ItemCoinEconomy.coinItemId();
        assertTrue(LootChestBonusApplier.addSplitAcrossRandomSlots(inv, new ItemStack(coin, 7), ThreadLocalRandom.current()));
        assertEquals(7, inv.countItemStacks(s -> coin.equals(s.getItemId())));
        assertTrue(piles(inv, coin) >= 2);
    }

    @Test void aSingleTokenLandsWhole() {
        var inv = new SimpleItemContainer((short) 27);
        assertTrue(LootChestBonusApplier.addSplitAcrossRandomSlots(inv, new ItemStack("Some_Token", 1), ThreadLocalRandom.current()));
        assertEquals(1, inv.countItemStacks(s -> "Some_Token".equals(s.getItemId())));
        assertEquals(1, piles(inv, "Some_Token"));
    }

    @Test void aFullChestTakesNothing() {
        var inv = new SimpleItemContainer((short) 1);
        inv.setItemStackForSlot((short) 0, new ItemStack("Some_Token", 1));
        assertFalse(LootChestBonusApplier.addSplitAcrossRandomSlots(inv, new ItemStack(ItemCoinEconomy.coinItemId(), 3), ThreadLocalRandom.current()));
    }
}
