package com.hexvane.aetherhaven.mastery;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("town")
class CoreVillagerMasteryTest {
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
    @Test void oldSavesStartWithCrudeToolsAndIndependentCappedMastery() {
        var town = new Gson().fromJson("{}", TownRecord.class);
        assertEquals(1, CoreVillagerMasteryService.level(town,"miner"));
        assertEquals("Tool_Pickaxe_Crude",CoreVillagerMasteryService.toolId(town,"miner"));
        var miner = CoreVillagerMasteryService.progress(town,"miner");
        for(int i=1;i<10;i++)assertTrue(miner.train());
        assertFalse(miner.train()); assertEquals(10,miner.level());
        assertEquals(1,CoreVillagerMasteryService.level(town,"logger"));
        assertEquals(10,CoreVillagerMasteryService.level(new Gson().fromJson(new Gson().toJson(town),TownRecord.class),"miner"));
    }
    @Test void earnedXpPersistsAndCatalystsKeepPartialProgressWithoutGrantingExtraLevels() {
        var progress = new Gson().fromJson("{\"level\":4}", CoreVillagerMastery.class);
        assertEquals(0, progress.xpInLevel());
        assertEquals(250, progress.xpToNextLevel());
        assertEquals(249, progress.earnXp(249));
        assertTrue(progress.train());
        assertEquals(5, progress.level());
        assertEquals(249, progress.xpInLevel());
        progress = new Gson().fromJson(new Gson().toJson(progress), CoreVillagerMastery.class);
        assertEquals(51, progress.earnXp(51));
        assertEquals(6, progress.level());
        assertEquals(0, progress.xpInLevel());
        assertEquals(0, progress.earnXp(-1));
        assertEquals(1700, progress.earnXp(Long.MAX_VALUE));
        assertEquals(10, progress.level());
        assertEquals(0, progress.xpInLevel());
        assertEquals(0, progress.xpToNextLevel());
        assertEquals(0, progress.earnXp(1));
        assertFalse(progress.train());
    }
    @Test void oreRequiresBothTrainingAndTheSpecifiedToolTier() {
        var town = new TownRecord(); var miner=CoreVillagerMasteryService.progress(town,"miner");
        assertNull(CoreProductionRequirements.issueForKind(town,"miner","Ore_Copper"));
        assertEquals("Requires Mastery 3.",CoreProductionRequirements.issueForKind(town,"miner","Ore_Iron"));
        miner.train(); miner.train();
        assertEquals("Requires Copper Pickaxe.",CoreProductionRequirements.issueForKind(town,"miner","Ore_Iron"));
        miner.setTool(new ItemStack("Tool_Pickaxe_Copper",1));
        assertNull(CoreProductionRequirements.issueForKind(town,"miner","Ore_Iron"));
        while(miner.train()) {}
        assertEquals("Requires Iron Pickaxe.",CoreProductionRequirements.issueForKind(town,"miner","Ore_Cobalt"));
        miner.setTool(new ItemStack("Tool_Pickaxe_Iron",1));
        assertNull(CoreProductionRequirements.issueForKind(town,"miner","Ore_Thorium"));
        assertNull(CoreProductionRequirements.issueForKind(town,"miner","Ore_Cobalt"));
        assertNotNull(CoreProductionRequirements.issueForKind(town,"miner","Ore_Adamantite"));
        for(String tier:java.util.List.of("Thorium","Cobalt","Adamantite","Mithril")) {
            miner.setTool(new ItemStack("Tool_Pickaxe_"+tier,1));
            assertNull(CoreProductionRequirements.issueForKind(town,"miner","Ore_Adamantite"));
            assertNull(CoreProductionRequirements.issueForKind(town,"miner","Ore_Iron"));
        }
    }
    @Test void woodBandsFollowRequestedNativeSaplingBenchTiers() {
        assertEquals(new CoreProductionRequirements.Requirement(1,0),CoreProductionRequirements.wood(0));
        assertEquals(new CoreProductionRequirements.Requirement(4,2),CoreProductionRequirements.wood(4));
        assertEquals(new CoreProductionRequirements.Requirement(5,3),CoreProductionRequirements.wood(5));
        assertEquals(new CoreProductionRequirements.Requirement(6,3),CoreProductionRequirements.wood(6));
        assertEquals(new CoreProductionRequirements.Requirement(7,4),CoreProductionRequirements.wood(7));
        assertEquals(new CoreProductionRequirements.Requirement(8,4),CoreProductionRequirements.wood(8));
        assertEquals(-1,CoreVillagerMasteryService.toolTier("logger","Tool_Pickaxe_Adamantite"));
    }
    @Test void exactToolExchangeKeepsMetadataSwapsWithTheToolHeldNowAndRejectsWrongOrMissingItems() {
        var p=new CoreVillagerMastery(); var bag=new SimpleItemContainer((short)1);
        var copper=new ItemStack("Tool_Pickaxe_Copper",1).withMetadata("custom",new org.bson.BsonString("keep"));
        bag.setItemStackForSlot((short)0,copper);
        assertNull(CoreVillagerMasteryService.equip(p,"miner",bag,(short)0,copper));
        assertTrue(ItemStack.isEmpty(bag.getItemStack((short)0))); assertEquals(copper,p.tool());
        var iron=new ItemStack("Tool_Pickaxe_Iron",1); bag.setItemStackForSlot((short)0,iron);
        // The page may have drawn the villager without a tool. The copper pickaxe held now comes back all the same.
        assertNull(CoreVillagerMasteryService.equip(p,"miner",bag,(short)0,iron));
        assertEquals(copper,bag.getItemStack((short)0)); assertEquals(iron,p.tool());
        // The same press again: the iron pickaxe is no longer carried, so nothing changes and the reason is given.
        assertEquals(CoreVillagerMasteryService.TOOL_GONE,CoreVillagerMasteryService.equip(p,"miner",bag,(short)0,iron));
        assertEquals(copper,bag.getItemStack((short)0)); assertEquals(iron,p.tool());
        assertEquals(iron,new Gson().fromJson(new Gson().toJson(p),CoreVillagerMastery.class).tool());
        assertNotNull(CoreVillagerMasteryService.equip(p,"logger",bag,(short)0,copper));
        assertEquals(copper,bag.getItemStack((short)0)); assertEquals(iron,p.tool());
    }
}
