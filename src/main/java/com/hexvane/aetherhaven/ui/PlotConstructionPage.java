package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCompleter;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.placement.PlotPlacementOpenHelper;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.HouseResidentAssignment;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberRole;
import com.hexvane.aetherhaven.town.TownMembershipActions;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotConstructionPage extends InteractiveCustomUIPage<PlotConstructionPage.PageData> {
    private static final int MATERIAL_ROW_CAP = 10;
    private static final int BREAK_SETTINGS = 10;
    private static final String MEMBER_ROWS = "#MemberRows";
    private static final int MAX_MEMBER_ROWS = 24;

    private final Ref<ChunkStore> blockRef;
    @Nonnull
    private final Vector3i blockWorldPos;
    private final boolean managementUi;
    /** 0 = Plot, 1 = Players (management UI only). */
    private int managementTab;
    /** Move-building confirmation modal (management block, completed plot). */
    private boolean moveBuildingConfirmOpen;
    /** Open the move-building modal on the first {@link #build} (e.g. returning from town needs). */
    private boolean pendingMoveBuildingModal;
    /**
     * {@code append(ui)} must run only once per page instance; repeating it on every {@link #sendUpdate} duplicates the
     * whole tree and breaks selectors (wrong title, orphan "Materials" label, empty tabs).
     */
    private boolean templateAppended;

    public PlotConstructionPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull Vector3i blockWorldPos,
        boolean managementUi
    ) {
        this(playerRef, blockRef, blockWorldPos, managementUi, 0, false);
    }

    public PlotConstructionPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull Vector3i blockWorldPos,
        boolean managementUi,
        int initialManagementTab,
        boolean openMoveBuildingModalOnFirstBuild
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.blockRef = blockRef;
        this.blockWorldPos = blockWorldPos.clone();
        this.managementUi = managementUi;
        this.managementTab = initialManagementTab;
        this.pendingMoveBuildingModal = openMoveBuildingModalOnFirstBuild;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PlotConstructionPage.ui");
            templateAppended = true;
        }
        if (managementUi && pendingMoveBuildingModal) {
            moveBuildingConfirmOpen = true;
            pendingMoveBuildingModal = false;
        }
        commandBuilder.set(
            "#ShellTitleText.TextSpans",
            managementUi
                ? Message.translation("server.aetherhaven.ui.plotmanagement.title")
                : Message.translation("server.aetherhaven.ui.plotconstruction.title")
        );
        boolean plotTabActive = !managementUi || managementTab == 0;
        commandBuilder.set("#ManagementTabStrip.Visible", managementUi);
        commandBuilder.set("#PlotTabContent.Visible", plotTabActive);
        commandBuilder.set("#PlayersTabContent.Visible", managementUi && managementTab == 1);
        commandBuilder.set("#MoveBuildingModal.Visible", managementUi && moveBuildingConfirmOpen);

        ConstructionDefinition def = resolveDefinition(store, ref);
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;

        if (def == null) {
            commandBuilder.set("#BuildingTitle.TextSpans", Message.raw(managementUi ? "Building" : "Plot sign"));
            commandBuilder.set("#Description.TextSpans", Message.raw("No construction is configured for this plot."));
            commandBuilder.set("#VillagerRow.Visible", false);
            commandBuilder.set("#TreasuryRow.Visible", false);
            commandBuilder.set("#HouseResidentRow.Visible", false);
            commandBuilder.set("#MaterialsHeader.Visible", false);
            for (int i = 0; i < MATERIAL_ROW_CAP; i++) {
                commandBuilder.set("#Mat" + i + ".Visible", false);
            }
            commandBuilder.set("#BuildButton.Disabled", true);
            commandBuilder.set("#PickUpPlotButton.Visible", false);
            commandBuilder.set("#TabNeedsButton.Disabled", true);
            commandBuilder.set("#TabMoveButton.Disabled", true);
            if (managementUi) {
                commandBuilder.set("#TabPlotButton.Disabled", managementTab == 0);
                commandBuilder.set("#TabPlayersButton.Disabled", managementTab == 1);
                bindManagementTabEvents(eventBuilder, false);
                if (managementTab == 1) {
                    buildManagementPlayersTab(ref, store, commandBuilder, eventBuilder);
                } else {
                    commandBuilder.clear(MEMBER_ROWS);
                }
            }
            return;
        }

        PlotInstanceState state = resolvePlotState(store, ref);
        boolean completed = state == PlotInstanceState.COMPLETE;
        boolean hideConstructionDetails = managementUi && completed;

        commandBuilder.set("#BuildingTitle.TextSpans", Message.raw(def.getDisplayName()));
        String desc = def.getDescription() != null ? def.getDescription() : "";
        if (completed) {
            if (hideConstructionDetails) {
                // Management block: hide the completion line (and leave only prefab text if present).
            } else {
                desc = desc.isEmpty() ? "Construction complete." : desc + "\n\nConstruction complete.";
            }
        }
        commandBuilder.set("#Description.TextSpans", Message.raw(desc));
        commandBuilder.set("#Description.Visible", !desc.isBlank());

        commandBuilder.set("#VillagerRow.Visible", false);

        long goldCost = def.getTreasuryGoldCoinCost();
        TownRecord treasuryTown =
            !managementUi && !completed && goldCost > 0 ? resolveTownForPlotSign(store, ref) : null;
        UUIDComponent ucComp = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID playerUuid = ucComp != null ? ucComp.getUuid() : null;
        boolean treasuryPerm =
            treasuryTown != null && playerUuid != null && treasuryTown.playerHasBuildPermission(playerUuid);
        long treasuryBal = treasuryTown != null ? treasuryTown.getTreasuryGoldCoinCount() : 0L;
        boolean treasuryOk = completed || goldCost <= 0 || (treasuryTown != null && treasuryBal >= goldCost && treasuryPerm);
        boolean showTreasury = !hideConstructionDetails && goldCost > 0;
        commandBuilder.set("#TreasuryRow.Visible", showTreasury);
        if (showTreasury) {
            String tLine;
            if (treasuryTown == null) {
                tLine = "Town treasury: " + goldCost + " gold (town not found for this plot.)";
                commandBuilder.set("#TreasuryLabel.Style.TextColor", "#962f2f");
            } else if (!treasuryPerm) {
                tLine = "Town treasury: " + goldCost + " gold (have " + treasuryBal + ") — no permission to spend.";
                commandBuilder.set("#TreasuryLabel.Style.TextColor", "#962f2f");
            } else {
                tLine = "Town treasury: pay " + goldCost + " gold (have " + treasuryBal + ")";
                commandBuilder.set("#TreasuryLabel.Style.TextColor", treasuryBal >= goldCost ? "#3d913f" : "#962f2f");
            }
            commandBuilder.set("#TreasuryLabel.TextSpans", Message.raw(tLine));
        }

        boolean showMaterialsHeader = !hideConstructionDetails && !def.getMaterials().isEmpty();
        commandBuilder.set("#MaterialsHeader.Visible", showMaterialsHeader);
        String lang = this.playerRef.getLanguage();
        int mi = 0;
        if (!hideConstructionDetails) {
            for (; mi < def.getMaterials().size() && mi < MATERIAL_ROW_CAP; mi++) {
                var m = def.getMaterials().get(mi);
                int need = m.getCount();
                int has = inv != null ? InventoryMaterials.count(inv, m) : 0;
                boolean ok = completed || has >= need;
                String itemLabel = materialLabelForUi(lang, m);
                commandBuilder.set("#Mat" + mi + ".Visible", true);
                commandBuilder.set("#Mat" + mi + " #Line.TextSpans", Message.raw(itemLabel + " x" + need + " (have " + has + ")"));
                commandBuilder.set("#Mat" + mi + " #Line.Style.TextColor", ok ? "#3d913f" : "#962f2f");
            }
        }
        for (; mi < MATERIAL_ROW_CAP; mi++) {
            commandBuilder.set("#Mat" + mi + ".Visible", false);
        }

        boolean matsOk = completed || (inv != null && InventoryMaterials.hasAll(inv, def.getMaterials()));
        boolean canBuild = !managementUi && !completed && matsOk && treasuryOk;
        commandBuilder.set("#BuildButton.Disabled", !canBuild);

        boolean canPickupPlot =
            !managementUi
                && !completed
                && def.getPlotTokenItemId() != null
                && !def.getPlotTokenItemId().isBlank();
        commandBuilder.set("#PickUpPlotButton.Visible", canPickupPlot);
        commandBuilder.set("#PickUpPlotButton.Disabled", !canPickupPlot);

        boolean needsMoveTabsOk = managementUi && completed;
        commandBuilder.set("#TabNeedsButton.Disabled", !needsMoveTabsOk);
        commandBuilder.set("#TabMoveButton.Disabled", !needsMoveTabsOk);
        if (managementUi) {
            commandBuilder.set("#TabPlotButton.Disabled", managementTab == 0);
            commandBuilder.set("#TabPlayersButton.Disabled", managementTab == 1);
        }

        boolean showHouseResident =
            managementUi
                && completed
                && AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(def.getId());
        commandBuilder.set("#HouseResidentRow.Visible", showHouseResident);
        if (showHouseResident && plotTabActive) {
            commandBuilder.set(
                "#HouseResidentHint.TextSpans",
                Message.raw("Assign a villager who lives here (completes their house quest when it matches).")
            );
            Store<ChunkStore> cs = blockRef.getStore();
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            UUID plotUuid = null;
            UUID townUuid = null;
            if (mb != null && mb.getPlotId() != null && !mb.getPlotId().isBlank()) {
                try {
                    plotUuid = UUID.fromString(mb.getPlotId().trim());
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (mb != null && mb.getTownId() != null && !mb.getTownId().isBlank()) {
                try {
                    townUuid = UUID.fromString(mb.getTownId().trim());
                } catch (IllegalArgumentException ignored) {
                }
            }
            ObjectArrayList<DropdownEntryInfo> resEntries = new ObjectArrayList<>();
            resEntries.add(new DropdownEntryInfo(LocalizableString.fromString("Unassigned"), ""));
            String selectedValue = "";
            if (plotUuid != null && townUuid != null) {
                AetherhavenPlugin plug = AetherhavenPlugin.get();
                if (plug != null) {
                    World world = store.getExternalData().getWorld();
                    TownManager townManager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plug);
                    TownRecord town = townManager.getTown(townUuid);
                    if (town != null) {
                        PlotInstance pi = town.findPlotById(plotUuid);
                        UUID cur = pi != null ? pi.getHomeResidentEntityUuid() : null;
                        if (cur != null) {
                            selectedValue = cur.toString();
                        }
                        List<HouseResidentRow> rows = collectHouseResidentRows(store, town);
                        for (HouseResidentRow row : rows) {
                            resEntries.add(
                                new DropdownEntryInfo(LocalizableString.fromString(row.label()), row.entityUuid().toString())
                            );
                        }
                    }
                }
            }
            commandBuilder.set("#HouseResidentDropdown #Input.Entries", resEntries);
            commandBuilder.set("#HouseResidentDropdown #Input.Value", selectedValue.isEmpty() ? "" : selectedValue);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AssignHouseResidentButton",
                new EventData().append("Action", "AssignHouseResident").append("@HouseResidentUuid", "#HouseResidentDropdown #Input.Value"),
                false
            );
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BuildButton",
            new EventData().append("Action", "Build"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#PickUpPlotButton",
            new EventData().append("Action", "PickupPlot"),
            false
        );

        if (managementUi) {
            bindManagementTabEvents(eventBuilder, needsMoveTabsOk);
            if (managementTab == 1) {
                buildManagementPlayersTab(ref, store, commandBuilder, eventBuilder);
            } else {
                commandBuilder.clear(MEMBER_ROWS);
            }
        }
        if (managementUi && moveBuildingConfirmOpen) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MoveBuildingConfirmButton",
                new EventData().append("Action", "ConfirmMoveBuilding"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MoveBuildingCancelButton",
                new EventData().append("Action", "CancelMoveBuilding"),
                false
            );
        }
    }

    private void bindManagementTabEvents(@Nonnull UIEventBuilder eventBuilder, boolean needsMoveTabsEnabled) {
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

    private void buildManagementPlayersTab(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder
    ) {
        World world = store.getExternalData().getWorld();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || uc == null) {
            commandBuilder.set("#PlayersHint.TextSpans", Message.raw("Could not load player data."));
            commandBuilder.clear(MEMBER_ROWS);
            return;
        }
        TownRecord town = resolveManagementTown(store);
        if (town == null) {
            commandBuilder.set("#PlayersHint.TextSpans", Message.raw("Town data not found for this block."));
            commandBuilder.clear(MEMBER_ROWS);
            return;
        }
        UUID viewer = uc.getUuid();
        boolean viewerOwner = town.getOwnerUuid().equals(viewer);
        commandBuilder.set(
            "#PlayersHint.TextSpans",
            viewerOwner
                ? Message.translation("server.aetherhaven.ui.plotmanagement.playersHint")
                : Message.translation("server.aetherhaven.ui.plotmanagement.playersHintReadOnly")
        );
        commandBuilder.set("#InviteLabel.Visible", viewerOwner);
        commandBuilder.set("#InvitePlayerInput.Visible", viewerOwner);
        commandBuilder.set("#InviteSendButton.Visible", viewerOwner);
        if (viewerOwner) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#InviteSendButton",
                new EventData().append("Action", "InviteMember").append("@InviteName", "#InvitePlayerInput.Value"),
                false
            );
        }

        commandBuilder.clear(MEMBER_ROWS);
        ObjectArrayList<DropdownEntryInfo> roleEntries = new ObjectArrayList<>();
        for (TownMemberRole r : TownMemberRole.values()) {
            roleEntries.add(new DropdownEntryInfo(LocalizableString.fromString(r.name()), r.name()));
        }

        List<UUID> ordered = new ArrayList<>();
        ordered.add(town.getOwnerUuid());
        List<UUID> mem = new ArrayList<>(town.getMemberPlayerUuids());
        mem.sort(Comparator.comparing(u -> TownPlayerLookup.displayNameForUuid(world, u), String.CASE_INSENSITIVE_ORDER));
        ordered.addAll(mem);

        int n = Math.min(ordered.size(), MAX_MEMBER_ROWS);
        for (int i = 0; i < n; i++) {
            UUID pid = ordered.get(i);
            boolean isOwner = pid.equals(town.getOwnerUuid());
            String rowPath = MEMBER_ROWS + "[" + i + "]";
            commandBuilder.append(MEMBER_ROWS, "Aetherhaven/TownMemberRow.ui");
            String display = TownPlayerLookup.displayNameForUuid(world, pid);
            commandBuilder.set(rowPath + " #NameLabel.TextSpans", Message.raw(display));
            if (isOwner) {
                commandBuilder.set(rowPath + " #RoleReadOnly.Visible", true);
                commandBuilder.set(rowPath + " #RoleReadOnly.TextSpans", Message.translation("server.aetherhaven.ui.plotmanagement.roleOwner"));
                commandBuilder.set(rowPath + " #RoleDropdown.Visible", false);
                commandBuilder.set(rowPath + " #KickButton.Visible", false);
            } else {
                TownMemberRole role = town.getMemberRoleOrNull(pid);
                String roleName = role != null ? role.name() : TownMemberRole.BOTH.name();
                if (viewerOwner) {
                    commandBuilder.set(rowPath + " #RoleReadOnly.Visible", false);
                    commandBuilder.set(rowPath + " #RoleDropdown.Visible", true);
                    commandBuilder.set(rowPath + " #RoleDropdown.Entries", roleEntries);
                    commandBuilder.set(rowPath + " #RoleDropdown.Value", roleName);
                    commandBuilder.set(rowPath + " #KickButton.Visible", true);
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        rowPath + " #RoleDropdown",
                        new EventData()
                            .append("Action", "ChangeMemberRole")
                            .append("@MemberUuid", pid.toString())
                            .append("@Role", rowPath + " #RoleDropdown.Value"),
                        false
                    );
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        rowPath + " #KickButton",
                        new EventData().append("Action", "KickMember").append("@MemberUuid", pid.toString()),
                        false
                    );
                } else {
                    commandBuilder.set(rowPath + " #RoleReadOnly.Visible", true);
                    commandBuilder.set(rowPath + " #RoleReadOnly.TextSpans", Message.raw(roleName));
                    commandBuilder.set(rowPath + " #RoleDropdown.Visible", false);
                    commandBuilder.set(rowPath + " #KickButton.Visible", false);
                }
            }
        }
    }

    @Nullable
    private TownRecord resolveManagementTown(@Nonnull Store<EntityStore> store) {
        if (!managementUi) {
            return null;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
        if (mb == null || mb.getTownId() == null || mb.getTownId().isBlank()) {
            return null;
        }
        try {
            UUID tid = UUID.fromString(mb.getTownId().trim());
            AetherhavenPlugin p = AetherhavenPlugin.get();
            if (p == null) {
                return null;
            }
            World world = store.getExternalData().getWorld();
            return AetherhavenWorldRegistries.getOrCreateTownManager(world, p).getTown(tid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action != null && data.action.equalsIgnoreCase("BeginMoveBuilding")) {
            if (!managementUi) {
                return;
            }
            PlotInstanceState stBegin = resolvePlotState(store, ref);
            if (stBegin != PlotInstanceState.COMPLETE) {
                return;
            }
            moveBuildingConfirmOpen = true;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("CancelMoveBuilding")) {
            moveBuildingConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("ConfirmMoveBuilding")) {
            if (!managementUi) {
                return;
            }
            moveBuildingConfirmOpen = false;
            PlotInstanceState stMove = resolvePlotState(store, ref);
            if (stMove != PlotInstanceState.COMPLETE) {
                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder ev = new UIEventBuilder();
                build(ref, cmd, ev, store);
                sendUpdate(cmd, ev, false);
                return;
            }
            Store<ChunkStore> csMove = blockRef.getStore();
            ManagementBlock mbMove = csMove.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mbMove == null || mbMove.getPlotId().isBlank() || mbMove.getTownId().isBlank()) {
                return;
            }
            UUID plotIdMove;
            UUID townIdMove;
            try {
                plotIdMove = UUID.fromString(mbMove.getPlotId().trim());
                townIdMove = UUID.fromString(mbMove.getTownId().trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            Player playerMove = store.getComponent(ref, Player.getComponentType());
            if (playerMove != null) {
                PlotPlacementPage placementPage = PlotPlacementOpenHelper.openForMove(ref, store, playerRef, townIdMove, plotIdMove);
                if (placementPage != null) {
                    playerMove.getPageManager().openCustomPage(ref, store, placementPage);
                }
            }
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("SwitchTabPlot")) {
            if (!managementUi) {
                return;
            }
            managementTab = 0;
            moveBuildingConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("SwitchTabPlayers")) {
            if (!managementUi) {
                return;
            }
            managementTab = 1;
            moveBuildingConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("ChangeMemberRole")) {
            if (!managementUi || data.memberUuid == null || data.role == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            TownRecord town = resolveManagementTown(store);
            if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
                return;
            }
            UUID memberId;
            try {
                memberId = UUID.fromString(data.memberUuid.trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            TownMemberRole role;
            try {
                role = TownMemberRole.valueOf(data.role.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String err = TownMembershipActions.trySetMemberRole(world, tm, town, playerRef, memberId, role);
            if (err != null) {
                playerRef.sendMessage(Message.raw(err));
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("KickMember")) {
            if (!managementUi || data.memberUuid == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            TownRecord town = resolveManagementTown(store);
            if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
                return;
            }
            UUID memberId;
            try {
                memberId = UUID.fromString(data.memberUuid.trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String err = TownMembershipActions.tryKickMemberUuid(world, tm, town, playerRef, memberId);
            if (err != null) {
                playerRef.sendMessage(Message.raw(err));
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("InviteMember")) {
            if (!managementUi || data.inviteName == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            TownRecord town = resolveManagementTown(store);
            if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String err = TownMembershipActions.tryInviteMember(world, tm, town, uc.getUuid(), playerRef, data.inviteName);
            if (err != null) {
                playerRef.sendMessage(Message.raw(err));
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("AssignHouseResident")) {
            if (!managementUi) {
                return;
            }
            PlotInstanceState st = resolvePlotState(store, ref);
            if (st != PlotInstanceState.COMPLETE) {
                return;
            }
            ConstructionDefinition def = resolveDefinition(store, ref);
            if (def == null || !AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(def.getId())) {
                return;
            }
            Store<ChunkStore> cs = blockRef.getStore();
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getPlotId().isBlank() || mb.getTownId().isBlank()) {
                return;
            }
            UUID plotId;
            UUID townId;
            try {
                plotId = UUID.fromString(mb.getPlotId().trim());
                townId = UUID.fromString(mb.getTownId().trim());
            } catch (IllegalArgumentException e) {
                return;
            }
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
            UUID residentUuid = null;
            String raw = data.houseResidentUuid;
            if (raw != null && !raw.isBlank()) {
                try {
                    residentUuid = UUID.fromString(raw.trim());
                } catch (IllegalArgumentException e) {
                    sendBuildError(store, ref, "Invalid villager selection.");
                    return;
                }
            }
            HouseResidentAssignment.assignResident(town, plotId, residentUuid, tm, world, store);
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(Message.raw(residentUuid == null ? "Cleared home assignment." : "Home assignment updated."));
            }
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("PickupPlot")) {
            if (managementUi) {
                return;
            }
            PlotInstanceState st = resolvePlotState(store, ref);
            if (st == PlotInstanceState.COMPLETE) {
                return;
            }
            ConstructionDefinition def = resolveDefinition(store, ref);
            if (def == null) {
                return;
            }
            String tokenId = def.getPlotTokenItemId();
            if (tokenId == null || tokenId.isBlank()) {
                return;
            }
            Store<ChunkStore> cstore = blockRef.getStore();
            PlotSignBlock plot = cstore.getComponent(blockRef, PlotSignBlock.getComponentType());
            if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
                sendBuildError(store, ref, "This plot sign has no plot id (legacy); replace the sign.");
                return;
            }
            UUID plotId;
            try {
                plotId = UUID.fromString(plot.getPlotId().trim());
            } catch (IllegalArgumentException e) {
                sendBuildError(store, ref, "Invalid plot id on sign.");
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            World world = store.getExternalData().getWorld();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
            if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
                sendBuildError(store, ref, "Only the town owner can pick up this plot.");
                return;
            }
            if (town.findPlotById(plotId) == null) {
                sendBuildError(store, ref, "This plot is not registered in your town.");
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            CombinedItemContainer inv =
                player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
            ItemStack tokenStack = new ItemStack(tokenId, 1);
            if (inv == null || !inv.canAddItemStack(tokenStack)) {
                sendBuildError(store, ref, "Make room in your inventory for the plot token.");
                return;
            }
            if (!town.removePlotInstance(plotId)) {
                sendBuildError(store, ref, "Could not remove plot from town data.");
                return;
            }
            tm.updateTown(town);
            world.breakBlock(blockWorldPos.x, blockWorldPos.y, blockWorldPos.z, BREAK_SETTINGS);
            if (player != null) {
                ItemStackTransaction giveTx = player.giveItem(tokenStack, ref, store);
                if (!giveTx.succeeded()) {
                    sendBuildError(store, ref, "Could not add plot token to inventory.");
                    return;
                }
            }
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(Message.raw("Plot removed and plot token returned."));
            }
            close();
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("OpenTownNeeds")) {
            if (!managementUi) {
                return;
            }
            moveBuildingConfirmOpen = false;
            PlotInstanceState st = resolvePlotState(store, ref);
            if (st != PlotInstanceState.COMPLETE) {
                return;
            }
            Store<ChunkStore> cs = blockRef.getStore();
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getTownId().isBlank()) {
                return;
            }
            UUID townUuid;
            try {
                townUuid = UUID.fromString(mb.getTownId().trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                // openCustomPage replaces this UI; do not call close() or Page.None clears the new page.
                player.getPageManager()
                    .openCustomPage(ref, store, new VillagerNeedsOverviewPage(playerRef, townUuid, blockRef, blockWorldPos));
            }
            return;
        }
        if (data.action == null || !data.action.equalsIgnoreCase("Build")) {
            return;
        }
        if (managementUi) {
            return;
        }
        ConstructionDefinition def = resolveDefinition(store, ref);
        if (def == null) {
            return;
        }
        PlotInstanceState state = resolvePlotState(store, ref);
        if (state == PlotInstanceState.COMPLETE) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (!InventoryMaterials.hasAll(inv, def.getMaterials())) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        Store<ChunkStore> cstore = blockRef.getStore();
        PlotSignBlock plot = cstore.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            sendBuildError(store, ref, "This plot sign has no plot id (legacy); replace the sign.");
            return;
        }
        UUID plotId;
        try {
            plotId = UUID.fromString(plot.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            sendBuildError(store, ref, "Invalid plot id on sign.");
            return;
        }
        long goldCost = def.getTreasuryGoldCoinCost();
        if (goldCost > 0) {
            AetherhavenPlugin plugPre = AetherhavenPlugin.get();
            World worldPre = store.getExternalData().getWorld();
            if (plugPre == null) {
                return;
            }
            TownManager tmPre = AetherhavenWorldRegistries.getOrCreateTownManager(worldPre, plugPre);
            TownRecord tr = tmPre.findTownOwningPlot(plotId);
            if (tr == null) {
                sendBuildError(store, ref, "No town owns this plot.");
                return;
            }
            if (!tr.playerHasBuildPermission(uc.getUuid())) {
                sendBuildError(store, ref, "You cannot spend town treasury for this build.");
                return;
            }
            if (tr.getTreasuryGoldCoinCount() < goldCost) {
                sendBuildError(store, ref, "Not enough gold in the town treasury.");
                return;
            }
            tr.addTreasuryGoldCoins(-goldCost);
            tmPre.updateTown(tr);
        }

        InventoryMaterials.removeAll(inv, def.getMaterials());

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i signPos = blockWorldPos;
        Rotation yaw = PlotBlockRotationUtil.readBlockYaw(world, signPos);
        Vector3i anchor = def.resolvePrefabAnchorWorld(signPos, yaw);
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            sendBuildError(store, ref, "Prefab not found for path: " + def.getPrefabPath());
            return;
        }
        IPrefabBuffer buffer = PrefabBufferUtil.getCached(prefabPath);
        var cfg = plugin.getConfig().get();
        UUID ownerUuid = uc.getUuid();
        Runnable onComplete =
            () -> {
                world.breakBlock(signPos.x, signPos.y, signPos.z, BREAK_SETTINGS);
                ConstructionCompleter.finishBuild(world, plugin, ownerUuid, plotId, anchor, yaw);
            };
        ConstructionAnimator.start(
            plugin,
            world,
            anchor,
            yaw,
            true,
            buffer,
            store,
            cfg.getConstructionBlocksPerTick(),
            cfg.getConstructionMinIntervalMs(),
            onComplete
        );
        close();
    }

    private void sendBuildError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String text) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw(text));
        }
    }

    @Nullable
    private TownRecord resolveTownForPlotSign(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return null;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        PlotSignBlock plot = cs.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            return null;
        }
        UUID plotId;
        try {
            plotId = UUID.fromString(plot.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
        return tm.findTownOwningPlot(plotId);
    }

    @Nullable
    private ConstructionDefinition resolveDefinition(@Nonnull Store<EntityStore> entityStore, @Nonnull Ref<EntityStore> playerRef) {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return null;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        World world = entityStore.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);

        if (managementUi) {
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getTownId().isBlank() || mb.getPlotId().isBlank()) {
                return null;
            }
            try {
                TownRecord town = tm.getTown(UUID.fromString(mb.getTownId().trim()));
                if (town == null) {
                    return null;
                }
                PlotInstance pi = town.findPlotById(UUID.fromString(mb.getPlotId().trim()));
                if (pi == null) {
                    return null;
                }
                return p.getConstructionCatalog().get(pi.getConstructionId());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        PlotSignBlock plot = cs.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null) {
            return null;
        }
        return p.getConstructionCatalog().get(plot.getConstructionId());
    }

    @Nonnull
    private PlotInstanceState resolvePlotState(@Nonnull Store<EntityStore> entityStore, @Nonnull Ref<EntityStore> playerRef) {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return PlotInstanceState.BLUEPRINTING;
        }
        World world = entityStore.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
        Store<ChunkStore> cs = blockRef.getStore();

        if (managementUi) {
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getTownId().isBlank() || mb.getPlotId().isBlank()) {
                return PlotInstanceState.COMPLETE;
            }
            try {
                TownRecord town = tm.getTown(UUID.fromString(mb.getTownId().trim()));
                if (town == null) {
                    return PlotInstanceState.COMPLETE;
                }
                PlotInstance pi = town.findPlotById(UUID.fromString(mb.getPlotId().trim()));
                return pi != null ? pi.getState() : PlotInstanceState.COMPLETE;
            } catch (IllegalArgumentException e) {
                return PlotInstanceState.COMPLETE;
            }
        }

        PlotSignBlock plot = cs.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            return PlotInstanceState.BLUEPRINTING;
        }
        UUIDComponent uc = entityStore.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return PlotInstanceState.BLUEPRINTING;
        }
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
        if (town == null) {
            return PlotInstanceState.BLUEPRINTING;
        }
        try {
            PlotInstance pi = town.findPlotById(UUID.fromString(plot.getPlotId().trim()));
            return pi != null ? pi.getState() : PlotInstanceState.BLUEPRINTING;
        } catch (IllegalArgumentException e) {
            return PlotInstanceState.BLUEPRINTING;
        }
    }

    @Nonnull
    private static String itemLabelForUi(@Nullable String language, @Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return itemId;
        }
        String trKey = item.getTranslationKey();
        String lang = language != null ? language : "en-US";
        String resolved = I18nModule.get().getMessage(lang, trKey);
        return resolved != null ? resolved : itemId;
    }

    @Nonnull
    private static String materialLabelForUi(@Nullable String language, @Nonnull MaterialRequirement m) {
        String rt = m.getResourceTypeId();
        if (rt != null && !rt.isBlank()) {
            String id = rt.trim();
            String lang = language != null ? language : "en-US";
            // Lang files are keyed as <fileBasename>.<entryKey>; vanilla uses Server/Languages/*/server.lang
            // so entries like resourceType.Wood_Trunk.name become server.resourceType.Wood_Trunk.name
            String key = "server.resourceType." + id + ".name";
            String resolved = I18nModule.get().getMessage(lang, key);
            if (resolved != null) {
                return resolved;
            }
            ResourceType asset = ResourceType.getAssetMap().getAsset(id);
            if (asset != null) {
                String n = asset.getName();
                if (n != null && !n.isBlank()) {
                    return n;
                }
            }
            return id;
        }
        String itemId = m.getItemId();
        return itemId != null && !itemId.isBlank() ? itemLabelForUi(language, itemId) : "?";
    }

    private record HouseResidentRow(@Nonnull String label, @Nonnull UUID entityUuid) {}

    @Nonnull
    private static List<HouseResidentRow> collectHouseResidentRows(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) {
            return List.of();
        }
        UUID tid = town.getTownId();
        Map<UUID, HouseResidentRow> byUuid = new LinkedHashMap<>();
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), npcType);
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, npcType);
                    if (uc == null || npc == null || npc.getRoleName() == null) {
                        continue;
                    }
                    UUID u = uc.getUuid();
                    String label = NpcPortraitProvider.displayLabelForRoleId(npc.getRoleName());
                    byUuid.put(u, new HouseResidentRow(label, u));
                }
            }
        );
        addHouseFallbackIfMissing(byUuid, town.getElderEntityUuid(), AetherhavenConstants.ELDER_NPC_ROLE_ID);
        addHouseFallbackIfMissing(byUuid, town.getInnkeeperEntityUuid(), AetherhavenConstants.INNKEEPER_NPC_ROLE_ID);
        List<HouseResidentRow> out = new ArrayList<>(byUuid.values());
        out.sort(Comparator.comparing(HouseResidentRow::label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static void addHouseFallbackIfMissing(
        @Nonnull Map<UUID, HouseResidentRow> byUuid, @Nullable UUID entityUuid, @Nonnull String roleId
    ) {
        if (entityUuid == null || byUuid.containsKey(entityUuid)) {
            return;
        }
        byUuid.put(
            entityUuid,
            new HouseResidentRow(NpcPortraitProvider.displayLabelForRoleId(roleId), entityUuid)
        );
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@MemberUuid", Codec.STRING), (d, v) -> d.memberUuid = v, d -> d.memberUuid)
            .add()
            .append(new KeyedCodec<>("@Role", Codec.STRING), (d, v) -> d.role = v, d -> d.role)
            .add()
            .append(new KeyedCodec<>("@InviteName", Codec.STRING), (d, v) -> d.inviteName = v, d -> d.inviteName)
            .add()
            .append(new KeyedCodec<>("@HouseResidentUuid", Codec.STRING), (d, v) -> d.houseResidentUuid = v, d -> d.houseResidentUuid)
            .add()
            .build();

        private String action;
        @Nullable
        private String memberUuid;
        @Nullable
        private String role;
        @Nullable
        private String inviteName;
        private String houseResidentUuid;
    }
}
