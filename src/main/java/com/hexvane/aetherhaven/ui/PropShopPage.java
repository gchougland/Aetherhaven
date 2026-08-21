package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.plot.PlotCraftingPrefabPreview;
import com.hexvane.aetherhaven.plot.PlotCraftingPrefabPreviewClientMode;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.propshop.FurnitureMerchantShopService;
import com.hexvane.aetherhaven.propshop.FurnitureMerchantShopSlotRecord;
import com.hexvane.aetherhaven.shopspot.ShopSpotBuyerPayment;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Cap'n Clive dialogue shop: list/grid of today's props with prefab preview and buy. */
public final class PropShopPage extends AetherhavenInteractiveCustomUIPage<PropShopPage.PageData> {
    private static final String MSG = "aetherhaven_prop_shop.aetherhaven.propShop";
    private static final String ROWS = "#PropListScroll #PropRows";
    private static final String GRID = "#PropListScroll #PropGridRows";

    private enum ViewMode {
        LIST,
        GRID
    }

    @Nonnull
    private final UUID townId;
    private boolean templateAppended;
    private boolean clientCreativeSpoofed;
    @Nonnull
    private ViewMode viewMode = ViewMode.LIST;
    @Nonnull
    private String searchQuery = "";
    private int selectedSlot = -1;
    @Nonnull
    private List<Integer> visibleSlotIndices = List.of();

