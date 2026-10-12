package com.hexvane.aetherhaven.construction;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkplace;
import com.hexvane.aetherhaven.workplace.BuildingProjectAutoQueue;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * N48: a planned building is supplied from the warehouse, by real reservations and carried cargo. Saved town and
 * warehouse state only; the courier's walk is the existing delivery code and is not exercised here.
 */
@Tag("town")
class SiteSupplyPlannerTest {
    private static final Gson GSON = new Gson();
    private static final UUID OWNER = UUID.randomUUID();
    private static final String PLANKS = "planks", STONE = "stone", COURIER = "courier";

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

    private static TownRecord town() {
        var yard = new ApprenticeWorkplace("Yard", TownsfolkWorkType.BUILDING, new WorkplaceArea(0, 128, 0, 6, 6));
        return GSON.fromJson(GSON.toJson(Map.of("ownerUuid", OWNER.toString(), "apprenticeWorkplaces", Map.of("sites", List.of(yard)))), TownRecord.class);
    }
    /** A placed plan that the yard already lists, as BuildingProjectAutoQueue does at placement. */
    private static PlotInstance plan(TownRecord town) {
        var plot = new PlotInstance(UUID.randomUUID(), "plot_house", PlotInstanceState.BLUEPRINTING, new PlotFootprintRecord(20, 128, 0, 29, 138, 9), 19, 128, 0, 0);
        town.addPlotInstance(plot);
        assertNotNull(BuildingProjectAutoQueue.offer(town, plot, OWNER));
        return plot;
    }
    private static void store(TownWarehouse stock, String item, int count) { assertTrue(stock.acceptGuardSpoils(new ItemStack(item, count))); }
    private static MaterialQuantity ingredient(String item, int count) { return new MaterialQuantity(item, null, null, count, null); }
    private static int carried(WarehouseBatch batch) { return batch.cargoItems().stream().mapToInt(ItemStack::getQuantity).sum(); }
    private static int waiting(WarehouseBatch batch) { return batch.pendingItems().stream().mapToInt(ItemStack::getQuantity).sum(); }
    private static int deposited(PlotInstance plot, String item) { return PlotMaterialDepositService.depositedCount(plot, MaterialRequirement.ofItem(item, 1)); }

    @Test void aPlannedBuildingIsSuppliedFromSpareStockAndReservedStockIsNeverTaken() {
        var town = town();
        var stock = town.getWarehouse();
        store(stock, PLANKS, 10);
        // A workshop order already holds eight of the ten planks.
        var crafting = stock.reserve(UUID.randomUUID(), UUID.randomUUID(), "recipe", List.of(ingredient(PLANKS, 8)), List.of(new ItemStack("kit", 1)));
        assertNotNull(crafting);
        var plot = plan(town);
        var required = List.of(MaterialRequirement.ofItem(PLANKS, 6));

        var trip = SiteSupplyPlanner.reserveNext(town, plot, required, 5);
        assertNotNull(trip);
        assertTrue(trip.siteSupply());
        assertEquals(2, waiting(trip), "only the two spare planks");
        assertEquals(SiteSupplyPlanner.sign(plot), trip.destination());
        assertEquals(10, stock.count(TownWarehouse.Page.GENERAL, PLANKS), "nothing leaves storage until a courier collects it");
        assertEquals(10, stock.reserved(TownWarehouse.Page.GENERAL, (short) 0));
        assertNull(SiteSupplyPlanner.reserveNext(town, plot, required, 5), "one trip at a time for a site");
        assertFalse(stock.hasUnreservedStock(PLANKS));

        assertEquals(2, stock.pickup(trip.id(), COURIER, 5));
        assertEquals(8, stock.count(TownWarehouse.Page.GENERAL, PLANKS));
        assertEquals(8, waiting(crafting), "the workshop's reservation is whole");
        assertEquals(0, deposited(plot, PLANKS), "carried, not yet delivered");
        assertTrue(SiteSupplyPlanner.deliver(stock, plot, required, trip, COURIER));
        assertEquals(2, deposited(plot, PLANKS));
        assertNull(stock.batch(trip.id()));

        // Everything left is reserved for the workshop: the site waits.
        assertNull(SiteSupplyPlanner.reserveNext(town, plot, required, 5));
        assertEquals(8, waiting(crafting));
        assertEquals(8, stock.pickup(crafting.id(), "smith", 5), "the workshop still gets all eight");
        assertEquals(0, stock.count(TownWarehouse.Page.GENERAL, PLANKS));

        // More planks arrive later; the site takes just what it still needs.
        store(stock, PLANKS, 9);
        var second = SiteSupplyPlanner.reserveNext(town, plot, required, 5);
        assertEquals(4, waiting(second));
        assertEquals(4, stock.pickup(second.id(), COURIER, 5));
        assertTrue(SiteSupplyPlanner.deliver(stock, plot, required, second, COURIER));
        assertTrue(PlotMaterialDepositService.allDeposited(plot, required));
        assertNull(SiteSupplyPlanner.reserveNext(town, plot, required, 5), "a supplied site asks for nothing more");
        assertEquals(5, stock.count(TownWarehouse.Page.GENERAL, PLANKS));
        assertEquals(6, deposited(plot, PLANKS));
    }

