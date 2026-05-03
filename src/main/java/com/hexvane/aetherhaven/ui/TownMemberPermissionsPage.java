package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberPermissions;
import com.hexvane.aetherhaven.town.TownMembershipActions;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
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

public final class TownMemberPermissionsPage extends InteractiveCustomUIPage<TownMemberPermissionsPage.PageData> {
    private static final String FLAG_PLACE_PLOTS = "placePlots";
    private static final String FLAG_MANAGE_CONSTRUCTIONS = "manageConstructions";
    private static final String FLAG_SPEND_TREASURY = "spendTreasuryGold";
    private static final String FLAG_OPEN_TREASURY = "openTreasuryPanel";
    private static final String FLAG_ACCEPT_QUESTS = "acceptQuests";
    private static final String FLAG_COMPLETE_QUESTS = "completeQuests";
    private static final String FLAG_ABANDON_QUESTS = "abandonQuests";
    private static final String FLAG_REVIVE = "reviveVillagers";

    private final Ref<ChunkStore> managementBlockRef;
    @Nonnull
    private final Vector3i managementBlockPos;
    @Nonnull
    private final UUID townUuid;
    @Nonnull
    private final UUID targetPlayerUuid;
    private boolean templateAppended;

    public TownMemberPermissionsPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<ChunkStore> managementBlockRef,
        @Nonnull Vector3i managementBlockPos,
        @Nonnull UUID townUuid,
        @Nonnull UUID targetPlayerUuid
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.managementBlockRef = managementBlockRef;
        this.managementBlockPos = managementBlockPos.clone();
        this.townUuid = townUuid;
        this.targetPlayerUuid = targetPlayerUuid;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/TownMemberPermissionsPage.ui");
            templateAppended = true;
        }
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#Content #PermBack",
            new EventData().append("Action", "BackToPlayers"),
            false
        );
        bindToggle(eventBuilder, "#Content #PermScroll #PermInner #PermPlacePlots", FLAG_PLACE_PLOTS);
        bindToggle(eventBuilder, "#Content #PermScroll #PermInner #PermManageConstructions", FLAG_MANAGE_CONSTRUCTIONS);
        bindToggle(eventBuilder, "#Content #PermScroll #PermInner #PermSpendTreasuryGold", FLAG_SPEND_TREASURY);
        bindToggle(eventBuilder, "#Content #PermScroll #PermInner #PermOpenTreasuryPanel", FLAG_OPEN_TREASURY);
        bindToggle(eventBuilder, "#Content #PermScroll #PermInner #PermAcceptQuests", FLAG_ACCEPT_QUESTS);
        bindToggle(eventBuilder, "#Content #PermScroll #PermInner #PermCompleteQuests", FLAG_COMPLETE_QUESTS);
        bindToggle(eventBuilder, "#Content #PermScroll #PermInner #PermAbandonQuests", FLAG_ABANDON_QUESTS);
        bindToggle(eventBuilder, "#Content #PermScroll #PermInner #PermReviveVillagers", FLAG_REVIVE);

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || uc == null) {
            commandBuilder.set("#Content #Hint.Visible", true);
            commandBuilder.set("#Content #Hint.TextSpans", Message.translation("server.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
            commandBuilder.set("#Content #Hint.Visible", true);
            commandBuilder.set(
                "#Content #Hint.TextSpans",
                Message.translation("server.aetherhaven.ui.memberPermissions.err.notOwner")
            );
            return;
        }
        if (!targetPlayerUuid.equals(town.getOwnerUuid()) && !town.isMemberPlayer(targetPlayerUuid)) {
            commandBuilder.set("#Content #Hint.Visible", true);
            commandBuilder.set(
                "#Content #Hint.TextSpans",
                Message.translation("server.aetherhaven.ui.memberPermissions.err.notInTown")
            );
            return;
        }
        commandBuilder.set("#Content #Hint.Visible", false);
        String display = TownPlayerLookup.displayNameForUuid(world, targetPlayerUuid);
        commandBuilder.set("#Content #TargetName.TextSpans", Message.raw(display));

        TownMemberPermissions p = town.getEffectiveMemberPermissions(targetPlayerUuid);
        setCheck(commandBuilder, "#Content #PermScroll #PermInner #PermPlacePlots", p.placePlots());
        setCheck(commandBuilder, "#Content #PermScroll #PermInner #PermManageConstructions", p.manageConstructions());
        setCheck(commandBuilder, "#Content #PermScroll #PermInner #PermSpendTreasuryGold", p.spendTreasuryGold());
        setCheck(commandBuilder, "#Content #PermScroll #PermInner #PermOpenTreasuryPanel", p.openTreasuryPanel());
        setCheck(commandBuilder, "#Content #PermScroll #PermInner #PermAcceptQuests", p.acceptQuests());
        setCheck(commandBuilder, "#Content #PermScroll #PermInner #PermCompleteQuests", p.completeQuests());
        setCheck(commandBuilder, "#Content #PermScroll #PermInner #PermAbandonQuests", p.abandonQuests());
        setCheck(commandBuilder, "#Content #PermScroll #PermInner #PermReviveVillagers", p.reviveVillagers());
    }

    private static void setCheck(@Nonnull UICommandBuilder commandBuilder, @Nonnull String path, boolean on) {
        commandBuilder.set(path + " #CheckBox.Value", on);
    }

    private static void bindToggle(@Nonnull UIEventBuilder eventBuilder, @Nonnull String checkPath, @Nonnull String flag) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            checkPath + " #CheckBox",
            new EventData().append("Action", "TogglePerm").append("@Flag", flag).append("@Checked", checkPath + " #CheckBox.Value"),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        if (data.action.equalsIgnoreCase("BackToPlayers")) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager()
                    .openCustomPage(
                        ref,
                        store,
                        new PlotConstructionPage(playerRef, managementBlockRef, managementBlockPos, true, 1, false)
                    );
            }
            return;
        }
        if (!data.action.equalsIgnoreCase("TogglePerm") || data.flag == null || data.checked == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || uc == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null) {
            return;
        }
        TownMemberPermissions next = town.getEffectiveMemberPermissions(targetPlayerUuid).copy();
        applyFlag(next, data.flag.trim(), parseBool(data.checked));
        Message err = TownMembershipActions.tryPutMemberPermissions(tm, town, playerRef, uc.getUuid(), targetPlayerUuid, next);
        if (err != null) {
            playerRef.sendMessage(err);
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    private static boolean parseBool(@Nonnull String raw) {
        String s = raw.trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) {
            return false;
        }
        return Boolean.parseBoolean(s);
    }

    private static void applyFlag(@Nonnull TownMemberPermissions p, @Nonnull String flag, boolean on) {
        switch (flag) {
            case FLAG_PLACE_PLOTS -> p.setPlacePlots(on);
            case FLAG_MANAGE_CONSTRUCTIONS -> p.setManageConstructions(on);
            case FLAG_SPEND_TREASURY -> p.setSpendTreasuryGold(on);
            case FLAG_OPEN_TREASURY -> p.setOpenTreasuryPanel(on);
            case FLAG_ACCEPT_QUESTS -> p.setAcceptQuests(on);
            case FLAG_COMPLETE_QUESTS -> p.setCompleteQuests(on);
            case FLAG_ABANDON_QUESTS -> p.setAbandonQuests(on);
            case FLAG_REVIVE -> p.setReviveVillagers(on);
            default -> {
            }
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@Flag", Codec.STRING), (d, v) -> d.flag = v, d -> d.flag)
            .add()
            .append(new KeyedCodec<>("@Checked", Codec.STRING), (d, v) -> d.checked = v, d -> d.checked)
            .add()
            .build();

        private String action;
        @Nullable
        private String flag;
        @Nullable
        private String checked;
    }
}
