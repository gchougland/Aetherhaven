package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Path;
import java.util.*;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;

@Tag("town")
class FetchQuestMaterialsTest {
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
        var f=Item.class.getDeclaredField("ASSET_STORE");f.setAccessible(true);previousStore=f.get(null);f.set(null,new Items.Config().build());
    }
    @AfterAll static void restore() throws Exception {
        var f=Item.class.getDeclaredField("ASSET_STORE");f.setAccessible(true);f.set(null,previousStore);
    }
    private QuestBoardItemRequirement item(String id,int count){return new QuestBoardItemRequirement(id,count);}
    private int count(SimpleItemContainer bag,String id){return bag.countItemStacks(s->id.equals(s.getItemId()));}

    @Test void duplicateRequirementsCannotPassUsingTheSameItemsTwice() {
        var bag=new SimpleItemContainer((short)3);bag.setItemStackForSlot((short)0,new ItemStack("Stone",24),false);
        var request=List.of(item("Stone",24),item("Stone",24));
        assertFalse(FetchQuestMaterials.available(bag,request));assertFalse(FetchQuestMaterials.consume(bag,request));
        assertEquals(24,count(bag,"Stone"));
        bag.setItemStackForSlot((short)1,new ItemStack("Stone",24),false);
        assertTrue(FetchQuestMaterials.available(bag,request));assertTrue(FetchQuestMaterials.consume(bag,request));
        assertEquals(0,count(bag,"Stone"));assertFalse(FetchQuestMaterials.consume(bag,request));
    }
    @Test void missingSecondItemCannotPartiallyConsumeTheFirst() {
        var bag=new SimpleItemContainer((short)3);bag.setItemStackForSlot((short)0,new ItemStack("Copper",8),false);
        assertFalse(FetchQuestMaterials.consume(bag,List.of(item("Copper",4),item("Leather",2))));
        assertEquals(8,count(bag,"Copper"));
    }
    @Test void nativeItemTypeMatchingConsumesMetadataBearingItemsInsteadOfClaimingAFreeTurnIn() {
        var bag=new SimpleItemContainer((short)3);
        bag.setItemStackForSlot((short)0,new ItemStack("Copper",8,BsonDocument.parse("{origin:'crafted'}")),false);
        assertTrue(FetchQuestMaterials.available(bag,List.of(item("Copper",4))));
        assertTrue(FetchQuestMaterials.consume(bag,List.of(item("Copper",4))));
        assertEquals(4,count(bag,"Copper"));assertEquals("crafted",com.hexvane.aetherhaven.inventory.ItemStackData.metadata(bag.getItemStack((short)0)).getString("origin").getValue());
    }
    @Test void malformedOrOverflowingRequestsCannotBecomeFreeCompletions() {
        var bag=new SimpleItemContainer((short)1);
        assertFalse(FetchQuestMaterials.consume(bag,List.of()));
        assertFalse(FetchQuestMaterials.consume(bag,List.of(item(" ",1))));
        assertFalse(FetchQuestMaterials.consume(bag,List.of(item("Stone",Integer.MAX_VALUE),item("Stone",1))));
    }
}
