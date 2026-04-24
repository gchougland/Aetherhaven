package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.placement.CharterRelocationSession;
import com.hexvane.aetherhaven.placement.CharterRelocationSessions;
import com.hexvane.aetherhaven.placement.PlotPlacementOpenHelper;
import com.hexvane.aetherhaven.placement.PlotPreviewSpawner;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownDissolutionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CharterTownPage extends InteractiveCustomUIPage<CharterTownPage.PageData> {
    private final Ref<ChunkStore> charterBlockRef;
    /**
     * {@code append(ui)} must run only once per page instance; repeating it on every {@link #sendUpdate} duplicates the
     * whole tree and stacks a second blank page.
     */
    private boolean templateAppended;
    /** Owner clicked Dissolve once; second step requires Confirm or Cancel. */
    private boolean dissolveConfirmOpen;
    /** Owner clicked Move charter once; second step requires Confirm or Cancel. */
    private boolean charterRelocateConfirmOpen;

    public CharterTownPage(@Nonnull PlayerRef playerRef, @Nonnull Ref<ChunkStore> charterBlockRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.charterBlockRef = charterBlockRef;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/CharterTownPage.ui");
            templateAppended = true;
        }
        Store<ChunkStore> cs = charterBlockRef.getStore();
        CharterBlock ch = cs.getComponent(charterBlockRef, CharterBlock.getComponentType());
        String townIdStr = ch != null ? ch.getTownId() : "";
        if (townIdStr.isEmpty()) {
            commandBuilder.set("#TownInfo.TextSpans", Message.translation("server.aetherhaven.chartertown.info.notLinked"));
            commandBuilder.set("#TownNameEditor.Visible", false);
            commandBuilder.set("#OwnerOnlyHint.Visible", false);
            commandBuilder.set("#TownInfoSeparator.Visible", false);
            commandBuilder.set("#DissolveButton.Visible", false);
            commandBuilder.set("#DissolveHint.Visible", false);
            commandBuilder.set("#CharterConfirmModal.Visible", false);
            commandBuilder.set("#CharterTopBar.Visible", false);
            commandBuilder.set("#MoveCharterButton.Visible", false);
            commandBuilder.set("#MoveCharterHint.Visible", false);
            return;
        }
        UUID townId;
        try {
            townId = UUID.fromString(townIdStr);
        } catch (IllegalArgumentException e) {
            commandBuilder.set("#TownInfo.TextSpans", Message.translation("server.aetherhaven.chartertown.info.invalidTownId"));
            commandBuilder.set("#TownNameEditor.Visible", false);
            commandBuilder.set("#OwnerOnlyHint.Visible", false);
            commandBuilder.set("#TownInfoSeparator.Visible", false);
            commandBuilder.set("#DissolveButton.Visible", false);
            commandBuilder.set("#DissolveHint.Visible", false);
            commandBuilder.set("#CharterConfirmModal.Visible", false);
            commandBuilder.set("#CharterTopBar.Visible", false);
            commandBuilder.set("#MoveCharterButton.Visible", false);
            commandBuilder.set("#MoveCharterHint.Visible", false);
            return;
        }
        World world = store.getExternalData().getWorld();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            commandBuilder.set("#TownInfo.TextSpans", Message.translation("server.aetherhaven.common.pluginNotLoaded"));
            commandBuilder.set("#TownNameEditor.Visible", false);
            commandBuilder.set("#OwnerOnlyHint.Visible", false);
            commandBuilder.set("#TownInfoSeparator.Visible", false);
            commandBuilder.set("#DissolveButton.Visible", false);
            commandBuilder.set("#DissolveHint.Visible", false);
            commandBuilder.set("#CharterConfirmModal.Visible", false);
            commandBuilder.set("#CharterTopBar.Visible", false);
            commandBuilder.set("#MoveCharterButton.Visible", false);
            commandBuilder.set("#MoveCharterHint.Visible", false);
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord tr = tm.getTown(townId);
        if (tr == null) {
            commandBuilder.set("#TownInfo.TextSpans", Message.translation("server.aetherhaven.chartertown.info.townDataMissing"));
            commandBuilder.set("#TownNameEditor.Visible", false);
            commandBuilder.set("#OwnerOnlyHint.Visible", false);
            commandBuilder.set("#TownInfoSeparator.Visible", false);
            commandBuilder.set("#DissolveButton.Visible", false);
            commandBuilder.set("#DissolveHint.Visible", false);
            commandBuilder.set("#CharterConfirmModal.Visible", false);
            commandBuilder.set("#CharterTopBar.Visible", false);
            commandBuilder.set("#MoveCharterButton.Visible", false);
            commandBuilder.set("#MoveCharterHint.Visible", false);
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        boolean owner = uc != null && tr.getOwnerUuid().equals(uc.getUuid());
        if (!owner) {
            dissolveConfirmOpen = false;
            charterRelocateConfirmOpen = false;
        }
        commandBuilder.set("#TownNameEditor.Visible", owner);
        commandBuilder.set("#OwnerOnlyHint.Visible", !owner);
        commandBuilder.set("#TownInfoSeparator.Visible", true);
        commandBuilder.set("#NameInput.Value", tr.getDisplayName());
        commandBuilder.set(
            "#TownInfo.TextSpans",
            Message.translation("server.aetherhaven.chartertown.townInfoBlock")
                .param("name", tr.getDisplayName())
                .param("id", townId.toString())
                .param("tier", String.valueOf(tr.getTier()))
                .param("radius", String.valueOf(tr.getTerritoryChunkRadius()))
                .param("cx", String.valueOf(tr.getCharterX()))
                .param("cy", String.valueOf(tr.getCharterY()))
                .param("cz", String.valueOf(tr.getCharterZ()))
        );
        boolean relocateFlow = owner && charterRelocateConfirmOpen;
        boolean dissolveFlow = owner && dissolveConfirmOpen;
        boolean modalOpen = dissolveFlow || relocateFlow;
        commandBuilder.set("#CharterConfirmModal.Visible", modalOpen);
        commandBuilder.set("#CharterTopBar.Visible", owner && !modalOpen);
        commandBuilder.set("#DissolveButton.Visible", owner && !modalOpen);
        commandBuilder.set("#DissolveHint.Visible", owner && !modalOpen);
        commandBuilder.set("#MoveCharterButton.Visible", owner && !modalOpen);
        commandBuilder.set("#MoveCharterHint.Visible", owner && !modalOpen);
        if (dissolveFlow) {
            commandBuilder.set("#CharterModalTitle.TextSpans", Message.translation("server.aetherhaven.ui.chartertown.modalDissolveTitle"));
            commandBuilder.set("#CharterModalText.TextSpans", Message.translation("server.aetherhaven.ui.chartertown.dissolveConfirmText"));
            commandBuilder.set("#CharterModalConfirmButton.TextSpans", Message.translation("server.aetherhaven.ui.chartertown.dissolveConfirm"));
        } else if (relocateFlow) {
            commandBuilder.set("#CharterModalTitle.TextSpans", Message.translation("server.aetherhaven.ui.chartertown.modalMoveTitle"));
            commandBuilder.set("#CharterModalText.TextSpans", Message.translation("server.aetherhaven.ui.chartertown.moveCharterConfirmText"));
            commandBuilder.set("#CharterModalConfirmButton.TextSpans", Message.translation("server.aetherhaven.ui.chartertown.moveCharterConfirm"));
        }
        if (owner) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveTownNameButton",
                new EventData().append("Action", "SaveTownName").append("@TownName", "#NameInput.Value"),
                false
            );
            if (modalOpen) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CharterModalConfirmButton",
                    new EventData().append("Action", "CharterModalConfirm"),
                    false
                );
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CharterModalCancelButton",
                    new EventData().append("Action", "CharterModalCancel"),
                    false
                );
            } else {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#DissolveButton",
                    new EventData().append("Action", "BeginDissolveConfirm"),
                    false
                );
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#MoveCharterButton",
                    new EventData().append("Action", "BeginMoveCharter"),
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
        if (data.action.equalsIgnoreCase("BeginDissolveConfirm")) {
            if (!tryResolveOwnerTown(ref, store)) {
                return;
            }
            dissolveConfirmOpen = true;
            charterRelocateConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action.equalsIgnoreCase("BeginMoveCharter")) {
            if (!tryResolveOwnerTown(ref, store)) {
                return;
            }
            charterRelocateConfirmOpen = true;
            dissolveConfirmOpen = false;
            UICommandBuilder cmdMove = new UICommandBuilder();
            UIEventBuilder evMove = new UIEventBuilder();
            build(ref, cmdMove, evMove, store);
            sendUpdate(cmdMove, evMove, false);
            return;
        }
        if (data.action.equalsIgnoreCase("CharterModalCancel")) {
            dissolveConfirmOpen = false;
            charterRelocateConfirmOpen = false;
            UICommandBuilder cmdMc = new UICommandBuilder();
            UIEventBuilder evMc = new UIEventBuilder();
            build(ref, cmdMc, evMc, store);
            sendUpdate(cmdMc, evMc, false);
            return;
        }
        if (data.action.equalsIgnoreCase("CharterModalConfirm")) {
            if (dissolveConfirmOpen) {
                UUIDComponent ucDissolve = store.getComponent(ref, UUIDComponent.getComponentType());
                if (ucDissolve == null) {
                    return;
                }
                AetherhavenPlugin pluginDissolve = AetherhavenPlugin.get();
                World worldDissolve = store.getExternalData().getWorld();
                if (pluginDissolve == null) {
                    return;
                }
                Store<ChunkStore> csDissolve = charterBlockRef.getStore();
                CharterBlock chDissolve = csDissolve.getComponent(charterBlockRef, CharterBlock.getComponentType());
                if (chDissolve == null || chDissolve.getTownId().isBlank()) {
                    return;
                }
                UUID townIdDissolve;
                try {
                    townIdDissolve = UUID.fromString(chDissolve.getTownId().trim());
                } catch (IllegalArgumentException e) {
                    return;
                }
                TownManager tmDissolve = AetherhavenWorldRegistries.getOrCreateTownManager(worldDissolve, pluginDissolve);
                TownRecord trDissolve = tmDissolve.getTown(townIdDissolve);
                if (trDissolve == null || !trDissolve.getOwnerUuid().equals(ucDissolve.getUuid())) {
                    return;
                }
                dissolveConfirmOpen = false;
                worldDissolve.execute(
                    () -> {
                        TownDissolutionService.dissolveTown(worldDissolve, pluginDissolve, trDissolve, store);
                        PlayerRef prDone = store.getComponent(ref, PlayerRef.getComponentType());
                        if (prDone != null) {
                            prDone.sendMessage(Message.translation("server.aetherhaven.ui.chartertown.dissolved"));
                        }
                        close();
                    }
                );
                return;
            }
            if (charterRelocateConfirmOpen) {
                UUIDComponent ucMc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (ucMc == null || !tryResolveOwnerTown(ref, store)) {
                    return;
                }
                AetherhavenPlugin pluginMc = AetherhavenPlugin.get();
                World worldMc = store.getExternalData().getWorld();
                if (pluginMc == null) {
                    return;
                }
                Store<ChunkStore> csMc = charterBlockRef.getStore();
                CharterBlock chMc = csMc.getComponent(charterBlockRef, CharterBlock.getComponentType());
                if (chMc == null || chMc.getTownId().isBlank()) {
                    return;
                }
                UUID townIdMc;
                try {
                    townIdMc = UUID.fromString(chMc.getTownId().trim());
                } catch (IllegalArgumentException e) {
                    return;
                }
                TownManager tmMc = AetherhavenWorldRegistries.getOrCreateTownManager(worldMc, pluginMc);
                TownRecord trMc = tmMc.getTown(townIdMc);
                if (trMc == null || !trMc.getOwnerUuid().equals(ucMc.getUuid())) {
                    return;
                }
                charterRelocateConfirmOpen = false;
                Player playerMc = store.getComponent(ref, Player.getComponentType());
                if (playerMc == null) {
                    return;
                }
                PlotPlacementOpenHelper.cancelActivePlotPlacement(ref, store, playerRef);
                CharterRelocationSession staleCharter = CharterRelocationSessions.get(ucMc.getUuid());
                if (staleCharter != null) {
                    PlotPreviewSpawner.clear(store, staleCharter.getPreviewEntityRefs());
                }
                CharterRelocationSessions.remove(ucMc.getUuid());
                CharterRelocationSession sessionMc =
                    new CharterRelocationSession(
                        worldMc,
                        new Vector3i(trMc.getCharterX(), trMc.getCharterY(), trMc.getCharterZ()),
                        trMc.getTownId()
                    );
                CharterRelocationSessions.put(ucMc.getUuid(), sessionMc);
                playerMc.getPageManager().openCustomPage(ref, store, new CharterRelocationPage(playerRef, sessionMc));
            }
            return;
        }
        if (!data.action.equalsIgnoreCase("SaveTownName")) {
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
        Store<ChunkStore> cs = charterBlockRef.getStore();
        CharterBlock ch = cs.getComponent(charterBlockRef, CharterBlock.getComponentType());
        if (ch == null || ch.getTownId().isBlank()) {
            return;
        }
        UUID townId;
        try {
            townId = UUID.fromString(ch.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord tr = tm.getTown(townId);
        if (tr == null || !tr.getOwnerUuid().equals(uc.getUuid())) {
            return;
        }
        String newName = data.townName != null ? data.townName.trim() : "";
        if (newName.isEmpty()) {
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(Message.translation("server.aetherhaven.chartertown.nameEmpty"));
            }
            return;
        }
        if (!tm.trySetDisplayName(tr, newName)) {
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(Message.translation("server.aetherhaven.chartertown.nameDuplicate"));
            }
            return;
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(
                Message.translation("server.aetherhaven.chartertown.nameSaved").param("name", tr.getDisplayName())
            );
        }
        dissolveConfirmOpen = false;
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    /** True if the opening player owns the town linked to this charter block. */
    private boolean tryResolveOwnerTown(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            return false;
        }
        Store<ChunkStore> cs = charterBlockRef.getStore();
        CharterBlock ch = cs.getComponent(charterBlockRef, CharterBlock.getComponentType());
        if (ch == null || ch.getTownId().isBlank()) {
            return false;
        }
        UUID tid;
        try {
            tid = UUID.fromString(ch.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord tr = tm.getTown(tid);
        return tr != null && tr.getOwnerUuid().equals(uc.getUuid());
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@TownName", Codec.STRING), (d, v) -> d.townName = v, d -> d.townName)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String townName;
    }
}
