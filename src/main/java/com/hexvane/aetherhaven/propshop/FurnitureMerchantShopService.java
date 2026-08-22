package com.hexvane.aetherhaven.propshop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteCatalog;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteDefinition;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteItemMetadata;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteShopPricing;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.prop.PropCatalog;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.prop.PropItemMetadata;
import com.hexvane.aetherhaven.prop.PropLoot;
import com.hexvane.aetherhaven.prop.PropLootExclusions;
import com.hexvane.aetherhaven.shopspot.ShopSpotBuyerPayment;
import com.hexvane.aetherhaven.time.AetherhavenMorningWindow;
import com.hexvane.aetherhaven.time.GameTimeEpochs;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Cap'n Clive's dialogue shop: six daily unique props and six block palettes, stock 5–10, rerolled at dawn per town.
 * Mutates {@link TownRecord} only (never Store from tick systems).
 */
public final class FurnitureMerchantShopService {
    public static final int SLOT_COUNT = 6;
    private static final int STOCK_MIN = 5;
    private static final int STOCK_MAX = 10;

    private static final ConcurrentHashMap<String, Long> LAST_MORNING_REROLL_EPOCH_DAY = new ConcurrentHashMap<>();

    private FurnitureMerchantShopService() {}

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
            performMorningReroll(world, plugin, epochDay);
            LAST_MORNING_REROLL_EPOCH_DAY.put(world.getName(), epochDay);
        }
    }

    private static void tick(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
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
        performMorningReroll(world, plugin, epochDay);
    }

    private static void performMorningReroll(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        long epochDay
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (town == null) {
                continue;
            }
            Long last = town.getFurnitureMerchantShopLastRerollEpochDay();
            if (last != null && last >= epochDay) {
                continue;
            }
            rerollTownInventory(plugin, town);
            town.setFurnitureMerchantShopLastRerollEpochDay(epochDay);
            tm.updateTown(town);
        }
    }

    /** Ensures the town has today's 6-slot inventory (rerolls if empty or never rolled). */
    public static void ensureInventory(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        long epochDay
    ) {
        Long last = town.getFurnitureMerchantShopLastRerollEpochDay();
        List<FurnitureMerchantShopSlotRecord> slots = town.getFurnitureMerchantShopSlots();
        List<FurnitureMerchantPaletteShopSlotRecord> paletteSlots = town.getFurnitureMerchantPaletteShopSlots();
        boolean empty = slots.isEmpty() || slots.stream().noneMatch(FurnitureMerchantShopSlotRecord::hasStock);
        boolean paletteEmpty =
            paletteSlots.isEmpty()
                || paletteSlots.stream().noneMatch(FurnitureMerchantPaletteShopSlotRecord::hasStock);
        if (last != null && last == epochDay && !empty && !paletteEmpty) {
            town.ensureFurnitureMerchantShopSlotCount(SLOT_COUNT);
            town.ensureFurnitureMerchantPaletteShopSlotCount(SLOT_COUNT);
            return;
        }
        rerollTownInventory(plugin, town);
        town.setFurnitureMerchantShopLastRerollEpochDay(epochDay);
        tm.updateTown(town);
    }

    public static void rerollTownInventory(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        town.ensureFurnitureMerchantShopSlotCount(SLOT_COUNT);
        List<FurnitureMerchantShopSlotRecord> slots = town.getFurnitureMerchantShopSlots();
        for (FurnitureMerchantShopSlotRecord slot : slots) {
            slot.clear();
        }
        PropCatalog catalog = plugin.getPropCatalog();
        List<PropDefinition> pool =
            new ArrayList<>(PropLoot.listEligible(catalog, PropLootExclusions.load(plugin)));
        if (pool.isEmpty()) {
            return;
        }
        Collections.shuffle(pool, ThreadLocalRandom.current());
        int n = Math.min(SLOT_COUNT, pool.size());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            PropDefinition def = pool.get(i);
            int stock = STOCK_MIN >= STOCK_MAX ? STOCK_MIN : rnd.nextInt(STOCK_MIN, STOCK_MAX + 1);
            FurnitureMerchantShopSlotRecord slot = slots.get(i);
            slot.setPropId(def.getId());
            slot.setStock(stock);
        }
        rerollPaletteInventory(plugin, town);
    }

    private static void rerollPaletteInventory(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        town.ensureFurnitureMerchantPaletteShopSlotCount(SLOT_COUNT);
        List<FurnitureMerchantPaletteShopSlotRecord> slots = town.getFurnitureMerchantPaletteShopSlots();
        for (FurnitureMerchantPaletteShopSlotRecord slot : slots) {
            slot.clear();
        }
        BlockPaletteCatalog catalog = plugin.getBlockPaletteCatalog();
        List<BlockPaletteDefinition> pool = new ArrayList<>(catalog.allById().values());
        if (pool.isEmpty()) {
            return;
        }
        Collections.shuffle(pool, ThreadLocalRandom.current());
        int n = Math.min(SLOT_COUNT, pool.size());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            BlockPaletteDefinition def = pool.get(i);
            int stock = STOCK_MIN >= STOCK_MAX ? STOCK_MIN : rnd.nextInt(STOCK_MIN, STOCK_MAX + 1);
            FurnitureMerchantPaletteShopSlotRecord slot = slots.get(i);
            slot.setPaletteId(def.getId());
            slot.setStock(stock);
        }
    }

    public record BuyResult(boolean ok, @Nullable String failLangKey) {
        @Nonnull
        public static BuyResult success() {
            return new BuyResult(true, null);
        }

        @Nonnull
        public static BuyResult fail(@Nonnull String key) {
            return new BuyResult(false, key);
        }
    }

    @Nonnull
    public static BuyResult tryBuy(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord shopTown,
        @Nonnull TownManager tm,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        int slotIndex
    ) {
        shopTown.ensureFurnitureMerchantShopSlotCount(SLOT_COUNT);
        List<FurnitureMerchantShopSlotRecord> slots = shopTown.getFurnitureMerchantShopSlots();
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.soldOut");
        }
        FurnitureMerchantShopSlotRecord slot = slots.get(slotIndex);
        if (!slot.hasStock()) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.soldOut");
        }
        PropDefinition def = plugin.getPropCatalog().get(slot.getPropId());
        if (def == null) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.soldOut");
        }
        long price = def.getGoldPrice();
        UUID buyer = playerRef.getUuid();
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, buyer);
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, buyer);
        CombinedItemContainer inv =
            InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        if (!GoldCoinPayment.canAfford(payerTown, inv, price, allowTreasury)) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.cannotAfford");
        }
        GoldCoinPayment.SpendBreakdown breakdown =
            GoldCoinPayment.trySpendReturningBreakdown(payerTown, inv, price, allowTreasury);
        if (breakdown == null) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.cannotAfford");
        }
        ItemStack grant = PropItemMetadata.createStack(def);
        ItemStackTransaction giveTx = player.giveItem(grant, ref, store);
        if (!giveTx.succeeded()) {
            GoldCoinPayment.refund(payerTown, player, ref, store, breakdown);
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.inventoryFull");
        }
        slot.setStock(slot.getStock() - 1);
        if (slot.getStock() <= 0) {
            slot.clear();
        }
        tm.updateTown(shopTown);
        if (payerTown != null && payerTown != shopTown) {
            tm.updateTown(payerTown);
        }
        return BuyResult.success();
    }

    @Nonnull
    public static BuyResult tryBuyPalette(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord shopTown,
        @Nonnull TownManager tm,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        int slotIndex
    ) {
        shopTown.ensureFurnitureMerchantPaletteShopSlotCount(SLOT_COUNT);
        List<FurnitureMerchantPaletteShopSlotRecord> slots = shopTown.getFurnitureMerchantPaletteShopSlots();
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.soldOut");
        }
        FurnitureMerchantPaletteShopSlotRecord slot = slots.get(slotIndex);
        if (!slot.hasStock()) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.soldOut");
        }
        BlockPaletteDefinition def = plugin.getBlockPaletteCatalog().get(slot.getPaletteId());
        if (def == null) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.soldOut");
        }
        long price = BlockPaletteShopPricing.goldPriceFor(def);
        UUID buyer = playerRef.getUuid();
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, buyer);
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, buyer);
        CombinedItemContainer inv =
            InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        if (!GoldCoinPayment.canAfford(payerTown, inv, price, allowTreasury)) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.cannotAfford");
        }
        GoldCoinPayment.SpendBreakdown breakdown =
            GoldCoinPayment.trySpendReturningBreakdown(payerTown, inv, price, allowTreasury);
        if (breakdown == null) {
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.cannotAfford");
        }
        ItemStack grant = BlockPaletteItemMetadata.createStack(def);
        ItemStackTransaction giveTx = player.giveItem(grant, ref, store);
        if (!giveTx.succeeded()) {
            GoldCoinPayment.refund(payerTown, player, ref, store, breakdown);
            return BuyResult.fail("aetherhaven_prop_shop.aetherhaven.propShop.error.inventoryFull");
        }
        slot.setStock(slot.getStock() - 1);
        if (slot.getStock() <= 0) {
            slot.clear();
        }
        tm.updateTown(shopTown);
        if (payerTown != null && payerTown != shopTown) {
            tm.updateTown(payerTown);
        }
        return BuyResult.success();
    }
}
