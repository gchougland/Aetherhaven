package com.hexvane.aetherhaven.inventory;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.*;
import org.junit.jupiter.api.*;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ItemStackDataTest {
    private static Object previousStore;
    private static class Items extends AssetStore<String,Item,DefaultAssetMap<String,Item>> {
        private final EventBus events=new EventBus(false);
        Items(Config config){super(config);}
        @Override protected IEventBus getEventBus(){return events;}
        @Override public void addFileMonitor(String pack,Path path){}
        @Override public void removeFileMonitor(Path path){}
        @Override protected void handleRemoveOrUpdate(Set<String> removed,Map<String,Item> updated,AssetUpdateQuery query){}
        private static class Config extends Builder<String,Item,DefaultAssetMap<String,Item>,Config> {
            Config(){super(String.class,Item.class,new DefaultAssetMap<>());setPath("Items");setCodec(Item.CODEC);setKeyFunction(Item::getId);}
            @Override public Items build(){return new Items(this);}
        }
    }
    @BeforeAll static void assets() throws Exception {
        var field=Item.class.getDeclaredField("ASSET_STORE");field.setAccessible(true);previousStore=field.get(null);field.set(null,new Items.Config().build());
    }
    @AfterAll static void restore() throws Exception {
        var field=Item.class.getDeclaredField("ASSET_STORE");field.setAccessible(true);field.set(null,previousStore);
    }


    @Test void conversionRetainsNativeFieldsAndDoesNotShareMutableMetadata() {
        var original = new ItemStack("old", 5, 7, 10, 2,
            new BsonDocument("nested", new BsonDocument("value", new BsonString("kept"))));
        var converted = ItemStackData.withItemId(original, "new");
        assertEquals("new", converted.getItemId());
        assertEquals(5, converted.getQuantity());
        assertEquals(7, converted.getDurability());
        assertEquals(10, converted.getMaxDurability());
        assertEquals(2, converted.getQualityIndex());
        assertEquals(ItemStackData.metadata(original), ItemStackData.metadata(converted));
        ItemStackData.metadata(converted).getDocument("nested").clear();
        assertEquals("kept", ItemStackData.metadata(converted).getDocument("nested").getString("value").getValue());
        assertEquals("old", original.getItemId());
    }

    @Test void plainItemKeepsAbsentMetadataAndNativeStackability() {
        var converted = ItemStackData.withItemId(new ItemStack("old", 1), "new");
        assertNull(ItemStackData.metadata(converted));
        assertTrue(converted.isStackableWith(new ItemStack("new", 1)));
    }
}