    public PropShopPage(@Nonnull PlayerRef playerRef, @Nonnull UUID townId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PropShopPage.ui");
            templateAppended = true;
            wireStaticEvents(eventBuilder);
        }
        commandBuilder.set("#PropShopTitle.TextSpans", Message.translation(MSG + ".title"));
        commandBuilder.set("#PreviewTitle.TextSpans", Message.translation(MSG + ".previewTitle"));
        commandBuilder.set("#EmptyHint.TextSpans", Message.translation(MSG + ".empty"));
        commandBuilder.set("#PreviewPlaceholder.TextSpans", Message.translation(MSG + ".previewHint"));
        commandBuilder.set("#SearchInput.Value", searchQuery);
        commandBuilder.set("#BuyButton.TextSpans", Message.translation(MSG + ".buy"));
        bindBrowser(commandBuilder, eventBuilder, store, ref);
        schedulePrefabPreviewWithRetries(ref, store);
    }

    private void wireStaticEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SearchInput",
            EventData.of("@SearchQuery", "#SearchInput.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ViewModeList",
            EventData.of("Action", "ViewModeList"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ViewModeGrid",
            EventData.of("Action", "ViewModeGrid"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BuyButton",
            EventData.of("Action", "Buy"),
            false
        );
    }

    private void bindBrowser(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        commandBuilder.clear(ROWS);
        commandBuilder.clear(GRID);
        if (plugin == null || world == null) {
            commandBuilder.set("#EmptyHint.Visible", true);
            commandBuilder.set("#BuyButton.Disabled", true);
            commandBuilder.set("#SelectedName.TextSpans", Message.raw(""));
            commandBuilder.set("#PriceLine.TextSpans", Message.raw(""));
            commandBuilder.set("#StockLine.TextSpans", Message.raw(""));
            commandBuilder.set("#FundsLine.TextSpans", Message.raw(""));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            commandBuilder.set("#EmptyHint.Visible", true);
            commandBuilder.set("#BuyButton.Disabled", true);
            commandBuilder.set("#SelectedName.TextSpans", Message.raw(""));
            commandBuilder.set("#PriceLine.TextSpans", Message.raw(""));
            commandBuilder.set("#StockLine.TextSpans", Message.raw(""));
            commandBuilder.set("#FundsLine.TextSpans", Message.raw(""));
            return;
        }
        long epochDay = epochDay(store);
        FurnitureMerchantShopService.ensureInventory(plugin, town, tm, epochDay);
        town.ensureFurnitureMerchantShopSlotCount(FurnitureMerchantShopService.SLOT_COUNT);
        List<FurnitureMerchantShopSlotRecord> slots = town.getFurnitureMerchantShopSlots();
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            FurnitureMerchantShopSlotRecord slot = slots.get(i);
            if (!slot.hasStock()) {
                continue;
            }
            PropDefinition def = plugin.getPropCatalog().get(slot.getPropId());
            if (def == null) {
                continue;
            }
            if (!matchesSearch(def)) {
                continue;
            }
            visible.add(i);
        }
        visibleSlotIndices = List.copyOf(visible);
        if (selectedSlot >= 0 && !visibleSlotIndices.contains(selectedSlot)) {
            selectedSlot = visibleSlotIndices.isEmpty() ? -1 : visibleSlotIndices.get(0);
        } else if (selectedSlot < 0 && !visibleSlotIndices.isEmpty()) {
            selectedSlot = visibleSlotIndices.get(0);
        }

        boolean grid = viewMode == ViewMode.GRID;
        commandBuilder.set("#PropRows.Visible", !grid);
        commandBuilder.set("#PropGridRows.Visible", grid);
        commandBuilder.set("#ViewModeList.Disabled", !grid);
        commandBuilder.set("#ViewModeGrid.Disabled", grid);
        commandBuilder.set("#EmptyHint.Visible", visibleSlotIndices.isEmpty());

        String rowSelector = grid ? GRID : ROWS;
        String rowDoc = grid ? "Aetherhaven/PlotCraftingBuildingGridCell.ui" : "Aetherhaven/PlotCraftingBuildingRow.ui";
        for (int vi = 0; vi < visibleSlotIndices.size(); vi++) {
            int slotIndex = visibleSlotIndices.get(vi);
            FurnitureMerchantShopSlotRecord slot = slots.get(slotIndex);
            PropDefinition def = plugin.getPropCatalog().get(slot.getPropId());
            if (def == null) {
                continue;
            }
            commandBuilder.append(rowSelector, rowDoc);
            String row = rowSelector + "[" + vi + "]";
            String icon = PropIconPath.forPropId(def.getId(), plugin.getDataDirectory());
            commandBuilder.set(row + " #BuildingIcon.AssetPath", icon);
            if (!grid) {
                commandBuilder.set(row + " #BuildingName.TextSpans", Message.raw(def.getDisplayName()));
                commandBuilder.set(row + " #BuildingCreator.Visible", false);
                commandBuilder.set(row + " #FavoriteButtonOn.Visible", false);
                commandBuilder.set(row + " #FavoriteButtonOff.Visible", false);
            } else {
                commandBuilder.set(row + " #Select.TooltipText", def.getDisplayName());
                commandBuilder.set(row + " #FavoriteButtonOn.Visible", false);
                commandBuilder.set(row + " #FavoriteButtonOff.Visible", false);
            }
            boolean selected = slotIndex == selectedSlot;
            commandBuilder.set(row + " #SelectHilite.Visible", selected);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                new EventData().append("Action", "Select").append("Slot", String.valueOf(slotIndex)),
                false
            );
        }

        applySelectionLabels(commandBuilder, plugin, town, store, ref);
    }

    private void applySelectionLabels(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        bindFundsLine(commandBuilder, store, ref, plugin);
        if (selectedSlot < 0) {
            commandBuilder.set("#SelectedName.TextSpans", Message.translation(MSG + ".pickOne"));
            commandBuilder.set("#PriceLine.TextSpans", Message.raw(""));
            commandBuilder.set("#StockLine.TextSpans", Message.raw(""));
            commandBuilder.set("#BuyButton.Disabled", true);
            commandBuilder.set("#PreviewPlaceholder.Visible", true);
            return;
        }
        List<FurnitureMerchantShopSlotRecord> slots = town.getFurnitureMerchantShopSlots();
        if (selectedSlot >= slots.size()) {
            commandBuilder.set("#BuyButton.Disabled", true);
            return;
        }
        FurnitureMerchantShopSlotRecord slot = slots.get(selectedSlot);
        PropDefinition def = plugin.getPropCatalog().get(slot.getPropId());
        if (def == null || !slot.hasStock()) {
            commandBuilder.set("#SelectedName.TextSpans", Message.translation(MSG + ".soldOut"));
            commandBuilder.set("#PriceLine.TextSpans", Message.raw(""));
            commandBuilder.set("#StockLine.TextSpans", Message.raw(""));
            commandBuilder.set("#BuyButton.Disabled", true);
            commandBuilder.set("#PreviewPlaceholder.Visible", true);
            return;
        }
        long price = def.getGoldPrice();
        commandBuilder.set("#SelectedName.TextSpans", Message.raw(def.getDisplayName()));
        commandBuilder.set(
            "#PriceLine.TextSpans",
            Message.translation(MSG + ".price").param("gold", String.valueOf(price))
        );
        commandBuilder.set(
            "#StockLine.TextSpans",
            Message.translation(MSG + ".stock").param("n", String.valueOf(slot.getStock()))
        );
        boolean canAfford = playerCanAfford(store, ref, plugin, price);
        commandBuilder.set("#BuyButton.Disabled", !canAfford);
        commandBuilder.set("#BuyButton.TextSpans", Message.translation(MSG + ".buy"));
        commandBuilder.set("#PreviewPlaceholder.Visible", false);
    }

    private void bindFundsLine(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AetherhavenPlugin plugin
    ) {
        World world = store.getExternalData().getWorld();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (world == null || pr == null) {
            commandBuilder.set("#FundsLine.TextSpans", Message.raw(""));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, pr.getUuid());
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, pr.getUuid());
        CombinedItemContainer inv =
            InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        long invCoins = GoldCoinPayment.totalAvailable(null, inv, false);
        long treasuryCoins =
            allowTreasury && payerTown != null ? payerTown.getTreasuryGoldCoinCount() : 0L;
        commandBuilder.set(
            "#FundsLine.TextSpans",
            Message.translation(MSG + ".funds")
                .param("inv", String.valueOf(invCoins))
                .param("treasury", String.valueOf(treasuryCoins))
        );
    }

    private static boolean playerCanAfford(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AetherhavenPlugin plugin,
        long price
    ) {
        if (price <= 0L) {
            return true;
        }
        World world = store.getExternalData().getWorld();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (world == null || pr == null) {
            return false;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, pr.getUuid());
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, pr.getUuid());
        CombinedItemContainer inv =
            InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        return GoldCoinPayment.canAfford(payerTown, inv, price, allowTreasury);
    }

    private boolean matchesSearch(@Nonnull PropDefinition def) {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        return def.getDisplayName().toLowerCase(Locale.ROOT).contains(q)
            || def.getId().toLowerCase(Locale.ROOT).contains(q);
    }

    private static long epochDay(@Nonnull Store<EntityStore> store) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return 0L;
        }
        return wtr.getGameDateTime().toLocalDate().toEpochDay();
    }

    private void schedulePrefabPreviewWithRetries(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null || selectedSlot < 0) {
            PlotCraftingPrefabPreview.clear(playerRef);
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            PlotCraftingPrefabPreview.clear(playerRef);
            return;
        }
        List<FurnitureMerchantShopSlotRecord> slots = town.getFurnitureMerchantShopSlots();
        if (selectedSlot >= slots.size()) {
            PlotCraftingPrefabPreview.clear(playerRef);
            return;
        }
        PropDefinition def = plugin.getPropCatalog().get(slots.get(selectedSlot).getPropId());
        if (def == null) {
            PlotCraftingPrefabPreview.clear(playerRef);
            return;
        }
        String keyForSend = def.getPrefabPath();
        Runnable attempt =
            () -> {
                if (isDismissed() || !ref.isValid()) {
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null || player.getPageManager().getCustomPage() != this) {
                    return;
                }
                if (PlotCraftingPrefabPreviewClientMode.ensureClientCreativeForPreview(
                    playerRef, player.getGameMode(), clientCreativeSpoofed
                )) {
                    clientCreativeSpoofed = true;
                }
                PlotCraftingPrefabPreview.send(playerRef, keyForSend);
            };
        for (long delayMs : new long[] {50L, 100L, 150L}) {
            plugin.scheduleOnWorld(world, attempt, delayMs);
        }
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (data.searchQuery != null) {
            searchQuery = data.searchQuery;
            refresh(ref, store);
            return;
        }
        String action = data.action != null ? data.action.trim() : "";
        if ("ViewModeList".equalsIgnoreCase(action)) {
            viewMode = ViewMode.LIST;
            refresh(ref, store);
            return;
        }
        if ("ViewModeGrid".equalsIgnoreCase(action)) {
            viewMode = ViewMode.GRID;
            refresh(ref, store);
            return;
        }
        if ("Select".equalsIgnoreCase(action) && data.slot != null) {
            try {
                selectedSlot = Integer.parseInt(data.slot.trim());
            } catch (NumberFormatException ignored) {
                return;
            }
            refresh(ref, store);
            return;
        }
        if ("Buy".equalsIgnoreCase(action)) {
            executeBuy(ref, store);
        }
    }

    private void executeBuy(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || world == null || player == null || pr == null || selectedSlot < 0) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return;
        }
        FurnitureMerchantShopService.BuyResult result =
            FurnitureMerchantShopService.tryBuy(plugin, town, tm, player, pr, ref, store, selectedSlot);
        if (!result.ok()) {
            if (result.failLangKey() != null) {
                NotificationUtil.sendNotification(
                    pr.getPacketHandler(),
                    Message.translation(result.failLangKey()),
                    NotificationStyle.Warning
                );
            }
            refresh(ref, store);
            return;
        }
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation(MSG + ".bought"),
            NotificationStyle.Success
        );
        refresh(ref, store);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            PlotCraftingPrefabPreviewClientMode.restoreClientGameMode(
                playerRef, player.getGameMode(), clientCreativeSpoofed
            );
        }
        PlotCraftingPrefabPreview.clear(playerRef);
        super.onDismiss(ref, store);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("Slot", Codec.STRING), (d, v) -> d.slot = v, d -> d.slot)
                .add()
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery)
                .add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String slot;
        @Nullable
        private String searchQuery;
    }
}
