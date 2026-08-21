package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
import com.hexvane.aetherhaven.production.ProductionTimeScaling;
import com.hexvane.aetherhaven.production.ProductionWithdrawal;
import com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades;
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
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Withdraw items from per plot workplace production storage (wardrobe block). */
public final class ProductionStoragePage extends AetherhavenInteractiveCustomUIPage<ProductionStoragePage.PageData> {
    private static final long LIVE_REFRESH_INTERVAL_MS = 1000L;
    private static final int MAX_UI_SLOTS = 5;
    private static final String SLOT_FRAGMENT = "[0]";
    private static final int SLOT_WIDTH_PX = 180;
    private static final int SLOT_GAP_PX = 20;
    private static final int CONTENT_PADDING_PX = 32;
    private static final int BOTTOM_BUTTON_ROW_PX = 416;
    /** DecoratedContainer title, patch border, and macro padding beyond {@code #Content}. */
    private static final int CONTAINER_CHROME_PX = 148;

    private final UUID townId;
    private final UUID plotId;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private boolean templateAppended;
    private boolean slotFragmentsAppended;
    private boolean localizationApplied;
    private boolean liveRefreshStarted;
    private volatile boolean liveRefreshActive;

    public ProductionStoragePage(
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
            commandBuilder.append("Aetherhaven/ProductionStorage.ui");
            templateAppended = true;
        }
        if (!slotFragmentsAppended) {
            for (int i = 0; i < MAX_UI_SLOTS; i++) {
                commandBuilder.append(slotHost(i), "Aetherhaven/ProductionStorageSlot.ui");
            }
            slotFragmentsAppended = true;
        }
        if (!localizationApplied) {
            AetherhavenUiLocalization.applyProductionStorage(commandBuilder);
            localizationApplied = true;
        }
        commandBuilder.set("#ErrMsg.Visible", false);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || uc == null || pr == null) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.plugin"));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.permission"));
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        String gameplayConstructionId = plugin.getConstructionCatalog().resolveGameplayConstructionId(plot != null ? plot.getConstructionId() : "");
        if (plot == null
            || plot.getState() != PlotInstanceState.COMPLETE
            || !ProductionCatalog.isProductionWorkplaceConstruction(gameplayConstructionId)
            || !plot.containsWorldBlock(blockX, blockY, blockZ)) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.plot"));
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
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.catalog"));
            return;
        }

        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        int slotCount = WorkplaceProductionUpgrades.slotCount(state);
        double speedMul = WorkplaceProductionUpgrades.speedMultiplier(state);
        applyShellWidth(commandBuilder, slotCount);

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#OpenUnlocks",
            new EventData().append("Action", "OpenUnlocks"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CollectAll",
            new EventData().append("Action", "CollectAll"),
            false
        );

        for (int col = 0; col < MAX_UI_SLOTS; col++) {
            String host = slotHost(col);
            boolean active = col < slotCount;
            if (active) {
                commandBuilder.set(host + ".Visible", true);
                resetHostAnchor(commandBuilder, host, col, slotCount);
            } else {
                commandBuilder.set(host + ".Visible", false);
                collapseHostAnchor(commandBuilder, host);
                continue;
            }
            String base = slotBase(col);
            String iconPath = base + " #Icon";
            int cursor = state.getSlotCursor(col);
            String itemId = entry.itemAtCursor(cursor);
            if (itemId == null || itemId.isBlank()) {
                commandBuilder.set(iconPath + ".Visible", false);
                commandBuilder.set(base + " #Name.TextSpans", Message.raw("—"));
                commandBuilder.set(base + " #Qty.TextSpans", Message.raw("0/0"));
                commandBuilder.set(base + " #Time.TextSpans", Message.raw(""));
                commandBuilder.set(base + " #Prog.Value", 0f);
            } else {
                long lineCap = WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, itemId);
                Item it = Item.getAssetMap().getAsset(itemId);
                commandBuilder.set(iconPath + ".Visible", true);
                commandBuilder.set(iconPath + ".AssetPath", ItemAssetImagePath.forItem(it, itemId));
                Message nameMsg =
                    it != null && it.getTranslationKey() != null && !it.getTranslationKey().isBlank()
                        ? Message.translation(it.getTranslationKey())
                        : Message.raw(itemId);
                commandBuilder.set(base + " #Name.TextSpans", nameMsg);
                long have = state.getAmount(itemId);
                commandBuilder.set(base + " #Qty.TextSpans", Message.raw(have + "/" + lineCap));
                int ticks =
                    ProductionTimeScaling.effectiveTicksForItemAtPlot(
                        cfg,
                        entry.ticksAtCursor(cursor),
                        speedMul,
                        world,
                        plot.getSignX(),
                        plot.getSignZ(),
                        itemId
                    );
                float progress = ticks > 0 ? Math.min(1f, state.getSlotTickAccum(col) / (float) ticks) : 0f;
                commandBuilder.set(base + " #Prog.Value", progress);
                commandBuilder.set(
                    base + " #Time.TextSpans",
                    Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.genInterval")
                        .param("time", ProductionCatalog.Entry.formatSecondsForTicks(ticks))
                );
            }
            bindColEvents(eventBuilder, col);
        }
        startLiveRefreshIfNeeded(store);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
        liveRefreshActive = false;
    }

    private static String slotHost(int col) {
        return "#SlotsRow #Slot" + col + "Host";
    }

    private static String slotBase(int col) {
        return slotHost(col) + SLOT_FRAGMENT;
    }

    private static void applyShellWidth(@Nonnull UICommandBuilder commandBuilder, int slotCount) {
        int slots = Math.max(1, Math.min(MAX_UI_SLOTS, slotCount));
        int slotsWidth = slots * SLOT_WIDTH_PX + Math.max(0, slots - 1) * SLOT_GAP_PX;
        int inner = Math.max(slotsWidth, BOTTOM_BUTTON_ROW_PX);
        int shellWidth = inner + CONTENT_PADDING_PX + CONTAINER_CHROME_PX;
        Anchor shell = new Anchor();
        shell.setWidth(Value.of(shellWidth));
        commandBuilder.setObject("#ProductionShell.Anchor", shell);
        Anchor row = new Anchor();
        row.setWidth(Value.of(slotsWidth));
        commandBuilder.setObject("#SlotsRow.Anchor", row);
    }

    private static void resetHostAnchor(@Nonnull UICommandBuilder commandBuilder, @Nonnull String host, int col, int slotCount) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(SLOT_WIDTH_PX));
        if (col < slotCount - 1) {
            anchor.setRight(Value.of(SLOT_GAP_PX));
        }
        commandBuilder.setObject(host + ".Anchor", anchor);
    }

    private static void collapseHostAnchor(@Nonnull UICommandBuilder commandBuilder, @Nonnull String host) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(0));
        anchor.setHeight(Value.of(0));
        commandBuilder.setObject(host + ".Anchor", anchor);
    }

    private void startLiveRefreshIfNeeded(@Nonnull Store<EntityStore> store) {
        if (liveRefreshStarted) {
            return;
        }
        liveRefreshStarted = true;
        liveRefreshActive = true;
        scheduleLiveRefreshTick(store.getExternalData().getWorld());
    }

    private void scheduleLiveRefreshTick(@Nonnull World world) {
        if (!liveRefreshActive) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            liveRefreshActive = false;
            return;
        }
        plugin.scheduleOnWorld(
            world,
            () -> {
                if (!liveRefreshActive) {
                    return;
                }
                Ref<EntityStore> r = playerRef.getReference();
                if (r == null || !r.isValid()) {
                    liveRefreshActive = false;
                    return;
                }
                Store<EntityStore> st = r.getStore();
                Player pl = st.getComponent(r, Player.getComponentType());
                if (pl == null || pl.getPageManager().getCustomPage() != this) {
                    liveRefreshActive = false;
                    return;
                }
                refresh(r, st);
                scheduleLiveRefreshTick(world);
            },
            LIVE_REFRESH_INTERVAL_MS
        );
    }

    private static void bindColEvents(@Nonnull UIEventBuilder eventBuilder, int col) {
        String base = slotBase(col);
        String c = String.valueOf(col);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            base + " #Pick",
            new EventData().append("Action", "PickMaterial").append("Slot", c),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            base + " #Take1",
            new EventData().append("Action", "Take").append("Slot", c).append("Amount", "1"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            base + " #Take10",
            new EventData().append("Action", "Take").append("Slot", c).append("Amount", "10"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            base + " #Take100",
            new EventData().append("Action", "Take").append("Slot", c).append("Amount", "100"),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        String action = data.action;
        if (action == null || action.isBlank()) {
            return;
        }
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
        if (entry == null || entry.catalogSize() <= 0) {
            return;
        }

        if (action.equalsIgnoreCase("OpenUnlocks")) {
            liveRefreshActive = false;
            player.getPageManager().openCustomPage(ref, store, new ProductionStorageUnlocksPage(playerRef, townId, plotId, blockX, blockY, blockZ));
            return;
        }

        if (action.equalsIgnoreCase("PickMaterial")) {
            int slot = parseSlot(data.slot);
            if (slot < 0 || slot >= WorkplaceProductionUpgrades.slotCount(state)) {
                return;
            }
            liveRefreshActive = false;
            player.getPageManager().openCustomPage(
                ref,
                store,
                new ProductionMaterialPickerPage(playerRef, townId, plotId, slot, blockX, blockY, blockZ)
            );
            return;
        }

        if (action.equalsIgnoreCase("CollectAll")) {
            collectAll(ref, store, player, pr, tm, town, state, entry);
            return;
        }

        if (!action.equalsIgnoreCase("Take")) {
            return;
        }
        int slot = parseSlot(data.slot);
        if (slot < 0 || slot >= WorkplaceProductionUpgrades.slotCount(state)) {
            return;
        }
        int want;
        try {
            want = Integer.parseInt(data.amount != null ? data.amount.trim() : "0");
        } catch (NumberFormatException e) {
            return;
        }
        if (want <= 0) {
            return;
        }
        withdrawFromSlot(ref, store, player, pr, tm, town, state, entry, slot, want);
    }

    private void collectAll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef pr,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotProductionState state,
        @Nonnull ProductionCatalog.Entry entry
    ) {
        int slotCount = WorkplaceProductionUpgrades.slotCount(state);
        Map<String, Long> toTake = new LinkedHashMap<>();
        for (int slot = 0; slot < slotCount; slot++) {
            String itemId = entry.itemAtCursor(state.getSlotCursor(slot));
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            long have = state.getAmount(itemId);
            if (have <= 0L) {
                continue;
            }
            toTake.merge(itemId, have, Long::sum);
        }
        if (toTake.isEmpty()) {
            refresh(ref, store);
            return;
        }
        ProductionWithdrawal.ResultSink result = new ProductionWithdrawal.ResultSink();
        for (var row : toTake.entrySet()) {
            ProductionWithdrawal.withdrawToPlayer(ref, store, player, state, entry, row.getKey(), row.getValue(), result);
        }
        tm.updateTown(town);
        notifyWithdrawResult(pr, result);
        refresh(ref, store);
    }

    private void withdrawFromSlot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef pr,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotProductionState state,
        @Nonnull ProductionCatalog.Entry entry,
        int slot,
        int want
    ) {
        String itemId = entry.itemAtCursor(state.getSlotCursor(slot));
        if (itemId == null || itemId.isBlank()) {
            refresh(ref, store);
            return;
        }
        ProductionWithdrawal.ResultSink result = new ProductionWithdrawal.ResultSink();
        ProductionWithdrawal.withdrawToPlayer(ref, store, player, state, entry, itemId, want, result);
        tm.updateTown(town);
        notifyWithdrawResult(pr, result);
        refresh(ref, store);
    }

    private static void notifyWithdrawResult(@Nonnull PlayerRef pr, @Nonnull ProductionWithdrawal.ResultSink result) {
        if (result.needEmptyBuckets) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.needEmptyBucket")
                    .param("need", String.valueOf(result.emptyBucketsRequired))
                    .param("have", String.valueOf(result.emptyBucketsHeld)),
                NotificationStyle.Warning
            );
            return;
        }
        if (result.inventoryFull) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.inventoryFull"),
                NotificationStyle.Warning
            );
            return;
        }
        if (result.inventoryPartial) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.inventoryPartial"),
                NotificationStyle.Warning
            );
        }
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
                .append(new KeyedCodec<>("Slot", Codec.STRING), (d, v) -> d.slot = v, d -> d.slot)
                .add()
                .append(new KeyedCodec<>("Amount", Codec.STRING), (d, v) -> d.amount = v, d -> d.amount)
                .add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String slot;
        @Nullable
        private String amount;
    }
}
