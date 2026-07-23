package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.shopspot.ShopPriceEntry;
import com.hexvane.aetherhaven.shopspot.ShopSpotBuyerPayment;
import com.hexvane.aetherhaven.shopspot.ShopSpotBlockInteractSupport;
import com.hexvane.aetherhaven.shopspot.ShopSpotHudRefresh;
import com.hexvane.aetherhaven.shopspot.ShopSpotOpenService;
import com.hexvane.aetherhaven.shopspot.ShopSpotPlayerComponent;
import com.hexvane.aetherhaven.shopspot.ShopSpotPricing;
import com.hexvane.aetherhaven.shopspot.ShopSpotPurchaseService;
import com.hexvane.aetherhaven.shopspot.ShopSpotRecord;
import com.hexvane.aetherhaven.shopspot.ShopSpotRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ShopSpotBuyPage extends AetherhavenInteractiveCustomUIPage<ShopSpotBuyPage.PageData> {
    private static final String MSG = "aetherhaven_shop.aetherhaven.shop.buy";
    private static final String PRICE_COLOR_OK = "#dce4ec";
    private static final String PRICE_COLOR_UNAFFORDABLE = "#e8a0a0";
    private static final String QTY_COLOR_OK = "#e8dcc8";
    private static final String QTY_COLOR_UNAFFORDABLE = "#e8a0a0";

    private boolean templateAppended;
    private int buyBatches = 1;
    private int maxBatches = 1;
    private boolean batched;
    @Nonnull
    private final Vector3i targetBlock;
    @Nonnull
    private final UUID spotId;

    public ShopSpotBuyPage(@Nonnull PlayerRef playerRef, @Nonnull UUID spotId, @Nonnull Vector3i targetBlock) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.spotId = spotId;
        this.targetBlock = new Vector3i(targetBlock);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/ShopSpotBuyPage.ui");
            templateAppended = true;
            wireEvents(eventBuilder);
        }
        applyLabels(commandBuilder);
        refreshQty(commandBuilder, store, ref);
    }

    private void wireEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Dec1Btn", EventData.of("Action", "Dec1"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Dec10Btn", EventData.of("Action", "Dec10"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Inc1Btn", EventData.of("Action", "Inc1"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Inc10Btn", EventData.of("Action", "Inc10"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MaxBtn", EventData.of("Action", "Max"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyBtn", EventData.of("Action", "Buy"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CancelBtn", EventData.of("Action", "Cancel"), false);
    }

    private void applyLabels(@Nonnull UICommandBuilder b) {
        b.set("#BuyTitle.TextSpans", Message.translation(MSG + ".title"));
        b.set("#Dec10Hint.TextSpans", Message.translation(MSG + ".step10"));
        b.set("#Inc10Hint.TextSpans", Message.translation(MSG + ".step10"));
        b.set("#MaxBtn.TextSpans", Message.translation(MSG + ".max"));
        b.set("#BuyBtn.TextSpans", Message.translation(MSG + ".buy"));
        b.set("#CancelBtn.TextSpans", Message.translation(MSG + ".cancel"));
    }

    private void refreshQty(
        @Nonnull UICommandBuilder b,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        ShopSpotRecord record = resolveRecord(world, plugin);
        if (record == null || plugin == null) {
            b.set("#QtyValue.TextSpans", Message.raw("1"));
            b.set("#BuyBtn.Disabled", true);
            return;
        }
        String itemId = record.getItemId();
        if (itemId == null) {
            b.set("#QtyValue.TextSpans", Message.raw("1"));
            b.set("#BuyBtn.Disabled", true);
            return;
        }
        ShopPriceEntry entry = ShopSpotPricing.catalogEntry(plugin, itemId);
        batched = entry.isBatched();
        maxBatches = Math.max(1, entry.batchCountFromItemStock(record.getStock()));
        buyBatches = Math.max(1, Math.min(maxBatches, buyBatches));
        Message itemName = UiMaterialLabels.itemNameMessage(itemId);
        b.set("#ItemLine.TextSpans", Message.translation(MSG + ".item").param("item", itemName));
        long gold = ShopSpotPricing.goldPerBatch(plugin, record, itemId);
        long total = ShopSpotPricing.totalCost(gold, buyBatches);
        boolean canAfford = playerCanAfford(playerRef, store, record, total);
        b.set("#BuyBtn.Disabled", !canAfford);
        b.set("#PriceLine.Style.TextColor", canAfford ? PRICE_COLOR_OK : PRICE_COLOR_UNAFFORDABLE);
        b.set("#QtyValue.Style.TextColor", canAfford ? QTY_COLOR_OK : QTY_COLOR_UNAFFORDABLE);
        if (batched) {
            b.set(
                "#PriceLine.TextSpans",
                Message.translation(MSG + ".priceBatch")
                    .param("gold", String.valueOf(gold))
                    .param("count", String.valueOf(entry.getBatchSize()))
                    .param("item", itemName)
            );
            b.set(
                "#StockLine.TextSpans",
                Message.translation(MSG + ".stockBatches")
                    .param("batches", String.valueOf(maxBatches))
                    .param("items", String.valueOf(record.getStock()))
            );
            b.set(
                "#QtyValue.TextSpans",
                Message.translation(MSG + ".qtyBatches")
                    .param("n", String.valueOf(buyBatches))
                    .param("total", String.valueOf(total))
            );
        } else {
            b.set("#PriceLine.TextSpans", Message.translation(MSG + ".price").param("gold", String.valueOf(gold)));
            b.set("#StockLine.TextSpans", Message.translation(MSG + ".stock").param("n", String.valueOf(record.getStock())));
            b.set(
                "#QtyValue.TextSpans",
                Message.translation(MSG + ".qty").param("n", String.valueOf(buyBatches)).param("total", String.valueOf(total))
            );
        }
    }

    private int maxAffordableBatches(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        ShopSpotRecord record = resolveRecord(world, plugin);
        if (record == null || plugin == null) {
            return Math.max(1, buyBatches);
        }
        String itemId = record.getItemId();
        if (itemId == null) {
            return Math.max(1, buyBatches);
        }
        ShopPriceEntry entry = ShopSpotPricing.catalogEntry(plugin, itemId);
        int stockCap = Math.max(1, entry.batchCountFromItemStock(record.getStock()));
        long goldPerBatch = ShopSpotPricing.goldPerBatch(plugin, record, itemId);
        if (goldPerBatch <= 0L) {
            return stockCap;
        }
        long available = playerAvailableGold(playerRef, store, record);
        long affordableLong = available / goldPerBatch;
        int affordable = affordableLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) affordableLong;
        affordable = Math.min(stockCap, affordable);
        return affordable <= 0 ? 1 : affordable;
    }

    private static long playerAvailableGold(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRecord record
    ) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (player == null || uc == null || plugin == null) {
            return 0L;
        }
        World world = store.getExternalData().getWorld();
        TownRecord payerTown =
            ShopSpotBuyerPayment.buyerHomeTown(
                AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin),
                uc.getUuid()
            );
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, uc.getUuid());
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.HOTBAR_FIRST);
        return GoldCoinPayment.totalAvailable(payerTown, inv, allowTreasury);
    }

    private static boolean playerCanAfford(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRecord record,
        long totalCost
    ) {
        if (totalCost <= 0L) {
            return true;
        }
        return playerAvailableGold(playerRef, store, record) >= totalCost;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "Dec1" -> buyBatches = Math.max(1, buyBatches - 1);
            case "Dec10" -> buyBatches = Math.max(1, buyBatches - 10);
            case "Inc1" -> buyBatches = Math.min(maxBatches, buyBatches + 1);
            case "Inc10" -> buyBatches = Math.min(maxBatches, buyBatches + 10);
            case "Max" -> buyBatches = maxAffordableBatches(ref, store);
            case "Buy" -> {
                executeBuy(ref, store);
                return;
            }
            case "Cancel" -> {
                close();
                return;
            }
            default -> {
                return;
            }
        }
        UICommandBuilder b = new UICommandBuilder();
        refreshQty(b, store, ref);
        sendUpdate(b, null, false);
    }

    private void executeBuy(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            close();
            return;
        }
        World world = store.getExternalData().getWorld();
        if (!ShopSpotPurchaseService.handleBuyFromUi(ref, store, targetBlock, buyBatches)) {
            UICommandBuilder b = new UICommandBuilder();
            refreshQty(b, store, ref);
            sendUpdate(b, null, false);
            return;
        }
        close();
    }

    @Nullable
    private ShopSpotRecord resolveRecord(@Nonnull World world, @Nullable AetherhavenPlugin plugin) {
        if (plugin == null) {
            return null;
        }
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ShopSpotRecord record = registry.get(spotId);
        if (record == null) {
            record = ShopSpotBlockInteractSupport.resolveRecord(world, plugin, targetBlock);
        }
        return record;
    }

    public static boolean tryOpen(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3i targetBlock
    ) {
        Store<EntityStore> store = commandBuffer.getStore();
        ShopSpotRecord record = ShopSpotBlockInteractSupport.resolveRecord(world, plugin, targetBlock);
        if (record == null || ShopSpotBlockInteractSupport.isConfiguringPendingSpot(playerRef, store, record)) {
            return false;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(record.getTownId());
        if (town == null) {
            return false;
        }
        PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        if (!record.hasStock()) {
            if (pr != null) {
                if (record.isPlayerControlled()) {
                    UUIDComponent uc = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
                    if (uc != null && town.playerCanUseShopSpots(uc.getUuid())) {
                        pr.sendMessage(Message.translation("aetherhaven_shop.aetherhaven.shop.holdItemToList"));
                    }
                } else {
                    pr.sendMessage(Message.translation("aetherhaven_shop.aetherhaven.shop.closed"));
                }
            }
            return false;
        }
        if (!ShopSpotOpenService.isOpen(record, town, world, store)) {
            if (pr != null) {
                String key = ShopSpotOpenService.isGameDay(store)
                    ? "aetherhaven_shop.aetherhaven.shop.closed"
                    : "aetherhaven_shop.aetherhaven.shop.closedNight";
                pr.sendMessage(Message.translation(key));
            }
            return false;
        }
        UUIDComponent uc = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        if (record.isPlayerControlled()
            && record.getSellerUuid() != null
            && uc != null
            && record.getSellerUuid().equals(uc.getUuid())) {
            return false;
        }
        Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());
        if (player == null || pr == null || player.getPageManager().getCustomPage() != null) {
            return false;
        }
        ShopSpotPlayerComponent st = commandBuffer.getComponent(playerRef, ShopSpotPlayerComponent.getComponentType());
        if (st == null) {
            st = new ShopSpotPlayerComponent();
        }
        st.setFocusedSpotId(record.getSpotId());
        commandBuffer.putComponent(playerRef, ShopSpotPlayerComponent.getComponentType(), st);
        player.getPageManager().openCustomPage(playerRef, store, new ShopSpotBuyPage(pr, record.getSpotId(), targetBlock));
        return true;
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        @Nullable
        private String action;
    }
}
