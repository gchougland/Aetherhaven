package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownLogEntry;
import com.hexvane.aetherhaven.town.TownLogMessage;
import com.hexvane.aetherhaven.town.TownLogService;
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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Town records shelf log: scrollable saved history of town events. */
public final class TownLogPage extends AetherhavenInteractiveCustomUIPage<TownLogPage.PageData> {
    private static final String LOG_ROWS = "#LogListPanel #LogScroll #LogRows";

    private final UUID townId;
    @Nullable
    private final Ref<ChunkStore> managementBlockRef;
    @Nullable
    private final Vector3i managementBlockPos;
    private boolean templateAppended;
    private boolean clearLogConfirmOpen;

    public TownLogPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nullable Ref<ChunkStore> managementBlockRef,
        @Nullable Vector3i managementBlockPos
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.managementBlockRef = managementBlockRef;
        this.managementBlockPos = managementBlockPos != null ? new Vector3i(managementBlockPos) : null;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/TownLogPage.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyTownLogPage(commandBuilder);
        commandBuilder.set("#ClearLogModal.Visible", clearLogConfirmOpen);

        boolean showMgmtTabs = managementBlockRef != null && managementBlockPos != null;
        commandBuilder.set("#ManagementTabStrip.Visible", showMgmtTabs);
        if (showMgmtTabs) {
            boolean needsOk = computeNeedsMoveTabsOk(store);
            commandBuilder.set("#TabPlotButton.Disabled", false);
            commandBuilder.set("#TabPlayersButton.Disabled", false);
            commandBuilder.set("#TabNeedsButton.Disabled", !needsOk);
            commandBuilder.set("#TabLogButton.Disabled", true);
            commandBuilder.set("#TabMoveButton.Disabled", !needsOk);
            bindManagementReturnNav(eventBuilder, needsOk);
        }

