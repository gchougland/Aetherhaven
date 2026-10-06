package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.PhysicalProductionOutputPolicy;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Pick an unlocked output for one production slot (opened from {@link ProductionStoragePage}). */
public final class ProductionMaterialPickerPage extends AetherhavenInteractiveCustomUIPage<ProductionMaterialPickerPage.PageData> {
    private static final String ROWS = "#Content #GridScroll #PickerRows";
    private static final String ERR_MSG = "#Content #ErrMsg";
    private static final String NAV_TO_PRODUCTION = "#Content #NavRow #NavToProduction";
    private static final Value<String> DEFAULT_TEXT_TOOLTIP_STYLE = Value.ref("Common.ui", "DefaultTextTooltipStyle");
    private static final int GRID_COLS = 8;

    private final UUID townId;
    private final UUID plotId;
    private final int targetSlot;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private boolean templateAppended;

    public ProductionMaterialPickerPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nonnull UUID plotId,
        int targetSlot,
        int blockX,
        int blockY,
        int blockZ
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.plotId = plotId;
        this.targetSlot = targetSlot;
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
            commandBuilder.append("Aetherhaven/ProductionMaterialPicker.ui");
            templateAppended = true;
            AetherhavenUiLocalization.applyProductionMaterialPicker(commandBuilder);
        }
        commandBuilder.set(ERR_MSG + ".Visible", false);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            NAV_TO_PRODUCTION,
            new EventData().append("Action", "BackToProduction"),
            false
        );

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || uc == null) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.plugin"));
            commandBuilder.clear(ROWS);
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.permission"));
            commandBuilder.clear(ROWS);
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        String gameplayConstructionId = plugin.getConstructionCatalog().resolveGameplayConstructionId(plot != null ? plot.getConstructionId() : "");
        if (plot == null
            || plot.getState() != PlotInstanceState.COMPLETE
            || !ProductionCatalog.isProductionWorkplaceConstruction(gameplayConstructionId)
            || !plot.containsWorldBlock(blockX, blockY, blockZ)) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.plot"));
            commandBuilder.clear(ROWS);
            return;
        }

        PlotProductionState state = town.getOrCreatePlotProduction(plotId);
        state.migrateIfNeeded();
        ProductionCatalog.Entry entry =
            ProductionEffectiveCatalog.effective(
                plugin.getProductionCatalog(),
                plugin.getWorkplaceUnlockCatalog(),
                gameplayConstructionId,
                state
            );
        if (entry == null || entry.catalogSize() <= 0) {
            commandBuilder.set(ERR_MSG + ".Visible", true);
            commandBuilder.set(ERR_MSG + ".TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.catalog"));
            commandBuilder.clear(ROWS);
            return;
        }

        String selectedItemId = entry.itemAtCursor(state.getSlotCursor(targetSlot));
        commandBuilder.clear(ROWS);
        int total = entry.catalogSize();
        int numRows = (total + GRID_COLS - 1) / GRID_COLS;
        for (int r = 0; r < numRows; r++) {
            commandBuilder.append(ROWS, "Aetherhaven/ProductionUnlockGridRow.ui");
            String rowBase = ROWS + "[" + r + "]";
            for (int c = 0; c < GRID_COLS; c++) {
                int idx = r * GRID_COLS + c;
                if (idx >= total) {
                    break;
                }
                String itemId = entry.itemAtCursor(idx);
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                commandBuilder.append(rowBase + " #Strip", "Aetherhaven/ProductionUnlockCell.ui");
                String cell = rowBase + " #Strip[" + c + "]";
                String slotPath = cell + " #UnlockHit #IconFrame #IconInner #UnlockIcon";
                Item assetItem = Item.getAssetMap().getAsset(itemId);
                commandBuilder.set(slotPath + ".AssetPath", ItemAssetImagePath.forItem(assetItem, itemId));
                String issue = PhysicalProductionOutputPolicy.selectionIssue(state, targetSlot, itemId);
                commandBuilder.set(cell + " #UnlockHit #IconFrame #LockOverlay.Visible", issue != null);
                boolean selected = itemId.equals(selectedItemId);
                commandBuilder.set(
                    cell + " #UnlockHit #IconFrame.Background",
                    selected ? "#4a6a4a" : "#5a5468"
                );
                commandBuilder.set(cell + " #UnlockHit.TextTooltipStyle", DEFAULT_TEXT_TOOLTIP_STYLE);
                Message nameMsg =
                    assetItem != null && assetItem.getTranslationKey() != null && !assetItem.getTranslationKey().isBlank()
                        ? Message.translation(assetItem.getTranslationKey())
                        : Message.raw(itemId);
                commandBuilder.set(cell + " #UnlockHit.TooltipTextSpans", issue == null ? nameMsg : Message.join(nameMsg, Message.raw("\n" + issue)));
                commandBuilder.set(cell + " #UnlockHit.Disabled", issue != null);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    cell + " #UnlockHit",
                    new EventData().append("Action", "Select").append("ItemId", itemId).append("Slot", String.valueOf(targetSlot)),
                    false
                );
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        if (data.action.equalsIgnoreCase("BackToProduction")) {
            openProduction(ref, store);
            return;
        }
        if (!data.action.equalsIgnoreCase("Select")) {
            return;
        }
        if (data.itemId == null || data.itemId.isBlank()) {
            return;
        }
        int slot = parseSlot(data.slot);
        if (slot < 0) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || player == null || uc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        String gameplayConstructionId = plugin.getConstructionCatalog().resolveGameplayConstructionId(plot != null ? plot.getConstructionId() : "");
        if (plot == null
            || plot.getState() != PlotInstanceState.COMPLETE
            || !ProductionCatalog.isProductionWorkplaceConstruction(gameplayConstructionId)
            || !plot.containsWorldBlock(blockX, blockY, blockZ)) {
            return;
        }
        PlotProductionState state = town.getOrCreatePlotProduction(plotId);
        state.migrateIfNeeded();
        ProductionCatalog.Entry entry =
            ProductionEffectiveCatalog.effective(
                plugin.getProductionCatalog(),
                plugin.getWorkplaceUnlockCatalog(),
                gameplayConstructionId,
                state
            );
        if (entry == null) {
            return;
        }
        if (slot != targetSlot || PhysicalProductionOutputPolicy.selectionIssue(state, slot, data.itemId.trim()) != null) {
            openProduction(ref, store);
            return;
        }
        if (state.setSlotCursorToItem(slot, entry, data.itemId.trim())) {
            tm.updateTown(town);
        }
        openProduction(ref, store);
    }

    private void openProduction(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new ProductionStoragePage(playerRef, townId, plotId, blockX, blockY, blockZ));
    }

    private static int parseSlot(@Nullable String slotStr) {
        if (slotStr == null || slotStr.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(slotStr.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("ItemId", Codec.STRING), (d, v) -> d.itemId = v, d -> d.itemId)
                .add()
                .append(new KeyedCodec<>("Slot", Codec.STRING), (d, v) -> d.slot = v, d -> d.slot)
                .add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String itemId;
        @Nullable
        private String slot;
    }
}
