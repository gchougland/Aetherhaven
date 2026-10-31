package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.production.FarmFields;
import com.hexvane.aetherhaven.production.WorkplaceUnlockCatalog;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.FarmerFieldPage;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class FarmCropUnlockNavigationTest {
    private static Object previousItems;
    private static final class Items extends com.hypixel.hytale.assetstore.AssetStore<String,
        com.hypixel.hytale.server.core.asset.type.item.config.Item,
        com.hypixel.hytale.assetstore.map.DefaultAssetMap<String, com.hypixel.hytale.server.core.asset.type.item.config.Item>> {
        private final com.hypixel.hytale.event.EventBus events = new com.hypixel.hytale.event.EventBus(false);
        Items(Config config) { super(config); }
        @Override protected com.hypixel.hytale.event.IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, java.nio.file.Path path) {}
        @Override public void removeFileMonitor(java.nio.file.Path path) {}
        @Override protected void handleRemoveOrUpdate(java.util.Set<String> removed,
            java.util.Map<String, com.hypixel.hytale.server.core.asset.type.item.config.Item> updated, com.hypixel.hytale.assetstore.AssetUpdateQuery query) {}
        static class Config extends Builder<String, com.hypixel.hytale.server.core.asset.type.item.config.Item,
            com.hypixel.hytale.assetstore.map.DefaultAssetMap<String, com.hypixel.hytale.server.core.asset.type.item.config.Item>, Config> {
            Config() { super(String.class, com.hypixel.hytale.server.core.asset.type.item.config.Item.class, new com.hypixel.hytale.assetstore.map.DefaultAssetMap<>());
                setPath("Items"); setCodec(com.hypixel.hytale.server.core.asset.type.item.config.Item.CODEC); setKeyFunction(com.hypixel.hytale.server.core.asset.type.item.config.Item::getId); }
            @Override public Items build() { return new Items(this); }
        }
    }
    @BeforeAll static void assets() throws Exception {
        var field = com.hypixel.hytale.server.core.asset.type.item.config.Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true);
        previousItems = field.get(null); field.set(null, new Items.Config().build());
    }
    @AfterAll static void restore() throws Exception {
        var field = com.hypixel.hytale.server.core.asset.type.item.config.Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true); field.set(null, previousItems);
    }
    @Test void homeAndRemoteScarecrowUnlocksReturnToTheSamePlannerInsteadOfStorage() throws Exception {
        var plot = new PlotInstance(UUID.randomUUID(), FarmFields.CONSTRUCTION, PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0, 100, 0, 11, 104, 11), 0, 100, 0, 0L);
        var town = UUID.randomUUID();
        for (Point scarecrow : new Point[] {null, new Point(80, 101, 80)}) {
            var unlocks = ProductionStorageUnlocksPage.farmFields(null, town, plot, scarecrow);
            assertInstanceOf(FarmerFieldPage.class, unlocks.returnPage());
            var field = FarmerFieldPage.class.getDeclaredField("scarecrow"); field.setAccessible(true);
            assertEquals(scarecrow, field.get(unlocks.returnPage()));
            for (String coordinate : new String[] {"blockX", "blockY", "blockZ"}) {
                var f = ProductionStorageUnlocksPage.class.getDeclaredField(coordinate); f.setAccessible(true);
                assertEquals(coordinate.equals("blockY") ? 100 : 0, f.getInt(unlocks),
                    "Unlock validation uses a point inside the farm, even from a remote Scarecrow");
            }
        }
        assertInstanceOf(ProductionStoragePage.class,
            new ProductionStorageUnlocksPage(null, town, plot.getPlotId(), 0, 100, 0).returnPage());
    }

    @Test void everyCropShowsItsMasteryRequirementSeparatelyFromTheUnlockCost() {
        var town = new TownRecord();
        var unlocks = WorkplaceUnlockCatalog.loadFromClasspath(getClass().getClassLoader());
        for (var crop : FarmFields.cropOrder()) {
            var line = unlocks.findLine(FarmFields.CONSTRUCTION, crop);
            assertNotNull(line, crop);
            var message = ProductionStorageUnlocksPage.cropMasteryMessage(line, town);
            assertNotNull(message, crop);
            assertEquals(FarmFields.LANG + "ui.unlocks.mastery", message.getMessageId());
        }
        assertNull(ProductionStorageUnlocksPage.cropMasteryMessage(
            new WorkplaceUnlockCatalog.UnlockLine("plot_lumbermill", "Wood_Oak_Trunk", 1, false, 25, 200, 25), town));
    }

    @Test void cropUnlocksStayWithTheFarmWhenItsCommunityPrefabAlsoContainsAMill() {
        var gson=new com.google.gson.Gson();
        var definition=gson.fromJson("{\"id\":\"mixed\",\"countsAsConstructionId\":[\"plot_lumbermill\",\"plot_farm\"]}",
            com.hexvane.aetherhaven.construction.ConstructionDefinition.class);
        var catalog=com.hexvane.aetherhaven.construction.ConstructionCatalog.forTests(java.util.Map.of("mixed",definition));
        var plot=new PlotInstance(UUID.randomUUID(),"mixed",PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0,100,0,11,104,11),0,100,0,0L);
        var unlocks=ProductionStorageUnlocksPage.farmFields(null,UUID.randomUUID(),plot,null);
        assertEquals(FarmFields.CONSTRUCTION,unlocks.gameplayConstructionId(catalog,plot));
        assertInstanceOf(FarmerFieldPage.class,unlocks.returnPage());
    }
}