        bindClearLogEvents(eventBuilder);

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            commandBuilder.set("#ClearLogButton.Visible", false);
            commandBuilder.set("#EmptyHint.Visible", true);
            commandBuilder.set("#LogScroll.Visible", false);
            commandBuilder.set(
                "#EmptyHint.TextSpans",
                Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded")
            );
            return;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town == null) {
            commandBuilder.set("#ClearLogButton.Visible", false);
            commandBuilder.set("#EmptyHint.Visible", true);
            commandBuilder.set("#LogScroll.Visible", false);
            commandBuilder.set(
                "#EmptyHint.TextSpans",
                Message.translation("aetherhaven_common.aetherhaven.common.townNotFound")
            );
            return;
        }

        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        boolean isOwner = pu != null && pu.getUuid().equals(town.getOwnerUuid());
        commandBuilder.set("#ClearLogButton.Visible", isOwner);

        List<TownLogEntry> entries = new ArrayList<>(town.getTownLog());
        entries.sort(Comparator.comparingLong(TownLogEntry::getGameEpochDay).reversed());

        if (entries.isEmpty()) {
            commandBuilder.set("#EmptyHint.Visible", true);
            commandBuilder.set("#LogScroll.Visible", false);
            commandBuilder.set(
                "#EmptyHint.TextSpans",
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.townlog.empty")
            );
            commandBuilder.set("#ClearLogButton.Disabled", true);
            commandBuilder.clear(LOG_ROWS);
            return;
        }

        commandBuilder.set("#EmptyHint.Visible", false);
        commandBuilder.set("#LogScroll.Visible", true);
        commandBuilder.set("#ClearLogButton.Disabled", !isOwner);
        commandBuilder.clear(LOG_ROWS);
        for (int i = 0; i < entries.size(); i++) {
            TownLogEntry ent = entries.get(i);
            commandBuilder.append(LOG_ROWS, "Aetherhaven/TownLogRow.ui");
            String row = LOG_ROWS + "[" + i + "]";
            commandBuilder.set(
                row + " #DayLabel.TextSpans",
                Message.raw(AetherhavenCalendar.formatDateFromEpochDay(ent.getGameEpochDay()))
            );
            commandBuilder.set(row + " #MessageLabel.TextSpans", TownLogMessage.render(ent));
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action != null) {
            if (data.action.equalsIgnoreCase("SwitchTabPlot")) {
                openPlotManagement(ref, store, 0, false);
                return;
            }
            if (data.action.equalsIgnoreCase("SwitchTabPlayers")) {
                openPlotManagement(ref, store, 1, false);
                return;
            }
            if (data.action.equalsIgnoreCase("OpenTownNeeds")) {
                openTownNeeds(ref, store);
                return;
            }
            if (data.action.equalsIgnoreCase("BeginMoveBuilding")) {
                openPlotManagement(ref, store, 0, true);
                return;
            }
            if (data.action.equalsIgnoreCase("BeginClearLog")) {
                clearLogConfirmOpen = true;
                rebuild(ref, store);
                return;
            }
            if (data.action.equalsIgnoreCase("CancelClearLog")) {
                clearLogConfirmOpen = false;
                rebuild(ref, store);
                return;
            }
            if (data.action.equalsIgnoreCase("ConfirmClearLog")) {
                confirmClearLog(ref, store);
                return;
            }
        }
    }

    private void bindClearLogEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ClearLogButton",
            new EventData().append("Action", "BeginClearLog"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ClearLogConfirmButton",
            new EventData().append("Action", "ConfirmClearLog"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ClearLogCancelButton",
            new EventData().append("Action", "CancelClearLog"),
            false
        );
    }

    private void bindManagementReturnNav(@Nonnull UIEventBuilder eventBuilder, boolean needsMoveTabsEnabled) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabPlotButton",
            new EventData().append("Action", "SwitchTabPlot"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabPlayersButton",
            new EventData().append("Action", "SwitchTabPlayers"),
            false
        );
        if (needsMoveTabsEnabled) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabNeedsButton",
                new EventData().append("Action", "OpenTownNeeds"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabMoveButton",
                new EventData().append("Action", "BeginMoveBuilding"),
                false
            );
        }
    }

    private void confirmClearLog(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (town == null || pu == null || !pu.getUuid().equals(town.getOwnerUuid())) {
            clearLogConfirmOpen = false;
            rebuild(ref, store);
            return;
        }
        TownLogService.clear(town, tm);
        clearLogConfirmOpen = false;
        playerRef.sendMessage(Message.translation("aetherhaven_ui_town.aetherhaven.ui.townlog.cleared"));
        rebuild(ref, store);
    }

    private void openTownNeeds(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (managementBlockRef == null || managementBlockPos == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager()
            .openCustomPage(ref, store, new VillagerNeedsOverviewPage(playerRef, townId, managementBlockRef, managementBlockPos));
    }

    private void openPlotManagement(
        @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, int managementTab, boolean openMoveBuildingModalOnFirstBuild
    ) {
        if (managementBlockRef == null || managementBlockPos == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager()
            .openCustomPage(
                ref,
                store,
                new PlotConstructionPage(
                    playerRef,
                    managementBlockRef,
                    managementBlockPos,
                    true,
                    managementTab,
                    openMoveBuildingModalOnFirstBuild
                )
            );
    }

    private void rebuild(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    private boolean computeNeedsMoveTabsOk(@Nonnull Store<EntityStore> store) {
        if (managementBlockRef == null) {
            return false;
        }
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
        Store<ChunkStore> cs = managementBlockRef.getStore();
        ManagementBlock mb = cs.getComponent(managementBlockRef, ManagementBlock.getComponentType());
        if (mb == null || mb.getTownId().isBlank() || mb.getPlotId().isBlank()) {
            return false;
        }
        try {
            TownRecord town = tm.getTown(UUID.fromString(mb.getTownId().trim()));
            if (town == null) {
                return false;
            }
            PlotInstance pi = town.findPlotById(UUID.fromString(mb.getPlotId().trim()));
            return pi != null && pi.getState() == PlotInstanceState.COMPLETE;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        private String action;
    }
}
