package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
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

public final class VillagerNeedsOverviewPage extends InteractiveCustomUIPage<VillagerNeedsOverviewPage.PageData> {
    private static final String VILLAGER_ROWS = "#VillagerRows";
    private static final String REPUTATION_HEART_SLOTS = "#ReputationHeartSlots";
    private static final int MAX_ROWS = 16;

    private final UUID townId;
    @Nullable
    private final Ref<ChunkStore> managementBlockRef;
    @Nullable
    private final Vector3i managementBlockPos;
    private int selectedIndex;
    /** -1 = default (0); set when reopening (e.g. from gift history back). */
    private final int initialVillagerIndex;
    /** {@code append(ui)} must run only once per page instance; repeating it on every {@link #sendUpdate} duplicates the whole tree. */
    private boolean templateAppended;
    private boolean reputationHeartSlotsAppended;

    public VillagerNeedsOverviewPage(@Nonnull PlayerRef playerRef, @Nonnull UUID townId) {
        this(playerRef, townId, null, null, -1);
    }

    public VillagerNeedsOverviewPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nullable Ref<ChunkStore> managementBlockRef,
        @Nullable Vector3i managementBlockPos
    ) {
        this(playerRef, townId, managementBlockRef, managementBlockPos, -1);
    }

    public VillagerNeedsOverviewPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nullable Ref<ChunkStore> managementBlockRef,
        @Nullable Vector3i managementBlockPos,
        int initialVillagerIndex
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.managementBlockRef = managementBlockRef;
        this.managementBlockPos = managementBlockPos != null ? managementBlockPos.clone() : null;
        this.initialVillagerIndex = initialVillagerIndex;
        if (initialVillagerIndex >= 0) {
            this.selectedIndex = initialVillagerIndex;
        }
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
        if (templateAppended && !reputationHeartSlotsAppended) {
            for (int h = 0; h < 10; h++) {
                commandBuilder.append(REPUTATION_HEART_SLOTS, "Aetherhaven/HeartSlot.ui");
            }
            reputationHeartSlotsAppended = true;
        }
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
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        boolean showMgmtTabs = managementBlockRef != null && managementBlockPos != null;
        commandBuilder.set("#ManagementTabStrip.Visible", showMgmtTabs);
        if (showMgmtTabs) {
            boolean needsOk = computeNeedsMoveTabsOk(store);
            commandBuilder.set("#TabPlotButton.Disabled", false);
            commandBuilder.set("#TabPlayersButton.Disabled", false);
            commandBuilder.set("#TabNeedsButton.Disabled", true);
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

        commandBuilder.set("#RescueTeleportButton.Visible", true);
        commandBuilder.set("#GiftHistoryButton.Visible", true);
        commandBuilder.set("#Hint.Visible", false);
        commandBuilder.clear(VILLAGER_ROWS);
        int n = Math.min(rows.size(), MAX_ROWS);
        for (int i = 0; i < n; i++) {
            TownVillagerRow r = rows.get(i);
            commandBuilder.append(VILLAGER_ROWS, "Aetherhaven/VillagerNeedsRow.ui");
            String row = VILLAGER_ROWS + "[" + i + "]";
            commandBuilder.set(
                row + " #Pick #Label.TextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.npcRoles." + r.roleId() + ".name")
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Pick",
                new EventData().append("Action", "Select").append("Index", Integer.toString(i)),
                false
            );
        }

        TownVillagerRow sel = rows.get(selectedIndex);
        VillagerNeeds needs = findNeeds(entityStore, sel.entityUuid());
        float hunger = needs != null ? needs.getHunger() / VillagerNeeds.MAX : 0.5f;
        float energy = needs != null ? needs.getEnergy() / VillagerNeeds.MAX : 0.5f;
        float fun = needs != null ? needs.getFun() / VillagerNeeds.MAX : 0.5f;
        commandBuilder.set("#HungerBar.Value", hunger);
        commandBuilder.set("#EnergyBar.Value", energy);
        commandBuilder.set("#FunBar.Value", fun);
        commandBuilder.set("#Portrait.AssetPath", NpcPortraitProvider.portraitPathForRoleId(sel.roleId()));

        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (pu != null) {
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
        if (data.action != null) {
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
            if (data.action.equalsIgnoreCase("RescueTeleport")) {
                handleRescueTeleport(ref, store);
                return;
            }
            if (data.action.equalsIgnoreCase("GiftHistory")) {
                openGiftHistory(ref, store);
                return;
            }
        }
        if (data.action == null || !data.action.equalsIgnoreCase("Select")) {
            return;
        }
        if (data.index >= 0 && data.index < MAX_ROWS) {
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
        }
    }

    private void handleRescueTeleport(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
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
        int idx = selectedIndex;
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
                if (tr == null) {
                    return;
                }
                List<TownVillagerRow> rows = TownVillagerDirectory.listResidents(es, tr);
                if (rows.isEmpty() || idx < 0 || idx >= rows.size()) {
                    return;
                }
                TownVillagerRow sel = rows.get(idx);
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
                float yaw = pTc.getRotation().getYaw();
                double side = 1.5;
                double cos = Math.cos(yaw);
                double sin = Math.sin(yaw);
                Vector3d target = new Vector3d(pPos.x + cos * side, pPos.y, pPos.z - sin * side);
                TransformComponent nTc = es.getComponent(npcRef, TransformComponent.getComponentType());
                Vector3f bodyRot = nTc != null ? nTc.getRotation().clone() : new Vector3f(yaw, 0f, 0f);
                es.addComponent(npcRef, Teleport.getComponentType(), Teleport.createExact(target, bodyRot));
                long now = VillagerAutonomySystem.resolveAutonomyNowMs(es);
                VillagerAutonomySystem.resetAutonomyForRescue(npcRef, es, now);
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

    @Nullable
    private static VillagerNeeds findNeeds(@Nonnull Store<EntityStore> store, @Nonnull UUID entityUuid) {
        VillagerNeeds[] found = new VillagerNeeds[1];
        store.forEachChunk(
            Query.and(VillagerNeeds.getComponentType(), UUIDComponent.getComponentType()),
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                if (found[0] != null) {
                    return;
                }
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    UUIDComponent u = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (u != null && entityUuid.equals(u.getUuid())) {
                        found[0] = archetypeChunk.getComponent(i, VillagerNeeds.getComponentType());
                        return;
                    }
                }
            }
        );
        return found[0];
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
