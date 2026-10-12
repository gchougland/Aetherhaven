package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.TownLogEntry;
import com.hexvane.aetherhaven.town.TownLogMessage;
import com.hexvane.aetherhaven.town.TownLogService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ShopSpotPurchaseService {
    private static final String MSG = "aetherhaven_shop.aetherhaven.shop";

    private ShopSpotPurchaseService() {}

    public static void handleBuy(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context,
        @Nonnull Vector3i targetBlock,
        int batchCount
    ) {
        if (!executeBuy(playerRef, commandBuffer.getStore(), commandBuffer, context, targetBlock, batchCount)) {
            fail(context);
        } else {
            context.getState().state = InteractionState.Finished;
        }
    }

    /** Buy flow from the quantity picker UI (no interaction tick / command buffer). */
    public static boolean handleBuyFromUi(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i targetBlock,
        int batchCount
    ) {
        return executeBuy(playerRef, store, null, null, targetBlock, batchCount);
    }

    private static boolean executeBuy(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nullable InteractionContext context,
        @Nonnull Vector3i targetBlock,
        int batchCount
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ShopSpotRecord record = resolveRecord(world, registry, targetBlock);
        if (record == null) {
            return false;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(record.getTownId());
        if (town == null) {
            return false;
        }
        if (!ShopSpotOpenService.isOpen(record, town, world, store)) {
            String key =
                ShopSpotOpenService.isGameDay(store) ? MSG + ".closed" : MSG + ".closedNight";
            notify(playerRef, store, commandBuffer, Message.translation(key));
            return false;
        }
        String itemId = record.getItemId();
        if (itemId == null || record.getStock() <= 0) {
            return false;
        }
        ShopPriceEntry entry = ShopSpotPricing.entry(plugin, record);
        int maxBatches = entry.batchCountFromItemStock(record.getStock());
        int buyBatches = Math.max(1, Math.min(batchCount, maxBatches));
        int itemQty = entry.itemsForBatchCount(buyBatches);
        if (itemQty <= 0 || itemQty > record.getStock()) {
            return false;
        }
        long goldPerBatch = ShopSpotPricing.goldPerBatch(plugin, record, itemId);
        long totalCost = ShopSpotPricing.totalCost(goldPerBatch, buyBatches);
        Player player = getPlayer(playerRef, store, commandBuffer);
        PlayerRef pr = getPlayerRef(playerRef, store, commandBuffer);
        UUIDComponent uc = getUuid(playerRef, store, commandBuffer);
        if (player == null || pr == null || uc == null) {
            return false;
        }
        UUID buyer = uc.getUuid();
        if (record.isPlayerControlled()
            && record.getSellerUuid() != null
            && record.getSellerUuid().equals(buyer)) {
            notify(playerRef, store, commandBuffer, Message.translation(MSG + ".cannotBuyOwnListing"));
            return false;
        }
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, buyer);
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, buyer);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.HOTBAR_FIRST);
        if (!GoldCoinPayment.canAfford(payerTown, inv, totalCost, allowTreasury)) {
            notify(playerRef, store, commandBuffer, Message.translation(MSG + ".cannotAfford"));
            return false;
        }
        GoldCoinPayment.SpendBreakdown breakdown =
            GoldCoinPayment.trySpendReturningBreakdown(payerTown, inv, totalCost, allowTreasury);
        if (breakdown == null) {
            notify(playerRef, store, commandBuffer, Message.translation(MSG + ".cannotAfford"));
            return false;
        }
        ItemStack grant = ShopSpotJewelrySupport.buildListingStack(itemId, itemQty, record);
        ShopSpotItemDelivery.Result delivery =
            ShopSpotItemDelivery.grantAtShop(
                player,
                playerRef,
                commandBuffer != null ? commandBuffer : store,
                commandBuffer,
                grant,
                targetBlock
            );
        if (!delivery.succeeded()) {
            GoldCoinPayment.refund(payerTown, player, playerRef, store, breakdown);
            if (payerTown != null) {
                tm.updateTown(payerTown);
            }
            return false;
        }
        if (delivery.droppedOnGround()) {
            notify(playerRef, store, commandBuffer, Message.translation(MSG + ".itemsDropped"));
        }
        if (payerTown != null) {
            tm.updateTown(payerTown);
        }
        if (record.isPlayerControlled()) {
            creditSellerPayout(plugin, town, record, totalCost);
            TownLogService.appendEntry(
                town,
                new TownLogEntry(
                    VillagerReputationService.currentGameEpochDay(store),
                    TownLogService.KEY_SHOP_SALE,
                    TownLogMessage.shopSaleParams(pr.getUsername(), itemId, Integer.toString(itemQty), Long.toString(totalCost))
                )
            );
            tm.updateTown(town);
        }
        notifySellerIfNeeded(record, buyer, pr.getUsername(), ShopListingText.name(record), itemQty, totalCost, world);
        ShopListing.sold(record, itemQty);
        registry.put(record);
        ShopSpotPersistence.save(world, plugin, registry);
        if (record.isPlayerControlled() && com.hexvane.aetherhaven.autonomy.ShopClerkWorkService.completedSale(store,town,record.getPlotId()) > 0) {
            tm.updateTown(town);
        }
        if (commandBuffer != null) {
            ShopSpotDisplayService.syncDisplay(world, store, commandBuffer, plugin, registry, record, town);
        } else {
            world.execute(() -> {
                Store<EntityStore> s = world.getEntityStore().getStore();
                if (s != null) {
                    ShopSpotDisplayService.syncDisplay(world, s, null, plugin, registry, record, town);
                }
            });
        }
        ShopSpotHudRefresh.refreshFocused(playerRef, store, world, plugin);
        return true;
    }

    @Nullable
    private static Player getPlayer(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer != null) {
            return commandBuffer.getComponent(playerRef, Player.getComponentType());
        }
        return store.getComponent(playerRef, Player.getComponentType());
    }

    @Nullable
    private static PlayerRef getPlayerRef(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer != null) {
            return commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        }
        return store.getComponent(playerRef, PlayerRef.getComponentType());
    }

    @Nullable
    private static UUIDComponent getUuid(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer != null) {
            return commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        }
        return store.getComponent(playerRef, UUIDComponent.getComponentType());
    }

    public static void handleListOrRemove(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context,
        @Nonnull Vector3i targetBlock,
        boolean secondary
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            fail(context);
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        World world = store.getExternalData().getWorld();
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ShopSpotRecord record = resolveRecord(world, registry, targetBlock);
        if (record == null || !record.isPlayerControlled()) {
            fail(context);
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(record.getTownId());
        if (town == null) {
            fail(context);
            return;
        }
        UUIDComponent uc = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            fail(context);
            return;
        }
        UUID playerUuid = uc.getUuid();
        if (!town.hasMemberOrOwner(playerUuid)) {
            notify(playerRef, commandBuffer, Message.translation(MSG + ".notTownMember"));
            fail(context);
            return;
        }
        if (record.hasStock() && record.getSellerUuid() != null && record.getSellerUuid().equals(playerUuid)) {
            removeListing(playerRef, commandBuffer, context, world, plugin, registry, record, town, targetBlock);
            return;
        }
        if (record.hasStock()) {
            fail(context);
            return;
        }
        if (!town.playerCanUseShopSpots(playerUuid)) {
            String key =
                town.hasMemberOrOwner(playerUuid) ? MSG + ".noShopSpotPermission" : MSG + ".notTownMember";
            notify(playerRef, commandBuffer, Message.translation(key));
            fail(context);
            return;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            fail(context);
            return;
        }
        PlayerRef sellerRef = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        Listed listed =
            listFromSlot(
                plugin.getShopPriceCatalog(),
                record,
                hotbar.getInventory(),
                hotbar.getActiveSlot(),
                playerUuid,
                sellerRef != null ? sellerRef.getUsername() : null
            );
        if (!listed.done()) {
            if (listed.refusal() != null) {
                notify(
                    playerRef,
                    commandBuffer,
                    Message.translation(MSG + "." + listed.refusal()).param("count", String.valueOf(listed.lot()))
                );
            }
            fail(context);
            return;
        }
        registry.put(record);
        ShopSpotPersistence.save(world, plugin, registry);
        ShopSpotDisplayService.syncDisplay(world, store, commandBuffer, plugin, registry, record, town);
        if (listed.kept() > 0) {
            notify(
                playerRef,
                commandBuffer,
                Message.translation(MSG + ".listedPartial")
                    .param("listed", String.valueOf(listed.listed()))
                    .param("kept", String.valueOf(listed.kept()))
            );
        } else {
            notify(playerRef, commandBuffer, Message.translation(MSG + ".listed"));
        }
        context.getState().state = InteractionState.Finished;
    }

    /**
     * What came of an attempt to list.
     *
     * @param listed how many items went on the stall; zero when nothing was listed
     * @param kept how many stayed in the slot because they did not fill a whole lot
     * @param refusal lang key suffix to tell the seller, or null when there is nothing to say
     * @param lot the lot size of the item, for the message that asks for more
     */
    public record Listed(int listed, int kept, @Nullable String refusal, int lot) {
        public boolean done() {
            return listed > 0;
        }

        static Listed refused(@Nullable String refusal, int lot) {
            return new Listed(0, 0, refusal, lot);
        }
    }

    /**
     * Moves the stack in one inventory slot onto an empty player stall: the price gate, whole lots only, the stack
     * taken out of the slot and kept whole on the stall with the price it went up at. The caller has checked who may
     * sell here, and saves and shows the stall afterwards.
     */
    @Nonnull
    public static Listed listFromSlot(
        @Nonnull ShopPriceCatalog prices,
        @Nonnull ShopSpotRecord record,
        @Nonnull ItemContainer container,
        short slot,
        @Nonnull UUID seller,
        @Nullable String sellerName
    ) {
        ItemStack hand = slot >= 0 && slot < container.getCapacity() ? container.getItemStack(slot) : null;
        if (ItemStack.isEmpty(hand)) {
            return Listed.refused(null, 1);
        }
        String itemId = hand.getItemId();
        if (!prices.hasExplicitPrice(itemId)) {
            return Listed.refused("noPrice", 1);
        }
        ShopPriceEntry entry = prices.getEntry(itemId);
        int handQty = hand.getQuantity();
        int listQty = ShopPriceEntry.alignItemStockToBatches(handQty, entry.getBatchSize());
        if (listQty <= 0) {
            return Listed.refused("wrongBatchSize", entry.getBatchSize());
        }
        if (ShopSpotJewelrySupport.isJewelryListing(itemId)
            && !ShopSpotJewelrySupport.capturePlayerJewelryListing(record, hand)) {
            return Listed.refused("jewelryNeedsAppraisal", 1);
        }
        if (!ShopSpotJewelrySupport.isJewelryListing(itemId)) {
            ShopSpotJewelrySupport.clearJewelryListing(record);
        }
        if (!takeFromSlot(container, slot, hand, listQty)) {
            return Listed.refused(null, 1);
        }
        // The stall keeps the stack itself, with its metadata and wear, and the price it went up at.
        ShopListing.list(record, hand, listQty, entry);
        record.setSellerUuid(seller);
        if (sellerName != null) {
            record.setSellerName(sellerName);
        }
        return new Listed(listQty, handQty - listQty, null, entry.getBatchSize());
    }

    private static void removeListing(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record,
        @Nonnull TownRecord town,
        @Nonnull Vector3i shopBlock
    ) {
        String itemId = record.getItemId();
        int stock = record.getStock();
        if (itemId == null || stock <= 0) {
            fail(context);
            return;
        }
        Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());
        Store<EntityStore> store = commandBuffer.getStore();
        if (player == null) {
            fail(context);
            return;
        }
        ItemStack stack = ShopSpotJewelrySupport.buildListingStack(itemId, stock, record);
        ShopSpotItemDelivery.Result delivery =
            ShopSpotItemDelivery.grantAtShop(player, playerRef, commandBuffer, commandBuffer, stack, shopBlock);
        if (!delivery.succeeded()) {
            notify(playerRef, commandBuffer, Message.translation(MSG + ".inventoryFull"));
            fail(context);
            return;
        }
        if (delivery.droppedOnGround()) {
            notify(playerRef, commandBuffer, Message.translation(MSG + ".itemsDropped"));
        }
        ShopListing.clear(record);
        registry.put(record);
        ShopSpotPersistence.save(world, plugin, registry);
        ShopSpotDisplayService.syncDisplay(world, store, commandBuffer, plugin, registry, record, town);
        notify(playerRef, commandBuffer, Message.translation(MSG + ".removedListing"));
        context.getState().state = InteractionState.Finished;
    }

    /** Credits listing revenue to the player shop safe or town treasury depending on plot type. */
    public static void creditSellerPayout(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull ShopSpotRecord record,
        long totalCost
    ) {
        if (totalCost <= 0L || !record.isPlayerControlled()) {
            return;
        }
        UUID seller = record.getSellerUuid();
        if (seller == null) {
            return;
        }
        if (isPlayerShopPlot(plugin, town, record.getPlotId())) {
            town.addPlayerShopSafeGold(seller, totalCost);
        } else {
            town.addTreasuryGoldCoins(totalCost);
        }
    }

    public static boolean isPlayerShopPlot(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nullable UUID plotId
    ) {
        if (plotId == null) {
            return false;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null) {
            return false;
        }
        String gid = plugin.getConstructionCatalog().resolveGameplayConstructionId(plot.getConstructionId());
        return AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP.equals(gid);
    }

    /** @param itemName the listing's own name, see {@link ShopListingText#name(ShopSpotRecord)} */
    public static void notifySellerIfNeeded(
        @Nonnull ShopSpotRecord record,
        @Nonnull UUID buyerUuid,
        @Nonnull String buyerName,
        @Nonnull Message itemName,
        int itemQty,
        long gold,
        @Nonnull World world
    ) {
        if (!record.isPlayerControlled()) {
            return;
        }
        UUID seller = record.getSellerUuid();
        if (seller == null || seller.equals(buyerUuid)) {
            return;
        }
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr.getUuid().equals(seller)) {
                NotificationUtil.sendNotification(
                    pr.getPacketHandler(),
                    Message.translation(MSG + ".soldNotify")
                        .param("buyer", buyerName)
                        .param("item", itemName)
                        .param("count", String.valueOf(itemQty))
                        .param("gold", String.valueOf(gold)),
                    NotificationStyle.Success
                );
                break;
            }
        }
    }

    @Nullable
    private static ShopSpotRecord resolveRecord(
        @Nonnull World world,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull Vector3i pos
    ) {
        UUID spotId = ShopSpotBlockUtil.spotIdAt(world, pos);
        if (spotId != null) {
            ShopSpotRecord byId = registry.get(spotId);
            if (byId != null) {
                return byId;
            }
        }
        return registry.getAtBlock(pos.x, pos.y, pos.z);
    }

    private static void notify(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Message msg
    ) {
        PlayerRef pr = getPlayerRef(playerRef, store, commandBuffer);
        if (pr != null) {
            NotificationUtil.sendNotification(pr.getPacketHandler(), msg, NotificationStyle.Warning);
        }
    }

    private static void notify(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Message msg
    ) {
        notify(playerRef, commandBuffer.getStore(), commandBuffer, msg);
    }

    private static boolean removeFromActiveHotbarStack(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull InventoryComponent.Hotbar hotbar,
        @Nonnull ItemStack inHand,
        int removeQty
    ) {
        return takeFromSlot(hotbar.getInventory(), hotbar.getActiveSlot(), inHand, removeQty);
    }

    /** Takes {@code removeQty} of the stack out of the slot. What is left keeps everything the stack carried. */
    private static boolean takeFromSlot(@Nonnull ItemContainer container, short slot, @Nonnull ItemStack inHand, int removeQty) {
        if (removeQty <= 0 || slot < 0) {
            return false;
        }
        int have = inHand.getQuantity();
        if (removeQty > have) {
            return false;
        }
        ItemStack replacement =
            removeQty >= have
                ? ItemStack.EMPTY
                : (inHand.withQuantity(have - removeQty) != null
                    ? inHand.withQuantity(have - removeQty)
                    : ItemStack.EMPTY);
        // Only items that really left the slot go on the stall. Otherwise they would exist twice.
        return container.replaceItemStackInSlot(slot, inHand, replacement).succeeded();
    }

    private static boolean clearActiveHotbarStack(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull InventoryComponent.Hotbar hotbar,
        @Nonnull ItemStack inHand
    ) {
        return removeFromActiveHotbarStack(playerRef, store, hotbar, inHand, inHand.getQuantity());
    }

    private static void fail(@Nonnull InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }
}
