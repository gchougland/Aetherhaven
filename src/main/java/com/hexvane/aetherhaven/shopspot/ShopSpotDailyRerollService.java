package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.time.AetherhavenMorningWindow;
import com.hexvane.aetherhaven.time.GameTimeEpochs;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;

/**
 * NPC shops are stocked when first placed, then reroll at most once per in-game day during the morning window (dawn).
 * Player-controlled spots never auto-reroll. Day/night gates whether the shop is open; it does not change stock.
 */
public final class ShopSpotDailyRerollService {
    private static final ConcurrentHashMap<String, Long> LAST_MORNING_REROLL_EPOCH_DAY = new ConcurrentHashMap<>();

    private ShopSpotDailyRerollService() {}

    public static void clearWorldState(@Nonnull String worldName) {
        LAST_MORNING_REROLL_EPOCH_DAY.remove(worldName);
    }

    public static void scheduleTickFromHub(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldTimeResource wtr
    ) {
        world.execute(() -> tick(world, plugin, wtr));
    }

    public static void catchUpAfterTimeJump(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Instant from,
        @Nonnull Instant to
    ) {
        int morningStart = plugin.getConfig().get().getGameMorningStartHour();
        LinkedHashSet<Long> days = new LinkedHashSet<>();
        GameTimeEpochs.collectEpochDaysWhereMorningStartOccurred(
            from, to, morningStart, WorldTimeResource.ZONE_OFFSET, days
        );
        if (days.isEmpty()) {
            return;
        }
        for (long epochDay : days) {
            Long last = LAST_MORNING_REROLL_EPOCH_DAY.get(world.getName());
            if (last != null && last >= epochDay) {
                continue;
            }
            performMorningReroll(world, plugin, store, epochDay);
            LAST_MORNING_REROLL_EPOCH_DAY.put(world.getName(), epochDay);
        }
    }

    private static void tick(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
        var es = world.getEntityStore();
        Store<EntityStore> store = es != null ? es.getStore() : null;
        if (store == null) {
            return;
        }
        int morningStart = plugin.getConfig().get().getGameMorningStartHour();
        int morningEndEx = plugin.getConfig().get().getGameMorningEndHourExclusive();
        if (!AetherhavenMorningWindow.isGameMorning(wtr, morningStart, morningEndEx)) {
            return;
        }
        long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        String worldName = world.getName();
        Long last = LAST_MORNING_REROLL_EPOCH_DAY.get(worldName);
        if (last != null && last >= epochDay) {
            return;
        }
        LAST_MORNING_REROLL_EPOCH_DAY.put(worldName, epochDay);
        performMorningReroll(world, plugin, store, epochDay);
    }

    private static void performMorningReroll(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        long epochDay
    ) {
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        ShopPriceCatalog prices = plugin.getShopPriceCatalog();
        boolean changed = false;
        for (ShopSpotRecord record : registry.allRecords()) {
            if (record.isPlayerControlled()) {
                continue;
            }
            if (record.getStockEpochDay() == epochDay) {
                continue;
            }
            rerollNpcStock(record, plugin, prices);
            record.setStockEpochDay(epochDay);
            changed = true;
            TownRecord town = tm.getTown(record.getTownId());
            if (town != null) {
                ShopSpotDisplayService.syncDisplay(world, store, plugin, registry, record, town);
            }
        }
        if (changed) {
            ShopSpotPersistence.save(world, plugin, registry);
        }
    }

    public static void rerollNpcStock(@Nonnull ShopSpotRecord record, @Nonnull AetherhavenPlugin plugin, @Nonnull ShopPriceCatalog prices) {
        if (record.isPlayerControlled()) {
            return;
        }
        String tableId = record.getLootTableId();
        if (tableId.isBlank()) {
            record.setItemId(null);
            record.setStock(0);
            ShopSpotJewelrySupport.clearJewelryListing(record);
            return;
        }
        ShopLootTable table = ShopLootFiles.loadTable(plugin, tableId);
        ShopLootTable.Entry loot = table.rollEntryWithRetries(32);
        if (loot == null) {
            record.setItemId(null);
            record.setStock(0);
            ShopSpotJewelrySupport.clearJewelryListing(record);
            return;
        }
        String itemId = loot.getItemId();
        int min = loot.getStockMin();
        int max = loot.getStockMax();
        ShopPriceEntry entry = prices.getEntry(itemId);
        int rolled = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        int stock =
            entry.isBatched()
                ? entry.itemsForBatchCount(rolled)
                : rolled;
        record.setItemId(itemId);
        record.setStock(stock);
        // Rolled stock is new goods, even when the same item comes up again: nothing a player once listed here
        // (its metadata, its recorded price) may leak into it.
        record.setStackData(null);
        record.setQuote(null);
        if (ShopSpotJewelrySupport.isJewelryListing(itemId)) {
            ShopSpotJewelrySupport.prepareNpcJewelryListing(record, itemId, plugin);
        } else {
            ShopSpotJewelrySupport.clearJewelryListing(record);
        }
    }

    /** Fills an empty NPC stall when first configured; marks today so dawn does not reroll again until the next day. */
    public static void initialRollIfNeeded(
        @Nonnull ShopSpotRecord record,
        @Nonnull AetherhavenPlugin plugin,
        long epochDay
    ) {
        if (record.isPlayerControlled() || record.hasStock()) {
            return;
        }
        ShopPriceCatalog prices = plugin.getShopPriceCatalog();
        rerollNpcStock(record, plugin, prices);
        record.setStockEpochDay(epochDay);
    }
}
