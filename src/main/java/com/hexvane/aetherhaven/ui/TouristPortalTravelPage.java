package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.map.TownBorderMapOverlayService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.tourist.TownPortalTravelColor;
import com.hexvane.aetherhaven.tourist.TouristPortalRecord;
import com.hexvane.aetherhaven.tourist.TouristPortalTravelService;
import com.hexvane.aetherhaven.tourist.TouristPortalTravelService.Destination;
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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TouristPortalTravelPage extends AetherhavenInteractiveCustomUIPage<TouristPortalTravelPage.PageData> {
    private static final String MSG = "aetherhaven_tourist.aetherhaven.tourist.portalTravel";
    private static final String MAIN = "#Content";
    private static final String ROWS = MAIN + " #DestinationScroll #DestinationRows";
    private static final String COLOR_MODAL = "#PortalColorPickerModal";
    private static final String COLOR_GRID = COLOR_MODAL + " #Content #PortalColorPresetGrid";
    private static final String COLOR_CHOOSE_BTN = MAIN + " #ChoosePortalColorButton";
    private static final String COLOR_CANCEL_BTN = COLOR_MODAL + " #Content #PortalColorPickerCancelButton";
    private static final String PRIVACY_ROW = MAIN + " #PrivacyRow";
    private static final int MAX_ROWS = 64;

    private final UUID sourcePortalId;
    private boolean portalColorPickerOpen;

    public TouristPortalTravelPage(@Nonnull PlayerRef playerRef, @Nonnull UUID sourcePortalId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.sourcePortalId = sourcePortalId;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/TouristPortalTravelPage.ui");
        AetherhavenUiLocalization.applyTouristPortalTravelPage(commandBuilder);
        wireStaticEvents(eventBuilder);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            commandBuilder.set(PRIVACY_ROW + ".Visible", false);
            commandBuilder.set(MAIN + " #DestinationsEmpty.Visible", true);
            commandBuilder.set(MAIN + " #DestinationScroll.Visible", false);
            commandBuilder.clear(ROWS);
            return;
        }

        TouristPortalRecord source = TouristPortalTravelService.findPortal(world, plugin, sourcePortalId);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        TownRecord sourceTown = source != null ? tm.getTown(source.getTownId()) : null;
        boolean canManage =
            sourceTown != null && uc != null && TouristPortalTravelService.canPlayerManageTownPortalSettings(sourceTown, uc.getUuid());

        commandBuilder.set(PRIVACY_ROW + ".Visible", canManage);
        if (canManage && sourceTown != null) {
            TownPlayerLookup.refreshOwnerUsernameIfOnline(world, sourceTown, tm);
            commandBuilder.set(MAIN + " #AllowInboundToggle.Value", sourceTown.isAllowVisitorPortalTravel());
            commandBuilder.set(MAIN + " #AllowInboundToggle.Disabled", false);
            String townColor = TownPortalTravelColor.resolveHex(sourceTown);
            TownPortalTravelColor.applyTeleportIconTint(commandBuilder, MAIN + " #TownColorPreviewIcon", townColor);
            commandBuilder.set(COLOR_CHOOSE_BTN + ".Disabled", false);
        }
        TownPortalTravelColorPickerUi.setModalVisible(commandBuilder, COLOR_MODAL, canManage && portalColorPickerOpen);
        if (canManage && portalColorPickerOpen && sourceTown != null) {
            TownPortalTravelColorPickerUi.buildPresetGrid(
                commandBuilder,
                eventBuilder,
                COLOR_GRID,
                TownPortalTravelColor.normalizePresetHex(TownPortalTravelColor.resolveHex(sourceTown))
            );
        }

        List<Destination> destinations =
            TouristPortalTravelService.listNetworkDestinations(world, plugin, sourcePortalId);
        commandBuilder.clear(ROWS);
        int travelable = 0;
        for (Destination d : destinations) {
            if (d.canTravelHere()) {
                travelable++;
            }
        }
        if (destinations.isEmpty()) {
            commandBuilder.set(MAIN + " #DestinationsEmpty.Visible", true);
            commandBuilder.set(MAIN + " #DestinationScroll.Visible", false);
        } else {
            commandBuilder.set(MAIN + " #DestinationsEmpty.Visible", false);
            commandBuilder.set(MAIN + " #DestinationScroll.Visible", true);
            int n = Math.min(destinations.size(), MAX_ROWS);
            for (int i = 0; i < n; i++) {
                Destination d = destinations.get(i);
                String row = ROWS + "[" + i + "]";
                commandBuilder.append(ROWS, "Aetherhaven/TouristPortalTravelRow.ui");
                TownPortalTravelColor.applyTeleportIconTint(commandBuilder, row + " #PortalTeleportIcon", d.townColorHex());
                commandBuilder.set(row + " #TownName.TextSpans", Message.raw(d.townDisplayName()));
                commandBuilder.set(
                    row + " #OwnerLine.TextSpans",
                    Message.translation(MSG + ".ownerLine").param("owner", Message.raw(d.ownerDisplayName()))
                );
                commandBuilder.set(row + " #StatusLine.TextSpans", statusMessage(d));
                commandBuilder.set(row + " #ClosedIcon.Visible", !d.acceptsVisitors() && !d.sourcePortal());
                if (!d.acceptsVisitors() && !d.sourcePortal()) {
                    commandBuilder.set(row + " #ClosedIcon.AssetPath", "UI/Custom/padlock.png");
                }
                boolean showTravel = d.canTravelHere();
                commandBuilder.set(row + " #TravelButton.Visible", showTravel);
                commandBuilder.set(row + " #TravelButton.Disabled", !showTravel);
                if (showTravel) {
                    commandBuilder.set(
                        row + " #TravelButton.Text",
                        Message.translation(MSG + ".travelButton")
                    );
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        row + " #TravelButton",
                        new EventData()
                            .append("Action", "Travel")
                            .append("PortalId", d.portalId().toString()),
                        false
                    );
                }
            }
        }
        commandBuilder.set(
            MAIN + " #TravelHint.TextSpans",
            Message.translation(MSG + ".hint").param("count", String.valueOf(travelable))
        );
    }

    @Nonnull
    private static Message statusMessage(@Nonnull Destination d) {
        if (d.sourcePortal()) {
            return Message.translation("aetherhaven_tourist.aetherhaven.tourist.portalTravel.statusHere");
        }
        if (!d.acceptsVisitors()) {
            return Message.translation("aetherhaven_tourist.aetherhaven.tourist.portalTravel.statusClosed");
        }
        return Message.translation("aetherhaven_tourist.aetherhaven.tourist.portalTravel.statusOpen");
    }

    private void wireStaticEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            MAIN + " #AllowInboundToggle",
            new EventData()
                .append("Action", "SetAllowInbound")
                .append("@AllowInbound", MAIN + " #AllowInboundToggle.Value"),
            false
        );
        TownPortalTravelColorPickerUi.bindOpenButton(eventBuilder, COLOR_CHOOSE_BTN);
        TownPortalTravelColorPickerUi.bindCloseButton(eventBuilder, COLOR_CANCEL_BTN);
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (data.action == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if ("SetAllowInbound".equalsIgnoreCase(data.action)) {
            if (data.allowInbound == null) {
                return;
            }
            handleSetAllowInbound(ref, store, plugin, world, data.allowInbound);
            return;
        }
        if (TownPortalTravelColorPickerUi.ACTION_OPEN.equalsIgnoreCase(data.action)) {
            portalColorPickerOpen = true;
            rebuild();
            return;
        }
        if (TownPortalTravelColorPickerUi.ACTION_CLOSE.equalsIgnoreCase(data.action)) {
            portalColorPickerOpen = false;
            rebuild();
            return;
        }
        if (TownPortalTravelColorPickerUi.ACTION_PICK.equalsIgnoreCase(data.action)) {
            handlePickPortalColorPreset(ref, store, plugin, world, data.presetHex);
            return;
        }
        if ("Travel".equalsIgnoreCase(data.action) && data.portalId != null) {
            handleTravel(ref, store, plugin, world, data.portalId);
        }
    }

    private void handleSetAllowInbound(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        boolean allow
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        TouristPortalRecord source = TouristPortalTravelService.findPortal(world, plugin, sourcePortalId);
        if (uc == null || source == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(source.getTownId());
        if (town == null || !TouristPortalTravelService.canPlayerManageTownPortalSettings(town, uc.getUuid())) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.translation(MSG + ".noPermission"),
                NotificationStyle.Warning
            );
            rebuild();
            return;
        }
        town.setAllowVisitorPortalTravel(allow);
        tm.updateTown(town);
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(),
            allow
                ? Message.translation(MSG + ".inboundEnabled")
                : Message.translation(MSG + ".inboundDisabled"),
            NotificationStyle.Success
        );
        rebuild();
    }

    private void handlePickPortalColorPreset(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nullable String presetHex
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        TouristPortalRecord source = TouristPortalTravelService.findPortal(world, plugin, sourcePortalId);
        if (uc == null || source == null || presetHex == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(source.getTownId());
        if (town == null || !TouristPortalTravelService.canPlayerManageTownPortalSettings(town, uc.getUuid())) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.translation(MSG + ".noPermission"),
                NotificationStyle.Warning
            );
            portalColorPickerOpen = false;
            rebuild();
            return;
        }
        TownPortalTravelColor.applyStoredHex(town, presetHex);
        tm.updateTown(town);
        TownBorderMapOverlayService.refreshPlayer(world, uc.getUuid());
        portalColorPickerOpen = false;
        rebuild();
    }

    private void handleTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull String portalIdRaw
    ) {
        UUID destId;
        try {
            destId = UUID.fromString(portalIdRaw.trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (destId.equals(sourcePortalId)) {
            return;
        }
        TouristPortalRecord dest = TouristPortalTravelService.findPortal(world, plugin, destId);
        if (dest == null) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.translation(MSG + ".destinationUnavailable"),
                NotificationStyle.Warning
            );
            rebuild();
            return;
        }
        TownRecord destTown = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(dest.getTownId());
        if (destTown == null || !destTown.isAllowVisitorPortalTravel()) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.translation(MSG + ".destinationClosed"),
                NotificationStyle.Warning
            );
            rebuild();
            return;
        }
        TouristPortalTravelService.teleportPlayerToPortal(ref, store, world, dest);
        close();
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(),
            Message.translation(MSG + ".traveled").param("town", destTown.getDisplayName()),
            NotificationStyle.Success
        );
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("PortalId", Codec.STRING), (d, v) -> d.portalId = v, d -> d.portalId)
                .add()
                .append(new KeyedCodec<>("@AllowInbound", Codec.BOOLEAN), (d, v) -> d.allowInbound = v, d -> d.allowInbound)
                .add()
                .append(new KeyedCodec<>("PresetHex", Codec.STRING), (d, v) -> d.presetHex = v, d -> d.presetHex)
                .add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String portalId;
        @Nullable
        private Boolean allowInbound;
        @Nullable
        private String presetHex;
    }
}