    @Test void severalMaterialsArriveOneTripEachAndSurviveASaveWhileCarried() {
        var town = town();
        store(town.getWarehouse(), PLANKS, 20);
        store(town.getWarehouse(), STONE, 3);
        var plot = plan(town);
        var required = List.of(MaterialRequirement.ofItem(PLANKS, 12), MaterialRequirement.ofItem("glass", 4), MaterialRequirement.ofItem(STONE, 8));

        var planks = SiteSupplyPlanner.reserveNext(town, plot, required, 5);
        assertEquals(12, waiting(planks));
        assertEquals(12, town.getWarehouse().pickup(planks.id(), COURIER, 5));
        // The server stops with the courier half way.
        town = GSON.fromJson(GSON.toJson(town), TownRecord.class);
        plot = town.findPlotById(plot.getPlotId());
        var stock = town.getWarehouse();
        planks = stock.batch(planks.id());
        assertTrue(planks.siteSupply());
        assertEquals(12, carried(planks));
        assertEquals(COURIER, planks.courierId());
        assertTrue(SiteSupplyPlanner.deliver(stock, plot, required, planks, COURIER));

        // No glass in storage: that line is skipped, the stone that exists still goes.
        var stone = SiteSupplyPlanner.reserveNext(town, plot, required, 5);
        assertEquals(3, waiting(stone));
        assertEquals(STONE, stone.pendingItems().getFirst().getItemId());
        assertEquals(3, stock.pickup(stone.id(), COURIER, 5));
        assertTrue(SiteSupplyPlanner.deliver(stock, plot, required, stone, COURIER));
        assertEquals(12, deposited(plot, PLANKS));
        assertEquals(3, deposited(plot, STONE));
        assertFalse(PlotMaterialDepositService.allDeposited(plot, required));
        assertNull(SiteSupplyPlanner.reserveNext(town, plot, required, 5));
        assertEquals(8, stock.count(TownWarehouse.Page.GENERAL, PLANKS));
    }

    @Test void whatTheSiteNoLongerNeedsIsCarriedBackNotLost() {
        var town = town();
        var stock = town.getWarehouse();
        store(stock, PLANKS, 10);
        var plot = plan(town);
        var required = List.of(MaterialRequirement.ofItem(PLANKS, 6));
        var trip = SiteSupplyPlanner.reserveNext(town, plot, required, 5);
        assertEquals(6, stock.pickup(trip.id(), COURIER, 5));
        // The player deposits four by hand while the courier is walking.
        assertEquals(4, PlotMaterialDepositService.depositStack(plot, required, new ItemStack(PLANKS, 4)));
        assertFalse(SiteSupplyPlanner.invalid(plot, required, trip), "two are still needed");

        assertTrue(SiteSupplyPlanner.deliver(stock, plot, required, trip, COURIER));
        assertEquals(6, deposited(plot, PLANKS));
        trip = stock.batch(trip.id());
        assertEquals(WarehouseBatch.State.RETURNING, trip.state());
        assertEquals(4, carried(trip));
        assertFalse(SiteSupplyPlanner.deliver(stock, plot, required, trip, COURIER), "a finished delivery cannot be repeated");
        assertEquals(6, deposited(plot, PLANKS));
        assertTrue(stock.returnCargo(trip.id(), COURIER));
        assertEquals(8, stock.count(TownWarehouse.Page.GENERAL, PLANKS));
        assertTrue(stock.batches().isEmpty());
        assertEquals(10, stock.count(TownWarehouse.Page.GENERAL, PLANKS) + deposited(plot, PLANKS) - 4, "ten town planks in all");
    }

