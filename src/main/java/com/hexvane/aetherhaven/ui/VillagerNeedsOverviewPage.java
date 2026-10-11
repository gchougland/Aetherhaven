package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.villager.VillagerBefriendableResolver;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hexvane.aetherhaven.villager.AetherhavenNpcTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagerNeedsOverviewPage extends AetherhavenInteractiveCustomUIPage<VillagerNeedsOverviewPage.PageData> {
    private static final String VILLAGER_ROWS = "#VillagerListScroll #VillagerRows";
    private static final String REPUTATION_HEART_SLOTS = "#ReputationHeartSlots";

    private final UUID townId;
    @Nullable
    private final Ref<ChunkStore> managementBlockRef;
    @Nullable
    private final Vector3i managementBlockPos;
    private int selectedIndex;
    private String residentFilter = "All";
    /** -1 = default (0); set when reopening (e.g. from gift history back). */
    private final int initialVillagerIndex;
    /** Read-only preview (e.g. Town Journal): no rescue teleport or gift history navigation. */
    private final boolean viewOnly;
    /** {@code append(ui)} must run only once per page instance; repeating it on every {@link #sendUpdate} duplicates the whole tree. */
    private boolean templateAppended;
    private boolean reputationHeartSlotsAppended;

    public VillagerNeedsOverviewPage(@Nonnull PlayerRef playerRef, @Nonnull UUID townId) {
        this(playerRef, townId, null, null, -1, false);
    }

    public VillagerNeedsOverviewPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nullable Ref<ChunkStore> managementBlockRef,
        @Nullable Vector3i managementBlockPos
    ) {
        this(playerRef, townId, managementBlockRef, managementBlockPos, -1, false);
    }

    public VillagerNeedsOverviewPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nullable Ref<ChunkStore> managementBlockRef,
        @Nullable Vector3i managementBlockPos,
        int initialVillagerIndex
    ) {
        this(playerRef, townId, managementBlockRef, managementBlockPos, initialVillagerIndex, false);
    }

    public VillagerNeedsOverviewPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nullable Ref<ChunkStore> managementBlockRef,
        @Nullable Vector3i managementBlockPos,
        int initialVillagerIndex,
        boolean viewOnly
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.managementBlockRef = managementBlockRef;
        this.managementBlockPos = managementBlockPos != null ? new Vector3i(managementBlockPos) : null;
        this.initialVillagerIndex = initialVillagerIndex;
        this.viewOnly = viewOnly;
        if (initialVillagerIndex >= 0) {
            this.selectedIndex = initialVillagerIndex;
        }
    }

    /** Opens the needs overview for one villager (e.g. from Town Journal). Shelf tabs are hidden. */
    public static void openForVillager(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull UUID villagerEntityUuid
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town == null) {
            return;
        }
        List<TownVillagerRow> rows = TownVillagerDirectory.listResidents(store, town);
        int idx = TownVillagerDirectory.indexOfEntity(rows, villagerEntityUuid);
        if (idx < 0) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager()
            .openCustomPage(ref, store, new VillagerNeedsOverviewPage(playerRef, townId, null, null, idx, true));
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/VillagerNeedsOverview.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyVillagerNeedsOverview(commandBuilder);
        for (String filter : List.of("All", "Core", "Townsfolk")) {
            commandBuilder.set("#Filter" + filter + ".Disabled", residentFilter.equals(filter));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Filter" + filter,
                EventData.of("Action", "Filter" + filter), false);
        }
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#OpenProfile", EventData.of("Action", "Profile"), false);
        if (templateAppended && !reputationHeartSlotsAppended) {
            for (int h = 0; h < 10; h++) {
                commandBuilder.append(REPUTATION_HEART_SLOTS, "Aetherhaven/HeartSlot.ui");
            }
            reputationHeartSlotsAppended = true;
        }
        if (!viewOnly) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RescueTeleportButton",
                new EventData().append("Action", "RescueTeleport"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#GiftHistoryButton",
                new EventData().append("Action", "GiftHistory"),
                false
            );
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        boolean showMgmtTabs = managementBlockRef != null && managementBlockPos != null;
        commandBuilder.set("#ManagementTabStrip.Visible", showMgmtTabs);
        if (showMgmtTabs) {
            boolean needsOk = computeNeedsMoveTabsOk(store);
            commandBuilder.set("#TabPlotButton.Disabled", false);
            commandBuilder.set("#TabPlayersButton.Disabled", false);
            commandBuilder.set("#TabNeedsButton.Disabled", true);
            commandBuilder.set("#TabLogButton.Disabled", !needsOk);
            commandBuilder.set("#TabPaintButton.Disabled", !needsOk);
            commandBuilder.set("#TabMoveButton.Disabled", !needsOk);
            bindManagementReturnNav(eventBuilder, needsOk);
        }
        if (plugin == null) {
            commandBuilder.set("#RescueTeleportButton.Visible", false);
            commandBuilder.set("#GiftHistoryButton.Visible", false);
            commandBuilder.set("#Hint.Visible", true);
            commandBuilder.set("#Hint.TextSpans", Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town != null && !town.hasMemberOrOwner(playerRef.getUuid())) { close(); return; }
        if (town == null) {
            commandBuilder.set("#RescueTeleportButton.Visible", false);
            commandBuilder.set("#GiftHistoryButton.Visible", false);
            commandBuilder.set("#Hint.Visible", true);
            commandBuilder.set("#Hint.TextSpans", Message.translation("aetherhaven_common.aetherhaven.common.townNotFound"));
            return;
        }

        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        List<TownVillagerRow> rows = TownVillagerDirectory.listResidents(entityStore, town);
        if (rows.isEmpty()) {
            commandBuilder.set("#ResidentDetails.Visible", false);
            commandBuilder.set("#RescueTeleportButton.Visible", false);
            commandBuilder.set("#GiftHistoryButton.Visible", false);
            commandBuilder.set("#Hint.Visible", true);
            commandBuilder.set("#Hint.TextSpans", Message.translation("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.noResidentsTracked"));
            commandBuilder.clear(VILLAGER_ROWS);
            return;
        }
        if (selectedIndex >= rows.size()) {
            selectedIndex = 0;
        }

        commandBuilder.set("#RescueTeleportButton.Visible", !viewOnly);
        commandBuilder.set("#Hint.Visible", false);
        commandBuilder.clear(VILLAGER_ROWS);
        int visibleRow = 0;
        for (int i = 0; i < rows.size(); i++) {
            TownVillagerRow r = rows.get(i);
            boolean townsfolk = com.hexvane.aetherhaven.town.TownResidentEligibility.isTownsfolkPoolKind(r.bindingKind(), r.roleId(), plugin);
            if (!residentFilter.equals("All") && residentFilter.equals("Townsfolk") != townsfolk) continue;
            commandBuilder.append(VILLAGER_ROWS, "Aetherhaven/TownJournalVillagerRow.ui");
            String row = VILLAGER_ROWS + "[" + visibleRow++ + "]";
            commandBuilder.set(row + " #Portrait.AssetPath", r.portraitPath());
            commandBuilder.set(row + " #VillagerName.Text", r.label());
            commandBuilder.set(row + " #ScheduleLocation.Text", townsfolk ? "Townsfolk" : "Villager");
            commandBuilder.set(row + " #LocateGroup.Visible", false);
            commandBuilder.set(row + " #Select.Disabled", selectedIndex == i);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                new EventData().append("Action", "Select").append("Index", Integer.toString(i)),
                false
            );
        }

        commandBuilder.set("#ResidentDetails.Visible", visibleRow > 0);
        TownVillagerRow sel = rows.get(selectedIndex);
        commandBuilder.set("#ResidentName.Text", sel.label());
        commandBuilder.set("#OpenProfile.Disabled", !ResidentProfileNavigation.supports(store, town, sel));
        boolean showNeeds = sel.usesNeeds() || TownVillagerBinding.KIND_TOWNSFOLK.equals(sel.bindingKind());
        var needs = ResidentNeedsUi.read(entityStore, town, sel.entityUuid(), showNeeds);
        commandBuilder.set("#NeedsBarsGroup.Visible", needs.available());
        commandBuilder.set("#NoNeedsHint.Visible", true);
        commandBuilder.set("#NoNeedsHint.Text", needs.status());
        commandBuilder.set("#HungerBar.Value", needs.hunger());
        commandBuilder.set("#EnergyBar.Value", needs.energy());
        commandBuilder.set("#FunBar.Value", needs.fun());
        commandBuilder.set("#SelectedResidentPortrait.AssetPath", sel.portraitPath());

        Ref<EntityStore> selRef = entityStore.getExternalData().getRefFromUUID(sel.entityUuid());
        boolean befriendable = !com.hexvane.aetherhaven.town.TownResidentEligibility.isTownsfolkPoolKind(sel.bindingKind(), sel.roleId(), plugin)
            && VillagerBefriendableResolver.isBefriendableByEntity(entityStore, sel.entityUuid(), sel.roleId(), plugin);
        commandBuilder.set("#ReputationBlock.Visible", befriendable);
        commandBuilder.set("#GiftHistoryButton.Visible", befriendable && !viewOnly);
        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (befriendable && pu != null) {
            int rep = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), sel.entityUuid()).getReputation();
            ReputationHeartUi.applyHearts(commandBuilder, REPUTATION_HEART_SLOTS, rep);
            commandBuilder.set(
                "#ReputationBlock.TooltipText",
                rep + "/" + VillagerReputationService.MAX_REPUTATION
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        var plugin = AetherhavenPlugin.get();
        if (plugin == null) return;
        var town = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin).getTown(townId);
        if (town == null || !town.hasMemberOrOwner(playerRef.getUuid())) { close(); return; }
        if (data.action != null) {
            if (List.of("FilterAll", "FilterCore", "FilterTownsfolk").contains(data.action)) {
                residentFilter = data.action.substring(6);
                var residents = TownVillagerDirectory.listResidents(store, town);
                for (int i = 0; i < residents.size(); i++) {
                    var resident = residents.get(i);
                    boolean folk = com.hexvane.aetherhaven.town.TownResidentEligibility.isTownsfolkPoolKind(resident.bindingKind(), resident.roleId(), plugin);
                    if (residentFilter.equals("All") || residentFilter.equals("Townsfolk") == folk) { selectedIndex = i; break; }
                }
                var c = new UICommandBuilder(); var e = new UIEventBuilder();
                build(ref, c, e, store); sendUpdate(c, e, false); return;
            }
            if (data.action.equals("Profile")) {
                var rows = TownVillagerDirectory.listResidents(store, town);
                if (selectedIndex >= 0 && selectedIndex < rows.size())
                    ResidentProfileNavigation.open(playerRef, ref, store, town, rows.get(selectedIndex).entityUuid(), viewOnly);
                return;
            }
            if (data.action.equalsIgnoreCase("SwitchTabPlot")) {
                openPlotManagement(ref, store, 0, false);
                return;
            }
            if (data.action.equalsIgnoreCase("SwitchTabPlayers")) {
                openPlotManagement(ref, store, 1, false);
                return;
            }
            if (data.action.equalsIgnoreCase("BeginMoveBuilding")) {
                openPlotManagement(ref, store, 0, true);
                return;
            }
            if (data.action.equalsIgnoreCase("OpenTownLog")) {
                openTownLog(ref, store);
                return;
            }
            if (data.action.equalsIgnoreCase("OpenBlockPalette")) {
                openBlockPalette(ref, store);
                return;
            }
            if (data.action.equalsIgnoreCase("RescueTeleport")) {
                if (viewOnly) {
                    return;
                }
                handleRescueTeleport(ref, store);
                return;
            }
            if (data.action.equalsIgnoreCase("GiftHistory")) {
                if (viewOnly) {
                    return;
                }
                openGiftHistory(ref, store);
                return;
            }
        }
        if (data.action == null || !data.action.equalsIgnoreCase("Select")) {
            return;
        }
        if (data.index >= 0) {
            selectedIndex = data.index;
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
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
                "#TabMoveButton",
                new EventData().append("Action", "BeginMoveBuilding"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabLogButton",
                new EventData().append("Action", "OpenTownLog"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabPaintButton",
                new EventData().append("Action", "OpenBlockPalette"),
                false
            );
        }
    }

    private void openTownLog(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (managementBlockRef == null || managementBlockPos == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager()
            .openCustomPage(ref, store, new TownLogPage(playerRef, townId, managementBlockRef, managementBlockPos));
    }

    private void openBlockPalette(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (managementBlockRef == null || managementBlockPos == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager()
            .openCustomPage(ref, store, new BlockPalettePage(playerRef, townId, managementBlockRef, managementBlockPos));
    }

    private void handleRescueTeleport(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(townId);
        if(town==null)return;
        var rows=TownVillagerDirectory.listResidents(store,town);
        if(selectedIndex<0||selectedIndex>=rows.size())return;
        rescueResident(playerRef,ref,store,townId,rows.get(selectedIndex).entityUuid());
    }

    public static void rescueResident(PlayerRef playerRef,Ref<EntityStore> ref,Store<EntityStore> store,UUID townId,UUID residentId) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town == null) {
            return;
        }
        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (pu == null) {
            return;
        }
        if (!town.playerHasQuestPermission(pu.getUuid())) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noQuestPermission"));
            return;
        }

        world.execute(
            () -> {
                AetherhavenPlugin p = AetherhavenPlugin.get();
                if (p == null) {
                    return;
                }
                Store<EntityStore> es = world.getEntityStore().getStore();
                Ref<EntityStore> playerEntityRef = playerRef.getReference();
                if (playerEntityRef == null || !playerEntityRef.isValid()) {
                    return;
                }
                TownRecord tr = AetherhavenWorldRegistries.getOrCreateTownManager(world, p).getTown(townId);
                if (tr == null || !tr.hasMemberOrOwner(playerRef.getUuid()) || !tr.playerHasQuestPermission(playerRef.getUuid())) {
                    return;
                }
                List<TownVillagerRow> rows = TownVillagerDirectory.listResidents(es, tr);
                var sel = rows.stream().filter(r -> residentId.equals(r.entityUuid())).findFirst().orElse(null);
                if (sel == null) return;
                Ref<EntityStore> npcRef = es.getExternalData().getRefFromUUID(sel.entityUuid());
                if (npcRef == null || !npcRef.isValid()) {
                    playerRef.sendMessage(Message.translation("aetherhaven_quests_portals.aetherhaven.villager.locateNotLoaded"));
                    return;
                }
                TownVillagerBinding b = es.getComponent(npcRef, TownVillagerBinding.getComponentType());
                if (b == null || !townId.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                    playerRef.sendMessage(Message.translation("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.rescueNotTownResident"));
                    return;
                }
                TransformComponent pTc = es.getComponent(playerEntityRef, TransformComponent.getComponentType());
                if (pTc == null) {
                    return;
                }
                Vector3d pPos = pTc.getPosition();
                float yaw = pTc.getRotation().yaw();
                double side = 1.5;
                double cos = Math.cos(yaw);
                double sin = Math.sin(yaw);
                Vector3d target = new Vector3d(pPos.x + cos * side, pPos.y, pPos.z - sin * side);
                TransformComponent nTc = es.getComponent(npcRef, TransformComponent.getComponentType());
                Rotation3f bodyRot =
                    nTc != null ? new Rotation3f(nTc.getRotation()) : new Rotation3f(0f, yaw, 0f);
                AetherhavenNpcTeleport.apply(npcRef, es, Teleport.createExact(target, bodyRot));
                long now = VillagerAutonomySystem.resolveAutonomyNowMs(es);
                VillagerAutonomySystem.resetAutonomyForRescue(npcRef, es, now);
                com.hexvane.aetherhaven.npc.NpcServicePause.begin(npcRef, es, true);
                playerRef.sendMessage(Message.translation("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.rescueDone"));
            }
        );
    }

    private void openGiftHistory(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return;
        }
        List<TownVillagerRow> rows = TownVillagerDirectory.listResidents(world.getEntityStore().getStore(), town);
        if (selectedIndex < 0 || selectedIndex >= rows.size()) {
            return;
        }
        TownVillagerRow sel = rows.get(selectedIndex);
        if (!VillagerBefriendableResolver.isBefriendableByEntity(store, sel.entityUuid(), sel.roleId(), plugin)) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player
            .getPageManager()
            .openCustomPage(
                ref,
                store,
                new VillagerGiftHistoryPage(
                    playerRef,
                    townId,
                    sel.roleId(),
                    sel.entityUuid(),
                    selectedIndex,
                    managementBlockRef,
                    managementBlockPos
                )
            );
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
                new PlotConstructionPage(playerRef, managementBlockRef, managementBlockPos, true, managementTab, openMoveBuildingModalOnFirstBuild)
            );
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
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("Index", Codec.STRING), (d, s) -> {
                if (s != null && !s.isBlank()) {
                    try {
                        d.index = Integer.parseInt(s.trim());
                    } catch (NumberFormatException ignored) {
                        d.index = 0;
                    }
                }
            }, d -> Integer.toString(d.index))
            .add()
            .build();

        private String action;
        private int index;
    }
}
