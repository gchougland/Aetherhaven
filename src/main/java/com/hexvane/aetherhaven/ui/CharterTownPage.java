package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
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
            commandBuilder.set("#TownInfo.TextSpans", Message.raw("Charter is not linked to a town yet."));
            commandBuilder.set("#TownNameEditor.Visible", false);
            commandBuilder.set("#OwnerOnlyHint.Visible", false);
            commandBuilder.set("#TownInfoSeparator.Visible", false);
            return;
        }
        UUID townId;
        try {
            townId = UUID.fromString(townIdStr);
        } catch (IllegalArgumentException e) {
            commandBuilder.set("#TownInfo.TextSpans", Message.raw("Invalid town id on charter."));
            commandBuilder.set("#TownNameEditor.Visible", false);
            commandBuilder.set("#OwnerOnlyHint.Visible", false);
            commandBuilder.set("#TownInfoSeparator.Visible", false);
            return;
        }
        World world = store.getExternalData().getWorld();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            commandBuilder.set("#TownInfo.TextSpans", Message.raw("Aetherhaven not loaded."));
            commandBuilder.set("#TownNameEditor.Visible", false);
            commandBuilder.set("#OwnerOnlyHint.Visible", false);
            commandBuilder.set("#TownInfoSeparator.Visible", false);
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord tr = tm.getTown(townId);
        if (tr == null) {
            commandBuilder.set("#TownInfo.TextSpans", Message.raw("Town data not found (see server log)."));
            commandBuilder.set("#TownNameEditor.Visible", false);
            commandBuilder.set("#OwnerOnlyHint.Visible", false);
            commandBuilder.set("#TownInfoSeparator.Visible", false);
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        boolean owner = uc != null && tr.getOwnerUuid().equals(uc.getUuid());
        commandBuilder.set("#TownNameEditor.Visible", owner);
        commandBuilder.set("#OwnerOnlyHint.Visible", !owner);
        commandBuilder.set("#TownInfoSeparator.Visible", true);
        commandBuilder.set("#NameInput.Value", tr.getDisplayName());
        commandBuilder.set(
            "#TownInfo.TextSpans",
            Message.raw(
                "Town name: "
                    + tr.getDisplayName()
                    + "\nTown ID: "
                    + townId
                    + "\nTier: "
                    + tr.getTier()
                    + "\nTerritory (chunk radius): "
                    + tr.getTerritoryChunkRadius()
                    + "\nCharter at "
                    + tr.getCharterX()
                    + ", "
                    + tr.getCharterY()
                    + ", "
                    + tr.getCharterZ()
            )
        );
        if (owner) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveTownNameButton",
                new EventData().append("Action", "SaveTownName").append("@TownName", "#NameInput.Value"),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null || !data.action.equalsIgnoreCase("SaveTownName")) {
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
                pr.sendMessage(Message.raw("Town name cannot be empty."));
            }
            return;
        }
        if (!tm.trySetDisplayName(tr, newName)) {
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(Message.raw("That name is already used by another town in this world."));
            }
            return;
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw("Town name saved: " + tr.getDisplayName()));
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
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
