package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
import com.hexvane.aetherhaven.production.WorkplaceUnlockCatalog;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
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
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Purchase workplace output unlocks for the current production plot (opened from {@link ProductionStoragePage}). */
public final class ProductionStorageUnlocksPage extends InteractiveCustomUIPage<ProductionStorageUnlocksPage.PageData> {
    private static final String ROWS = "#Content #GridScroll #UnlockRows";
    private static final String ERR_MSG = "#Content #ErrMsg";
    private static final String DETAIL_PANEL = "#Content #DetailPanel";
    private static final String DETAIL_NAME = "#Content #DetailPanel #DetailName";
    private static final String DETAIL_BODY = "#Content #DetailPanel #DetailBody";
    private static final String NAV_TO_PRODUCTION = "#Content #NavRow #NavToProduction";
    /** Same as {@code AssetPackSaveBrowser}: mod UI files may not resolve {@code $C.@DefaultTextTooltipStyle}; bind from Java. */
    private static final Value<String> DEFAULT_TEXT_TOOLTIP_STYLE = Value.ref("Common.ui", "DefaultTextTooltipStyle");
    private static final int GRID_COLS = 8;
    private static final int MAX_SLOTS = 160;
    /** Tooltip line when player meets requirement */
    private static final String TOOLTIP_OK_COLOR = "#3d913f";
    /** Tooltip line when player is short */
    private static final String TOOLTIP_BAD_COLOR = "#d14d4d";

    private final UUID townId;
    private final UUID plotId;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private boolean templateAppended;
    /** Last icon clicked; drives the bottom detail panel. */
    @Nullable
    private String detailFocusItemId;

    public ProductionStorageUnlocksPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nonnull UUID plotId,
        int blockX,
        int blockY,
        int blockZ
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.plotId = plotId;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/ProductionStorageUnlocks.ui");
            templateAppended = true;
        }
        commandBuilder.set(ERR_MSG + ".Visible", false);
        commandBuilder.set(DETAIL_PANEL + ".Visible", false);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            NAV_TO_PRODUCTION,
            new EventData().append("Action", "BackToProduction"),
            false
        );

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || uc == null || pr == null) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("server.aetherhaven.ui.production.err.plugin"));
            commandBuilder.clear(ROWS);
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("server.aetherhaven.ui.production.err.permission"));
            commandBuilder.clear(ROWS);
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null
            || plot.getState() != PlotInstanceState.COMPLETE
            || !ProductionCatalog.isProductionWorkplaceConstruction(plot.getConstructionId())
            || !plot.containsWorldBlock(blockX, blockY, blockZ)) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("server.aetherhaven.ui.production.err.plot"));
            commandBuilder.clear(ROWS);
            return;
        }
        String constructionId = plot.getConstructionId();
        ProductionCatalog.Entry base = plugin.getProductionCatalog().get(constructionId);
        if (base == null || base.catalogSize() <= 0) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("server.aetherhaven.ui.production.err.catalog"));
            commandBuilder.clear(ROWS);
            return;
        }

        PlotProductionState state = town.getOrCreatePlotProduction(plotId);
        state.migrateIfNeeded();
        ProductionCatalog.Entry effective =
            ProductionEffectiveCatalog.effective(
                plugin.getProductionCatalog(),
                plugin.getWorkplaceUnlockCatalog(),
                constructionId,
                state
            );
        if (effective == null || effective.catalogSize() <= 0) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("server.aetherhaven.ui.production.err.catalog"));
            commandBuilder.clear(ROWS);
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
        if (inv == null) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("server.aetherhaven.ui.productionUnlocks.err.inventory"));
            commandBuilder.clear(ROWS);
            return;
        }

        WorkplaceUnlockCatalog ucat = plugin.getWorkplaceUnlockCatalog();
        List<WorkplaceUnlockCatalog.UnlockLine> lines = ucat.linesForWorkplace(constructionId);
        commandBuilder.clear(ROWS);
        if (lines.isEmpty()) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("server.aetherhaven.ui.productionUnlocks.err.empty"));
            return;
        }

        int total = Math.min(lines.size(), MAX_SLOTS);
        int numRows = (total + GRID_COLS - 1) / GRID_COLS;
        for (int r = 0; r < numRows; r++) {
            commandBuilder.append(ROWS, "Aetherhaven/ProductionUnlockGridRow.ui");
            String rowBase = ROWS + "[" + r + "]";
            for (int c = 0; c < GRID_COLS; c++) {
                int idx = r * GRID_COLS + c;
                if (idx >= total) {
                    break;
                }
                WorkplaceUnlockCatalog.UnlockLine line = lines.get(idx);
                String itemId = line.itemId();
                commandBuilder.append(rowBase + " #Strip", "Aetherhaven/ProductionUnlockCell.ui");
                String cell = rowBase + " #Strip[" + c + "]";
                String slotPath = cell + " #UnlockHit #IconFrame #IconInner #UnlockIcon";
                Item assetItem = Item.getAssetMap().getAsset(itemId);
                commandBuilder.set(slotPath + ".AssetPath", ItemAssetImagePath.forItem(assetItem, itemId));

                boolean unlocked = line.defaultUnlocked() || state.isWorkplaceOutputUnlocked(itemId);
                commandBuilder.set(cell + " #UnlockHit.TextTooltipStyle", DEFAULT_TEXT_TOOLTIP_STYLE);
                commandBuilder.set(
                    cell + " #UnlockHit.TooltipTextSpans",
                    cellTooltipMessage(itemLineDisplayName(line, assetItem), unlocked, line, town, inv, town.playerCanSpendTreasuryGold(uc.getUuid()))
                );

                commandBuilder.set(cell + " #UnlockHit #IconFrame #LockOverlay.Visible", !unlocked);
                commandBuilder.set(cell + " #UnlockHit.Disabled", false);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    cell + " #UnlockHit",
                    new EventData().append("Action", "Pick").append("ItemId", itemId),
                    false
                );
            }
        }

        applyDetailPanel(commandBuilder, constructionId, ucat, state, town, inv, town.playerCanSpendTreasuryGold(uc.getUuid()));
    }

    private void applyDetailPanel(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String constructionId,
        @Nonnull WorkplaceUnlockCatalog ucat,
        @Nonnull PlotProductionState state,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold
    ) {
        commandBuilder.set(DETAIL_PANEL + ".Visible", true);
        if (detailFocusItemId == null || detailFocusItemId.isBlank()) {
            commandBuilder.set(DETAIL_NAME + ".TextSpans", Message.raw(""));
            commandBuilder.set(DETAIL_BODY + ".TextSpans", Message.translation("server.aetherhaven.ui.productionUnlocks.detailHint"));
            return;
        }
        WorkplaceUnlockCatalog.UnlockLine line = ucat.findLine(constructionId, detailFocusItemId);
        if (line == null) {
            commandBuilder.set(DETAIL_NAME + ".TextSpans", Message.raw(""));
            commandBuilder.set(DETAIL_BODY + ".TextSpans", Message.translation("server.aetherhaven.ui.productionUnlocks.detailHint"));
            return;
        }
        commandBuilder.set(DETAIL_NAME + ".TextSpans", itemLineDisplayName(line));
        boolean unlocked = line.defaultUnlocked() || state.isWorkplaceOutputUnlocked(line.itemId());
        commandBuilder.set(DETAIL_BODY + ".TextSpans", unlockRequirementBody(unlocked, line, town, inv, allowTreasuryGold));
    }

    @Nonnull
    private static Message itemLineDisplayName(@Nonnull WorkplaceUnlockCatalog.UnlockLine line) {
        return itemLineDisplayName(line, Item.getAssetMap().getAsset(line.itemId()));
    }

    @Nonnull
    private static Message itemLineDisplayName(@Nonnull WorkplaceUnlockCatalog.UnlockLine line, @Nullable Item it) {
        if (it != null && it.getTranslationKey() != null && !it.getTranslationKey().isBlank()) {
            return Message.translation(it.getTranslationKey());
        }
        return Message.raw(line.itemId());
    }

    @Nonnull
    private static Message cellTooltipMessage(
        @Nonnull Message itemName,
        boolean unlocked,
        @Nonnull WorkplaceUnlockCatalog.UnlockLine line,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold
    ) {
        return Message.join(itemName, Message.raw("\n\n"), unlockRequirementBody(unlocked, line, town, inv, allowTreasuryGold));
    }

    /**
     * Item line: held / needed for the unlock item (bags); gold line: total available / needed (treasury + bags).
     * Each line is green if sufficient, red if not.
     */
    @Nonnull
    private static Message unlockRequirementBody(
        boolean unlocked,
        @Nonnull WorkplaceUnlockCatalog.UnlockLine line,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold
    ) {
        if (unlocked) {
            return Message.translation("server.aetherhaven.ui.productionUnlocks.tooltip.unlockedSub");
        }
        int need = line.resourceCost();
        int held = InventoryMaterials.count(inv, line.itemId());
        boolean itemOk = held >= need;
        Message body =
            Message.translation("server.aetherhaven.ui.productionUnlocks.tooltip.itemHeldNeed")
                .param("held", String.valueOf(held))
                .param("need", String.valueOf(need))
                .color(itemOk ? TOOLTIP_OK_COLOR : TOOLTIP_BAD_COLOR);
        long goldNeed = WorkplaceUnlockCatalog.goldCoinsForRarityTier(line.rarityTier());
        if (goldNeed > 0L) {
            long goldHeld = GoldCoinPayment.totalAvailable(town, inv, allowTreasuryGold);
            boolean goldOk = goldHeld >= goldNeed;
            Message goldLine =
                Message.translation("server.aetherhaven.ui.productionUnlocks.tooltip.goldHeldNeed")
                    .param("held", String.valueOf(goldHeld))
                    .param("need", String.valueOf(goldNeed))
                    .color(goldOk ? TOOLTIP_OK_COLOR : TOOLTIP_BAD_COLOR);
            body = Message.join(body, Message.raw("\n"), goldLine);
        }
        return body;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        if (data.action.equalsIgnoreCase("BackToProduction")) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store, new ProductionStoragePage(playerRef, townId, plotId, blockX, blockY, blockZ));
            return;
        }
        if (!data.action.equalsIgnoreCase("Pick")) {
            return;
        }
        if (data.itemId == null || data.itemId.isBlank()) {
            return;
        }
        String itemId = data.itemId.trim();
        detailFocusItemId = itemId;
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || player == null || pr == null || uc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null
            || plot.getState() != PlotInstanceState.COMPLETE
            || !ProductionCatalog.isProductionWorkplaceConstruction(plot.getConstructionId())
            || !plot.containsWorldBlock(blockX, blockY, blockZ)) {
            return;
        }
        String constructionId = plot.getConstructionId();
        WorkplaceUnlockCatalog.UnlockLine line = plugin.getWorkplaceUnlockCatalog().findLine(constructionId, itemId);
        if (line == null) {
            return;
        }
        PlotProductionState state = town.getOrCreatePlotProduction(plotId);
        state.migrateIfNeeded();
        if (line.defaultUnlocked() || state.isWorkplaceOutputUnlocked(itemId)) {
            refresh(ref, store);
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        int needRes = line.resourceCost();
        long goldCost = WorkplaceUnlockCatalog.goldCoinsForRarityTier(line.rarityTier());
        if (InventoryMaterials.count(inv, itemId) < needRes) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("server.aetherhaven.ui.productionUnlocks.notify.needResource").param("need", needRes),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            return;
        }
        boolean allowTreasuryGold = town.playerCanSpendTreasuryGold(uc.getUuid());
        if (goldCost > 0L && !GoldCoinPayment.canAfford(town, inv, goldCost, allowTreasuryGold)) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("server.aetherhaven.ui.productionUnlocks.notify.needGold").param("need", goldCost),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            return;
        }

        ItemStackTransaction takeRes = inv.removeItemStack(new ItemStack(itemId, needRes));
        if (!takeRes.succeeded()) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("server.aetherhaven.ui.productionUnlocks.notify.takeResourceFailed"),
                NotificationStyle.Danger
            );
            refresh(ref, store);
            return;
        }
        if (goldCost > 0L && !GoldCoinPayment.trySpend(town, inv, goldCost, allowTreasuryGold)) {
            player.giveItem(new ItemStack(itemId, needRes), ref, store);
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("server.aetherhaven.ui.productionUnlocks.notify.payGoldFailed"),
                NotificationStyle.Danger
            );
            refresh(ref, store);
            return;
        }

        state.addWorkplaceOutputUnlock(itemId);
        tm.updateTown(town);
        Item unlockedItem = Item.getAssetMap().getAsset(itemId);
        Message unlockedLabel =
            unlockedItem != null && unlockedItem.getTranslationKey() != null && !unlockedItem.getTranslationKey().isBlank()
                ? Message.translation(unlockedItem.getTranslationKey())
                : Message.raw(itemId);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("server.aetherhaven.ui.productionUnlocks.notify.unlocked").param("item", unlockedLabel),
            NotificationStyle.Success
        );
        refresh(ref, store);
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("ItemId", Codec.STRING), (d, v) -> d.itemId = v, d -> d.itemId)
                .add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String itemId;
    }
}
