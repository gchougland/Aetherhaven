package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
import com.hexvane.aetherhaven.production.ProductionTimeScaling;
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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Withdraw items from per plot workplace production storage (wardrobe block). */
public final class ProductionStoragePage extends InteractiveCustomUIPage<ProductionStoragePage.PageData> {
    private static final long LIVE_REFRESH_INTERVAL_MS = 350L;

    private final UUID townId;
    private final UUID plotId;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private boolean templateAppended;
    /** Started after first successful full build so we do not stack timers on reopen. */
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
        commandBuilder.set("#ErrMsg.Visible", false);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || uc == null || pr == null) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("server.aetherhaven.ui.production.err.plugin"));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("server.aetherhaven.ui.production.err.permission"));
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null
            || plot.getState() != PlotInstanceState.COMPLETE
            || !ProductionCatalog.isProductionWorkplaceConstruction(plot.getConstructionId())
            || !plot.containsWorldBlock(blockX, blockY, blockZ)) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("server.aetherhaven.ui.production.err.plot"));
            return;
        }
        PlotProductionState state = town.getOrCreatePlotProduction(plotId);
        state.migrateIfNeeded();

        ProductionCatalog.Entry entry =
            ProductionEffectiveCatalog.effective(
                plugin.getProductionCatalog(),
                plugin.getWorkplaceUnlockCatalog(),
                plot.getConstructionId(),
                state
            );
        if (entry == null || entry.catalogSize() <= 0) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("server.aetherhaven.ui.production.err.catalog"));
            return;
        }

        AetherhavenPluginConfig cfg = plugin.getConfig().get();

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#OpenUnlocks",
            new EventData().append("Action", "OpenUnlocks"),
            false
        );

        for (int col = 0; col < 3; col++) {
            String p = "#Slot" + col;
            String c = String.valueOf(col);
            String iconGrid =
                "#Slot"
                    + c
                    + " #Slot"
                    + c
                    + "NavRow #Slot"
                    + c
                    + "IconBox #Slot"
                    + c
                    + "IconSlot #Slot"
                    + c
                    + "Icon";
            int cursor = state.getSlotCursor(col);
            String itemId = entry.itemAtCursor(cursor);
            long cap = AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX;
            if (itemId == null || itemId.isBlank()) {
                commandBuilder.set(iconGrid + ".Slots", new ItemGridSlot[0]);
                commandBuilder.set(p + "Name.TextSpans", Message.raw("—"));
                commandBuilder.set(p + "Qty.TextSpans", Message.raw("0/" + cap));
                commandBuilder.set(p + "Time.TextSpans", Message.raw(""));
                commandBuilder.set(p + "Prog.Value", 0f);
            } else {
                long lineCap = entry.maxStorageForItem(itemId);
                ItemStack iconStack = new ItemStack(itemId, 1);
                commandBuilder.set(iconGrid + ".Slots", new ItemGridSlot[] {new ItemGridSlot(iconStack)});
                Item it = iconStack.getItem();
                Message nameMsg =
                    it != null && it.getTranslationKey() != null && !it.getTranslationKey().isBlank()
                        ? Message.translation(it.getTranslationKey())
                        : Message.raw(itemId);
                commandBuilder.set(p + "Name.TextSpans", nameMsg);
                long have = state.getAmount(itemId);
                commandBuilder.set(p + "Qty.TextSpans", Message.raw(have + "/" + lineCap));
                int ticks = ProductionTimeScaling.effectiveTicks(cfg, entry.ticksAtCursor(cursor));
                float progress = ticks > 0 ? Math.min(1f, state.getSlotTickAccum(col) / (float) ticks) : 0f;
                commandBuilder.set(p + "Prog.Value", progress);
                commandBuilder.set(
                    p + "Time.TextSpans",
                    Message.translation("server.aetherhaven.ui.production.genInterval")
                        .param("time", ProductionCatalog.Entry.formatSecondsForTicks(ticks))
                );
            }
            bindColEvents(eventBuilder, col);
        }
        startLiveRefreshIfNeeded(store);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        liveRefreshActive = false;
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
        String p = "#Slot" + col;
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            p + "Prev",
            new EventData().append("Action", "Left").append("Slot", String.valueOf(col)),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            p + "Next",
            new EventData().append("Action", "Right").append("Slot", String.valueOf(col)),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            p + "Take1",
            new EventData().append("Action", "Take").append("Slot", String.valueOf(col)).append("Amount", "1"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            p + "Take10",
            new EventData().append("Action", "Take").append("Slot", String.valueOf(col)).append("Amount", "10"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            p + "Take100",
            new EventData().append("Action", "Take").append("Slot", String.valueOf(col)).append("Amount", "100"),
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
        if (plot == null
            || plot.getState() != PlotInstanceState.COMPLETE
            || !ProductionCatalog.isProductionWorkplaceConstruction(plot.getConstructionId())
            || !plot.containsWorldBlock(blockX, blockY, blockZ)) {
            return;
        }
        PlotProductionState state = town.getOrCreatePlotProduction(plotId);
        state.migrateIfNeeded();
        ProductionCatalog.Entry entry =
            ProductionEffectiveCatalog.effective(
                plugin.getProductionCatalog(),
                plugin.getWorkplaceUnlockCatalog(),
                plot.getConstructionId(),
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

        if (action.equalsIgnoreCase("Left") || action.equalsIgnoreCase("Right")) {
            int slot = parseSlot(data.slot);
            if (slot < 0 || slot > 2) {
                refresh(ref, store);
                return;
            }
            int delta = action.equalsIgnoreCase("Left") ? -1 : 1;
            state.cycleSlotCursor(slot, delta, entry.catalogSize());
            tm.updateTown(town);
            refresh(ref, store);
            return;
        }
        if (!action.equalsIgnoreCase("Take")) {
            return;
        }
        int slot = parseSlot(data.slot);
        if (slot < 0 || slot > 2) {
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
        String itemId = entry.itemAtCursor(state.getSlotCursor(slot));
        if (itemId == null || itemId.isBlank()) {
            refresh(ref, store);
            return;
        }
        long have = state.getAmount(itemId);
        long take = Math.min(have, want);
        if (take <= 0L) {
            refresh(ref, store);
            return;
        }
        state.removeAmountUpTo(itemId, take);
        ItemStack grant = new ItemStack(itemId, (int) Math.min(take, Integer.MAX_VALUE));
        ItemStackTransaction giveTx = player.giveItem(grant, ref, store);
        if (!giveTx.succeeded()) {
            state.addAmount(itemId, take, entry.maxStorageForItem(itemId));
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("server.aetherhaven.ui.production.err.inventoryFull"),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            return;
        }
        long given = grant.getQuantity();
        long refund = take - given;
        if (refund > 0L) {
            state.addAmount(itemId, refund, entry.maxStorageForItem(itemId));
        }
        tm.updateTown(town);
        refresh(ref, store);
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
