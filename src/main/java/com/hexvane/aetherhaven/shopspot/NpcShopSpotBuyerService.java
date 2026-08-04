package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.TownLogEntry;
import com.hexvane.aetherhaven.town.TownLogMessage;
import com.hexvane.aetherhaven.town.TownLogService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Villagers and tourists buy one player listing per visit at a player shop. No town treasury is debited; earnings go to the
 * seller's shop safe. Persistence runs on the world thread via {@link World#execute} so tick systems never write the entity
 * store directly.
 */
public final class NpcShopSpotBuyerService {
    private NpcShopSpotBuyerService() {}

    /**
     * Schedules an NPC purchase attempt on the world thread. Safe to call from entity tick systems.
     */
    public static void scheduleBuyOneListing(
        @Nonnull World world,
        @Nonnull UUID townId,
        @Nonnull UUID playerShopPlotId,
        @Nonnull String buyerDisplayName,
        @Nullable UUID buyerEntityUuid
    ) {
        world.execute(() -> tryBuyOneListingOnWorldThread(world, townId, playerShopPlotId, buyerDisplayName, buyerEntityUuid));
    }

    public static boolean tryBuyOneListingOnWorldThread(
        @Nonnull World world,
        @Nonnull UUID townId,
        @Nonnull UUID playerShopPlotId,
        @Nonnull String buyerDisplayName,
        @Nullable UUID buyerEntityUuid
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (store == null) {
            return false;
        }
        // Player listings stay open at night; only NPC shops are daylight-gated elsewhere.
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return false;
        }
        PlotInstance plot = town.findPlotById(playerShopPlotId);
        if (plot != null && !plot.isAllowNpcShopPurchases()) {
            return false;
        }
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        List<ShopSpotRecord> listings = registry.listPlayerListingsOnPlot(playerShopPlotId);
        if (listings.isEmpty()) {
            return false;
        }
        ShopSpotRecord record = listings.get(ThreadLocalRandom.current().nextInt(listings.size()));
        if (!ShopSpotOpenService.isOpen(record, town, world, store)) {
            return false;
        }
        String itemId = record.getItemId();
        if (itemId == null || record.getStock() <= 0) {
            return false;
        }
        ShopPriceEntry entry = ShopSpotPricing.catalogEntry(plugin, itemId);
        int buyBatches = 1;
        int itemQty = entry.itemsForBatchCount(buyBatches);
        if (itemQty <= 0 || itemQty > record.getStock()) {
            return false;
        }
        long goldPerBatch = ShopSpotPricing.goldPerBatch(plugin, record, itemId);
        long totalCost = ShopSpotPricing.totalCost(goldPerBatch, buyBatches);
        if (totalCost <= 0L) {
            return false;
        }
        ShopSpotPurchaseService.creditSellerPayout(plugin, town, record, totalCost);
        TownLogService.appendEntry(
            town,
            new TownLogEntry(
                VillagerReputationService.currentGameEpochDay(store),
                TownLogService.KEY_SHOP_SALE,
                TownLogMessage.shopSaleParams(buyerDisplayName, itemId, Integer.toString(itemQty), Long.toString(totalCost))
            )
        );
        tm.updateTown(town);
        record.setStock(record.getStock() - itemQty);
        UUID buyerUuid = buyerEntityUuid != null ? buyerEntityUuid : new UUID(0L, 0L);
        ShopSpotPurchaseService.notifySellerIfNeeded(
            record,
            buyerUuid,
            buyerDisplayName,
            itemId,
            itemQty,
            totalCost,
            world,
            store,
            plugin
        );
        if (record.getStock() <= 0) {
            record.setItemId(null);
            record.setSellerUuid(null);
            ShopSpotJewelrySupport.clearJewelryListing(record);
        }
        registry.put(record);
        ShopSpotPersistence.save(world, plugin, registry);
        ShopSpotDisplayService.syncDisplay(world, store, null, plugin, registry, record, town);
        return true;
    }
}