    @Test void aSiteThatWasStartedFilledOrRemovedCancelsItsDelivery() {
        var town = town();
        var stock = town.getWarehouse();
        store(stock, PLANKS, 10);
        var plot = plan(town);
        var required = List.of(MaterialRequirement.ofItem(PLANKS, 6));

        // Not yet collected: the reservation is simply released.
        var waitingTrip = SiteSupplyPlanner.reserveNext(town, plot, required, 5);
        plot.setState(PlotInstanceState.ASSEMBLING);
        assertTrue(SiteSupplyPlanner.invalid(plot, required, waitingTrip));
        assertTrue(stock.cancel(waitingTrip.id()));
        assertTrue(stock.batches().isEmpty());
        assertTrue(stock.hasUnreservedStock(PLANKS));
        assertNull(SiteSupplyPlanner.reserveNext(town, plot, required, 5), "a started build is not supplied");

        // Already carried: filled by hand, so the courier turns round with the whole load.
        plot.setState(PlotInstanceState.BLUEPRINTING);
        var carriedTrip = SiteSupplyPlanner.reserveNext(town, plot, required, 5);
        assertEquals(6, stock.pickup(carriedTrip.id(), COURIER, 5));
        assertEquals(6, PlotMaterialDepositService.depositStack(plot, required, new ItemStack(PLANKS, 6)));
        assertTrue(SiteSupplyPlanner.invalid(plot, required, carriedTrip));
        assertTrue(stock.cancel(carriedTrip.id()));
        assertFalse(SiteSupplyPlanner.deliver(stock, plot, required, carriedTrip, COURIER));
        assertTrue(stock.returnCargo(carriedTrip.id(), COURIER));
        assertEquals(10, stock.count(TownWarehouse.Page.GENERAL, PLANKS));
        assertTrue(SiteSupplyPlanner.invalid(null, List.of(), carriedTrip), "a removed plan");
    }

    @Test void onlyTheRightCourierAtTheRightSignCanDeliver() {
        var town = town();
        var stock = town.getWarehouse();
        store(stock, PLANKS, 10);
        var plot = plan(town);
        var required = List.of(MaterialRequirement.ofItem(PLANKS, 6));
        var trip = SiteSupplyPlanner.reserveNext(town, plot, required, 5);
        assertFalse(SiteSupplyPlanner.deliver(stock, plot, required, trip, COURIER), "nothing collected yet");
        assertEquals(6, stock.pickup(trip.id(), COURIER, 5));
        assertEquals(0, stock.pickup(trip.id(), "second", 5), "one courier collects it");
        assertFalse(SiteSupplyPlanner.deliver(stock, plot, required, trip, "second"));
        assertFalse(stock.deliverSiteSupply(trip.id(), COURIER, new WarehouseBatch.Point(0, 0, 0), stack -> stack.getQuantity()));
        assertFalse(stock.deliver(trip.id(), COURIER), "a site trip is not a workplace delivery");
        assertEquals(0, deposited(plot, PLANKS));
        assertTrue(SiteSupplyPlanner.deliver(stock, plot, required, trip, COURIER));
        assertEquals(6, deposited(plot, PLANKS));
        assertFalse(SiteSupplyPlanner.deliver(stock, plot, required, trip, COURIER));
        assertEquals(6, deposited(plot, PLANKS));
        assertEquals(4, stock.count(TownWarehouse.Page.GENERAL, PLANKS));
    }

    @Test void supplyNeedsAYardOrderAndAPlayerAllowedToUseStorage() {
        var town = town();
        store(town.getWarehouse(), PLANKS, 10);
        var required = List.of(MaterialRequirement.ofItem(PLANKS, 6));
        var yard = town.getApprenticeWorkplaces().sites().getFirst().building();

        var unlisted = new PlotInstance(UUID.randomUUID(), "plot_house", PlotInstanceState.BLUEPRINTING, new PlotFootprintRecord(40, 128, 0, 49, 138, 9), 39, 128, 0, 0);
        town.addPlotInstance(unlisted);
        assertFalse(SiteSupplyPlanner.supplied(town, unlisted));
        assertNull(SiteSupplyPlanner.reserveNext(town, unlisted, required, 5), "not on any yard's board");
        // Queued by someone who is not a member: their order never runs and never draws on storage.
        assertNull(yard.add(unlisted.getPlotId(), UUID.randomUUID()));
        assertNull(SiteSupplyPlanner.reserveNext(town, unlisted, required, 5));

        var listed = plan(town);
        assertTrue(SiteSupplyPlanner.supplied(town, listed));
        assertTrue(yard.pause(true));
        assertNull(SiteSupplyPlanner.reserveNext(town, listed, required, 5), "a paused yard starts no new trips");
        assertTrue(yard.pause(false));
        assertNotNull(SiteSupplyPlanner.reserveNext(town, listed, required, 5));
        assertTrue(town.getWarehouse().hasUnreservedStock(PLANKS));
        assertEquals(4, SiteSupplyPlanner.available(town.getWarehouse(), MaterialRequirement.ofItem(PLANKS, 99), 99));
    }
}
