package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Path;
import java.util.*;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;

@Tag("town")
class StationUpgradeCommitTest {
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
    private static MaterialQuantity cost(String id,int count){return new MaterialQuantity(id,null,null,count,null);}
    private static void put(SimpleItemContainer bag,int slot,ItemStack stack){bag.setItemStackForSlot((short)slot,stack,false);}
    private static int count(SimpleItemContainer bag,int slot){var s=bag.getItemStack((short)slot);return s==null?0:s.getQuantity();}

    @Test void missingSecondIngredientDoesNotConsumeTheFirstOrChangeTier() {
        var bag=new SimpleItemContainer((short)4);put(bag,0,new ItemStack("ingot",8));
        var bench=new BenchBlock();
        assertEquals(StationUpgradeCommit.Result.MISSING_MATERIALS,StationUpgradeCommit.apply(bench,1,
            new MaterialQuantity[]{cost("ingot",4),cost("leather",2)},bag,false));
        assertEquals(8,count(bag,0));assertEquals(1,bench.getTierLevel());assertEquals(0,bench.getUpgradeItems().length);
    }
    @Test void successfulUpgradeRecordsExactConsumedStacksAndCannotChargeTheSameTierTwice() {
        var bag=new SimpleItemContainer((short)4);
        var engraved=new ItemStack("ingot",6,BsonDocument.parse("{source:'test'}"));put(bag,0,engraved);
        var oldRefund=new ItemStack("oldMaterial",2);
        var bench=new BenchBlock(1,new ItemStack[]{oldRefund});
        var costs=new MaterialQuantity[]{new MaterialQuantity("ingot",null,null,4,com.hexvane.aetherhaven.inventory.ItemStackData.metadata(engraved))};
        assertEquals(StationUpgradeCommit.Result.UPGRADED,StationUpgradeCommit.apply(bench,1,costs,bag,false));
        assertEquals(2,count(bag,0));assertEquals(2,bench.getTierLevel());
        assertTrue(Arrays.asList(bench.getUpgradeItems()).contains(oldRefund));
        var refund=Arrays.stream(bench.getUpgradeItems()).filter(s->s.getItemId().equals("ingot")).findFirst().orElseThrow();
        assertEquals(4,refund.getQuantity());assertEquals(com.hexvane.aetherhaven.inventory.ItemStackData.metadata(engraved),com.hexvane.aetherhaven.inventory.ItemStackData.metadata(refund));
        assertEquals(StationUpgradeCommit.Result.CHANGED,StationUpgradeCommit.apply(bench,1,costs,bag,false));
        assertEquals(2,count(bag,0));assertEquals(2,bench.getUpgradeItems().length);
    }
    @Test void creativeUpgradeDoesNotCreateFreeRefundMaterials() {
        var bag=new SimpleItemContainer((short)4);put(bag,0,new ItemStack("ingot",8));var bench=new BenchBlock();
        assertEquals(StationUpgradeCommit.Result.UPGRADED,StationUpgradeCommit.apply(bench,1,new MaterialQuantity[]{cost("ingot",4)},bag,true));
        assertEquals(8,count(bag,0));assertEquals(2,bench.getTierLevel());assertEquals(0,bench.getUpgradeItems().length);
    }
    @Test void ordinaryMaterialsCanSpanSeveralInventorySlots() {
        var bag=new SimpleItemContainer((short)4);put(bag,0,new ItemStack("ingot",2));put(bag,1,new ItemStack("ingot",3));
        put(bag,2,new ItemStack("leather",2));var bench=new BenchBlock();
        assertEquals(StationUpgradeCommit.Result.UPGRADED,StationUpgradeCommit.apply(bench,1,
            new MaterialQuantity[]{cost("ingot",4),cost("leather",2)},bag,false));
        assertEquals(1,count(bag,0)+count(bag,1));assertEquals(0,count(bag,2));
        assertEquals(6,Arrays.stream(bench.getUpgradeItems()).mapToInt(ItemStack::getQuantity).sum());
    }
    @Test void undefinedUpgradeCannotAdvanceEvenInCreative() {
        var bench=new BenchBlock();var bag=new SimpleItemContainer((short)1);
        assertEquals(StationUpgradeCommit.Result.CHANGED,StationUpgradeCommit.apply(bench,1,new MaterialQuantity[0],bag,true));
        assertEquals(1,bench.getTierLevel());
    }
}
