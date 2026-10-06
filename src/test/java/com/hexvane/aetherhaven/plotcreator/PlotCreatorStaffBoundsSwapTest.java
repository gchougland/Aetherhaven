package com.hexvane.aetherhaven.plotcreator;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("town")
class PlotCreatorStaffBoundsSwapTest {
    private static Object previousItemStore;
    private static class Items extends com.hypixel.hytale.assetstore.AssetStore<String,com.hypixel.hytale.server.core.asset.type.item.config.Item,
            com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,com.hypixel.hytale.server.core.asset.type.item.config.Item>> {
        Items(Config config){super(config);}
        @Override protected com.hypixel.hytale.event.IEventBus getEventBus(){return new com.hypixel.hytale.event.EventBus(false);}
        @Override public void addFileMonitor(String pack,java.nio.file.Path path){}
        @Override public void removeFileMonitor(java.nio.file.Path path){}
        @Override protected void handleRemoveOrUpdate(java.util.Set<String> removed,java.util.Map<String,com.hypixel.hytale.server.core.asset.type.item.config.Item> updated,com.hypixel.hytale.assetstore.AssetUpdateQuery query){}
        private static class Config extends Builder<String,com.hypixel.hytale.server.core.asset.type.item.config.Item,
                com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,com.hypixel.hytale.server.core.asset.type.item.config.Item>,Config> {
            Config(){super(String.class,com.hypixel.hytale.server.core.asset.type.item.config.Item.class,new com.hypixel.hytale.assetstore.map.DefaultAssetMap<>());
                setPath("Items");setCodec(com.hypixel.hytale.server.core.asset.type.item.config.Item.CODEC);setKeyFunction(com.hypixel.hytale.server.core.asset.type.item.config.Item::getId);}
            @Override public Items build(){return new Items(this);}
        }
    }
    @BeforeAll static void assets() throws Exception {
        var field=com.hypixel.hytale.server.core.asset.type.item.config.Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);previousItemStore=field.get(null);field.set(null,new Items.Config().build());
    }
    @AfterAll static void restoreAssets() throws Exception {
        var field=com.hypixel.hytale.server.core.asset.type.item.config.Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);field.set(null,previousItemStore);
    }
    @Test void editorAndCreatorRestoreTheExactOriginalItemAcrossNativeSerialization() {
        for(String id : new String[]{AetherhavenConstants.BUILDING_EDITOR_STAFF_ITEM_ID,AetherhavenConstants.PLOT_CREATOR_STAFF_ITEM_ID}) {
            var original=new ItemStack(id,1,12.0,40.0,org.bson.BsonDocument.parse("{custom: {name: 'Keep my staff'}}"));
            var bounds=PlotCreatorStaffBoundsSwap.toBoundsStaff(original);
            assertTrue(PlotCreatorStaffBoundsSwap.isBoundsStaff(bounds));
            assertEquals(id.equals(AetherhavenConstants.BUILDING_EDITOR_STAFF_ITEM_ID),PlotCreatorStaffBoundsSwap.isEditorBoundsStaff(bounds));
            var saved=ItemStack.CODEC.encode(bounds,new com.hypixel.hytale.codec.ExtraInfo());
            var loaded=ItemStack.CODEC.decode(saved,new com.hypixel.hytale.codec.ExtraInfo());
            assertEquals(original,PlotCreatorStaffBoundsSwap.fromBoundsStaff(loaded));
            assertEquals(original.getMetadata(),PlotCreatorStaffBoundsSwap.fromBoundsStaff(loaded).getMetadata());
        }
    }
    @Test void oldBoundsStaffStillRestoresCreatorAndUnrelatedItemsAreRejected() {
        assertEquals(AetherhavenConstants.PLOT_CREATOR_STAFF_ITEM_ID,PlotCreatorStaffBoundsSwap.fromBoundsStaff(new ItemStack(AetherhavenConstants.PLOT_CREATOR_STAFF_BOUNDS_ITEM_ID,1)).getItemId());
        assertThrows(IllegalArgumentException.class,()->PlotCreatorStaffBoundsSwap.toBoundsStaff(new ItemStack("Tool_Pickaxe_Crude",1)));
    }
}
