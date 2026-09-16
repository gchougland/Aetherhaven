package com.hexvane.aetherhaven.autonomy;
import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
@Tag("autonomy")
class VillagerTemporaryItemsTest {
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


    @Test void ordinaryItemsRemainRealAndTemporaryOwnershipSurvivesSerialization() {
        var real = new ItemStack("Tool_Hammer_Iron", 1);
        var temporary = VillagerLifeProps.temporary("Tool_Hammer_Iron", (byte) 3);
        assertFalse(VillagerLifeProps.isTemporary(real));
        assertTrue(VillagerLifeProps.isTemporary(temporary));
        assertEquals(real.getItemId(), temporary.getItemId());
        var extra = new com.hypixel.hytale.codec.ExtraInfo();
        var reloaded = ItemStack.CODEC.decode(ItemStack.CODEC.encode(temporary, extra), extra);
        assertTrue(VillagerLifeProps.isTemporary(reloaded));
        assertEquals(3, VillagerLifeProps.previousSlot(reloaded, (byte) -1));
        assertFalse(VillagerLifeProps.isTemporary(ItemStack.EMPTY));
        assertTrue(VillagerLifeProps.isTemporary(new ItemStack("Aetherhaven_Life_Prop_Salad", 1)));
    }
    @Test void nativeSaladCanBeEquippedWithoutChangingTheFoodAssetOrUtilityFilter() {
        var utility = new InventoryComponent.Utility((short) 2);
        var salad = VillagerLifeProps.temporary(VillagerLifeProps.SALAD, (byte) -1);
        assertFalse(utility.getInventory().setItemStackForSlot((short) 0, salad).succeeded());
        assertTrue(utility.getInventory().setItemStackForSlot((short) 0, salad, false).succeeded());
        assertEquals(VillagerLifeProps.SALAD, utility.getInventory().getItemStack((short) 0).getItemId());
        assertFalse(utility.getInventory().setItemStackForSlot((short) 1, new ItemStack(VillagerLifeProps.SALAD, 1)).succeeded());
    }
    @Test void emptyOrFullNpcUtilityInventoriesGetASpareSlotWithoutLosingEquipment() {
        var utility = new InventoryComponent.Utility((short) 0);
        assertEquals(0, VillagerLifeProps.reserveUtilitySlot(utility));
        var original = new ItemStack("Tool_Hammer_Iron", 1);
        utility.getInventory().setItemStackForSlot((short) 0, original, false);
        assertEquals(1, VillagerLifeProps.reserveUtilitySlot(utility));
        assertEquals(original, utility.getInventory().getItemStack((short) 0));
        assertEquals(1, VillagerLifeProps.reserveUtilitySlot(utility));
        assertEquals(2, utility.getInventory().getCapacity());
    }
}
