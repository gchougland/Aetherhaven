package com.hexvane.aetherhaven.construction;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkplace;
import com.hexvane.aetherhaven.workplace.WorkplaceArea;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bson.BsonDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * P47: builders carry loose items from a building site to the warehouse. Saved town and warehouse state only; finding
 * the item entities and the walk are exercised by {@code /ah test salvage} on a server, not here.
 */
@Tag("town")
class SiteSalvageTest {
    private static final Gson GSON = new Gson();
    private static final UUID OWNER = UUID.randomUUID();
    private static final String STONE = "stone", PLANKS = "planks", ORE = "ore", BUILDER = "builder", OTHER = "other builder";

    // The same stand-in asset maps the warehouse tests use, so real stacks and containers work without a server.
    private static Object previousItems, previousQualities;
    private static class Qualities extends AssetStore<String, ItemQuality, IndexedLookupTableAssetMap<String, ItemQuality>> {
        Qualities(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return new EventBus(false); }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, ItemQuality> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, ItemQuality, IndexedLookupTableAssetMap<String, ItemQuality>, Config> {
            Config() {
                super(String.class, ItemQuality.class, new IndexedLookupTableAssetMap<>(ItemQuality[]::new));
                setPath("ItemQualities"); setCodec(ItemQuality.CODEC); setKeyFunction(ItemQuality::getId);
                setReplaceOnRemove(id -> ItemQuality.DEFAULT_ITEM_QUALITY);
            }
            @Override public Qualities build() { return new Qualities(this); }
        }
    }
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
        var items = Item.class.getDeclaredField("ASSET_STORE"); items.setAccessible(true);
        previousItems = items.get(null); items.set(null, new Items.Config().build());
        var qualities = ItemQuality.class.getDeclaredField("ASSET_STORE"); qualities.setAccessible(true);
        previousQualities = qualities.get(null); qualities.set(null, new Qualities.Config().build());
    }
    @AfterAll static void restore() throws Exception {
        var items = Item.class.getDeclaredField("ASSET_STORE"); items.setAccessible(true); items.set(null, previousItems);
        var qualities = ItemQuality.class.getDeclaredField("ASSET_STORE"); qualities.setAccessible(true); qualities.set(null, previousQualities);
    }

    private static ItemStack stack(String item, int count) { return new ItemStack(item, count); }
    /** The most of one test item a single native stack holds. */
    private static int full() { return stack(STONE, 1).getItem().getMaxStack(); }
    private static void store(TownWarehouse stock, String item, int count) { assertTrue(stock.acceptGuardSpoils(stack(item, count))); }
    private static long stored(TownWarehouse stock, String item) { return stock.count(TownWarehouse.Page.GENERAL, item); }
    private static WarehouseBatch load(TownWarehouse stock, String worker) {
        return stock.batches().stream().filter(b -> worker.equals(b.courierId())).findFirst().orElse(null);
    }
    private static int carried(TownWarehouse stock, String worker, String item) {
        var batch = load(stock, worker);
        return batch == null ? 0 : batch.cargoItems().stream().filter(s -> item.equals(s.getItemId())).mapToInt(ItemStack::getQuantity).sum();
    }
    /** Leaves exactly one general storage slot, holding {@code held} of {@code item} (zero leaves it empty). */
    private static TownWarehouse almostFull(String item, int held) {
        var stock = new TownWarehouse();
        for (int slot = 0; slot < stock.capacity() - 1; slot++) store(stock, "filler" + slot, 1);
        if (held > 0) store(stock, item, held);
        assertEquals(stock.capacity() - (held > 0 ? 0 : 1), stock.used(TownWarehouse.Page.GENERAL));
        return stock;
    }

    @Test void whatIsTakenNeverExceedsTheRoomInStorage() {
        int full = full();
        var stock = almostFull(STONE, full - 10);
        // 25 stone lie on the site, storage can hold 10 more: 10 are taken and 15 stay on the ground.
        int[] taken = SiteSalvage.take(stock, true, BUILDER, List.of(stack(STONE, 25), stack(PLANKS, 5)), 5);
        assertArrayEquals(new int[] {10, 0}, taken, "planks have no slot at all");
        assertEquals(10, carried(stock, BUILDER, STONE));
        assertEquals(full - 10, stored(stock, STONE), "nothing is in storage until the builder reaches a desk");
        // The room is promised once: more stone, for this builder or another, finds none left.
        assertArrayEquals(new int[] {0}, SiteSalvage.take(stock, true, BUILDER, List.of(stack(STONE, 15)), 5));
        assertArrayEquals(new int[] {0}, SiteSalvage.take(stock, true, OTHER, List.of(stack(STONE, 15)), 5));
        assertNull(load(stock, OTHER), "a refused pickup leaves no empty load behind");
        assertEquals(List.of(), stock.unloadSalvage(load(stock, BUILDER).id(), BUILDER));
        assertEquals(full, stored(stock, STONE));
        assertTrue(stock.batches().isEmpty());
    }

    @Test void whatIsTakenNeverExceedsWhatTheHandsHold() {
        int full = full();
        var stock = new TownWarehouse();
        // Two stacks in hand at most. The third kind stays on the ground although storage is empty.
        int[] taken = SiteSalvage.take(stock, true, BUILDER, List.of(stack(STONE, full), stack(PLANKS, 7), stack(ORE, 5)), 2);
        assertArrayEquals(new int[] {full, 7, 0}, taken);
        var room = stock.salvageRoom(BUILDER, 2);
        assertTrue(room.carrying());
        assertTrue(room.handsFull());
        assertEquals(0, room.accepts(stack(ORE, 1)));
        assertEquals(0, room.accepts(stack(STONE, 1)), "that stack is full");
        assertEquals(full - 7, room.accepts(stack(PLANKS, full)), "a part stack in hand can be topped up");
        // A second pickup tops the planks up and takes no more than that.
        assertArrayEquals(new int[] {full - 7}, SiteSalvage.take(stock, true, BUILDER, List.of(stack(PLANKS, full)), 2));
        assertEquals(1, stock.batches().size(), "one load per builder, however many pickups");
        assertEquals(full, carried(stock, BUILDER, PLANKS));
        assertEquals(full, carried(stock, BUILDER, STONE));
        assertNull(stock.salvageRoom(BUILDER, 0), "no carrying allowance");
        assertNull(stock.salvageRoom(" ", 3));
    }

    @Test void reservedStockIsLeftAlone() {
        var stock = new TownWarehouse();
        store(stock, PLANKS, 10);
        // A workshop order holds eight of the ten planks before any salvage arrives.
        var workshop = stock.reserve(UUID.randomUUID(), UUID.randomUUID(), "recipe",
            List.of(new MaterialQuantity(PLANKS, null, null, 8, null)), List.of(stack("kit", 1)));
        assertNotNull(workshop);
        assertEquals(8, stock.reserved(TownWarehouse.Page.GENERAL, (short) 0));

        assertArrayEquals(new int[] {5}, SiteSalvage.take(stock, true, BUILDER, List.of(stack(PLANKS, 5)), 3));
        assertEquals(10, stored(stock, PLANKS));
        assertEquals(8, stock.reserved(TownWarehouse.Page.GENERAL, (short) 0));
        assertEquals(List.of(), stock.unloadSalvage(load(stock, BUILDER).id(), BUILDER));
        assertEquals(15, stored(stock, PLANKS));
        assertEquals(8, stock.reserved(TownWarehouse.Page.GENERAL, (short) 0), "the reservation is whole");
        assertEquals(8, stock.pickup(workshop.id(), "courier", 5), "and the workshop still collects all eight");
        assertEquals(7, stored(stock, PLANKS));
    }

    @Test void aFullWarehouseTakesNothing() {
        var stock = almostFull(STONE, full());
        long revision = stock.revision();
        assertArrayEquals(new int[] {0, 0}, SiteSalvage.take(stock, true, BUILDER, List.of(stack(STONE, 3), stack(PLANKS, 3)), 5));
        assertTrue(stock.batches().isEmpty());
        assertEquals(revision, stock.revision(), "nothing changed at all");
        assertEquals(0, stock.salvageRoom(BUILDER, 5).accepts(stack(STONE, 1)));
    }

    @Test void withNoWarehouseNothingIsPickedUp() {
        var stock = new TownWarehouse();
        assertArrayEquals(new int[] {0}, SiteSalvage.take(stock, false, BUILDER, List.of(stack(STONE, 3)), 5));
        assertTrue(stock.batches().isEmpty());

        var town = town();
        assertFalse(SiteSalvage.warehouseBuilt(town, SiteSalvage.WAREHOUSE::equals));
        var warehouse = new PlotInstance(UUID.randomUUID(), SiteSalvage.WAREHOUSE, PlotInstanceState.ASSEMBLING, new PlotFootprintRecord(40, 128, 0, 49, 138, 9), 39, 128, 0, 0);
        town.addPlotInstance(warehouse);
        assertFalse(SiteSalvage.warehouseBuilt(town, SiteSalvage.WAREHOUSE::equals), "a Warehouse still being built is not one yet");
        warehouse.setState(PlotInstanceState.COMPLETE);
        assertTrue(SiteSalvage.warehouseBuilt(town, SiteSalvage.WAREHOUSE::equals));
    }

    @Test void aCarriedLoadSurvivesASaveAndIsStoredOnce() {
        var town = town();
        var special = new ItemStack(ORE, 2, BsonDocument.parse("{custom: {name:'Kept'}}"));
        assertArrayEquals(new int[] {12, 3, 2}, SiteSalvage.take(town.getWarehouse(), true, BUILDER, List.of(stack(STONE, 12), stack(PLANKS, 3), special), 5));
        UUID id = load(town.getWarehouse(), BUILDER).id();

        // The server stops while the builder is on the way to the desk.
        town = GSON.fromJson(GSON.toJson(town), TownRecord.class);
        var stock = town.getWarehouse();
        var batch = stock.batch(id);
        assertNotNull(batch);
        assertTrue(batch.salvage());
        assertTrue(batch.cancelled(), "the courier code returns a cancelled load to a desk");
        assertEquals(WarehouseBatch.State.RETURNING, batch.state());
        assertEquals(BUILDER, batch.courierId());
        assertNull(batch.destination());
        assertEquals(List.of(stack(STONE, 12), stack(PLANKS, 3), special), batch.cargoItems());
        assertTrue(stock.salvageRoom(BUILDER, 5).carrying());
        assertEquals(0, stored(stock, STONE));

        assertNull(stock.unloadSalvage(id, OTHER), "only the builder who carries it can put it away");
        assertEquals(List.of(), stock.unloadSalvage(id, BUILDER));
        assertEquals(12, stored(stock, STONE));
        assertEquals(3, stored(stock, PLANKS));
        assertEquals(special, stock.stack(TownWarehouse.Page.GENERAL, (short) 2), "metadata is kept");
        assertTrue(stock.batches().isEmpty());
        assertNull(stock.unloadSalvage(id, BUILDER), "a second arrival stores nothing more");
        assertEquals(12, stored(stock, STONE));
    }

    @Test void theOrdinaryReturnTripAlsoStoresALoad() {
        var stock = new TownWarehouse();
        assertArrayEquals(new int[] {6}, SiteSalvage.take(stock, true, BUILDER, List.of(stack(STONE, 6)), 5));
        // What CourierWorkService did before it knew about salvage still puts the load away, all or nothing.
        assertTrue(stock.returnCargo(load(stock, BUILDER).id(), BUILDER));
        assertEquals(6, stored(stock, STONE));
        assertTrue(stock.batches().isEmpty());
    }

    @Test void aLoadThatNoLongerFitsIsHandedBackAndNeverLost() {
        int full = full();
        var stock = almostFull(STONE, 0);
        assertArrayEquals(new int[] {30}, SiteSalvage.take(stock, true, BUILDER, List.of(stack(STONE, 30)), 5));
        // A player fills most of the last slot while the builder walks.
        store(stock, STONE, full - 10);
        var left = stock.unloadSalvage(load(stock, BUILDER).id(), BUILDER);
        assertEquals(List.of(stack(STONE, 20)), left, "ten fitted, twenty go back on the ground");
        assertEquals(full, stored(stock, STONE));
        assertTrue(stock.batches().isEmpty(), "the builder's hands are empty either way");
    }

    @Test void suppliesAlreadyWalkingBackKeepTheirRoom() {
        var stock = new TownWarehouse();
        store(stock, ORE, 6);
        var delivery = stock.reserve(UUID.randomUUID(), UUID.randomUUID(), "recipe",
            List.of(new MaterialQuantity(ORE, null, null, 6, null)), List.of(stack("kit", 1)));
        assertEquals(6, stock.pickup(delivery.id(), "courier", 5));
        assertTrue(stock.cancel(delivery.id()));
        assertNull(stock.salvageRoom("courier", 5), "hands holding a delivery take no salvage");
        assertArrayEquals(new int[] {0}, SiteSalvage.take(stock, true, "courier", List.of(stack(STONE, 3)), 5));
        // One slot is free again and it belongs to the ore on its way back.
        for (int slot = 1; slot < stock.capacity(); slot++) store(stock, "filler" + slot, 1);
        assertArrayEquals(new int[] {0}, SiteSalvage.take(stock, true, BUILDER, List.of(stack(STONE, 3)), 5));
        assertArrayEquals(new int[] {4}, SiteSalvage.take(stock, true, BUILDER, List.of(stack(ORE, 4)), 5), "ore shares the ore's slot");
        assertTrue(stock.returnCargo(delivery.id(), "courier"));
        assertEquals(List.of(), stock.unloadSalvage(load(stock, BUILDER).id(), BUILDER));
        assertEquals(10, stored(stock, ORE));
    }

    private static TownRecord town() {
        var yard = new ApprenticeWorkplace("Yard", TownsfolkWorkType.BUILDING, new WorkplaceArea(0, 128, 0, 6, 6));
        return GSON.fromJson(GSON.toJson(Map.of("ownerUuid", OWNER.toString(), "apprenticeWorkplaces", Map.of("sites", List.of(yard)))), TownRecord.class);
    }
    private static PlotInstance project(TownRecord town, PlotInstanceState state, int minX) {
        var plot = new PlotInstance(UUID.randomUUID(), "plot_house", state, new PlotFootprintRecord(minX, 128, 0, minX + 9, 138, 9), minX - 1, 128, 0, 0);
        town.addPlotInstance(plot);
        var yard = town.getApprenticeWorkplaces().sites().getFirst();
        assertNull(yard.building().add(plot.getPlotId(), OWNER));
        return plot;
    }

    @Test void onlyTheSitesBeingRaisedAreTidiedAndTwoBlocksAroundThem() {
        var town = town();
        var yard = town.getApprenticeWorkplaces().sites().getFirst();
        var building = project(town, PlotInstanceState.ASSEMBLING, 20);
        project(town, PlotInstanceState.BLUEPRINTING, 60);
        project(town, PlotInstanceState.COMPLETE, 100);
        var clearing = project(town, PlotInstanceState.ASSEMBLING, 140);

        var areas = SiteSalvage.areas(town, yard, order -> !order.plot().equals(clearing.getPlotId()));
        assertEquals(1, areas.size(), "not the plan, not the finished house, not the site still being cleared");
        var area = areas.getFirst();
        assertEquals(building.toFootprint().getMinX() - SiteSalvage.MARGIN, area.getMinX());
        assertEquals(building.toFootprint().getMaxZ() + SiteSalvage.MARGIN, area.getMaxZ());
        assertTrue(SiteSalvage.lies(area, 18.0, 128.2, -2.0), "two blocks outside the corner");
        assertTrue(SiteSalvage.lies(area, 31.99, 126.0, 11.99));
        assertFalse(SiteSalvage.lies(area, 17.99, 128.2, 5.0), "three blocks out is somebody's dropped lunch, not site litter");
        assertFalse(SiteSalvage.lies(area, 32.0, 128.2, 5.0));
        assertFalse(SiteSalvage.lies(area, 25.0, 125.9, 5.0));

        yard.building().pause(true);
        assertEquals(List.of(), SiteSalvage.areas(town, yard, order -> true), "a paused yard tidies nothing");
        assertEquals(List.of(), SiteSalvage.areas(town, null, order -> true));
    }

    @Test void aBuilderCarriesWhatACourierOfTheSameLevelWould() {
        var progress = new TownsfolkProgress();
        progress.setSkill(TownsfolkSkill.LOGISTICS, 0);
        progress.setSkill(TownsfolkSkill.BUILDING, 0);
        assertEquals(1, SiteSalvage.carryStacks(progress, null), "always at least one stack");
        progress.setSkill(TownsfolkSkill.BUILDING, 1);
        assertEquals(1, SiteSalvage.carryStacks(progress, null));
        progress.setSkill(TownsfolkSkill.BUILDING, 5);
        assertEquals(3, SiteSalvage.carryStacks(progress, null));
        progress.setSkill(TownsfolkSkill.BUILDING, 10);
        assertEquals(5, SiteSalvage.carryStacks(progress, null));
        // A builder who is also a good courier keeps the larger allowance.
        progress.setSkill(TownsfolkSkill.BUILDING, 1);
        progress.setSkill(TownsfolkSkill.LOGISTICS, 8);
        assertEquals(4, SiteSalvage.carryStacks(progress, null));
    }
}
