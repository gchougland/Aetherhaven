package com.hexvane.aetherhaven.festival.market;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Player Use (F) on the shared town stall to set out goods. */
public final class MarketStallUseInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<MarketStallUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(MarketStallUseInteraction.class, MarketStallUseInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Open the shared Market Festival stall with Use (F).")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    protected void firstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (type != InteractionType.Use) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> targetRef = resolveTarget(context, commandBuffer);
        Ref<EntityStore> playerRef = context.getEntity();
        if (targetRef == null || !targetRef.isValid() || playerRef == null || !playerRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        MarketStallComponent pad =
            commandBuffer.getComponent(targetRef, MarketStallComponent.getComponentType());
        if (pad == null) {
            pad = commandBuffer.getStore().getComponent(targetRef, MarketStallComponent.getComponentType());
        }
        UUID townId = pad != null ? pad.getTownId() : null;
        if (townId == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUID playerUuid = uuid(playerRef, commandBuffer);
        Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            player = commandBuffer.getStore().getComponent(playerRef, Player.getComponentType());
        }
        PlayerRef playerRefComp = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComp == null) {
            playerRefComp = commandBuffer.getStore().getComponent(playerRef, PlayerRef.getComponentType());
        }
        if (playerUuid == null || player == null || playerRefComp == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = commandBuffer.getExternalData().getWorld();
        if (plugin == null || world == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !MarketIds.FESTIVAL_ID.equals(town.getActiveFestivalId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!town.hasMemberOrOwner(playerUuid)) {
            playerRefComp.sendMessage(
                Message.translation("aetherhaven_festivals.aetherhaven.festival.market.stall.notMember")
            );
            context.getState().state = InteractionState.Failed;
            return;
        }
        MarketSession session = MarketSessionIndex.getOrCreate(townId);
        if (session.isStallLocked()) {
            playerRefComp.sendMessage(
                Message.translation("aetherhaven_festivals.aetherhaven.festival.market.stall.locked")
            );
            context.getState().state = InteractionState.Failed;
            return;
        }
        boolean opened = MarketStallService.openStall(player, playerRef, commandBuffer.getStore(), session, townId);
        context.getState().state = opened ? InteractionState.Finished : InteractionState.Failed;
    }

    @Nullable
    private static Ref<EntityStore> resolveTarget(
        @Nonnull InteractionContext context,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> target = context.getTargetEntity();
        if (target != null && target.isValid()) {
            return target;
        }
        var clientState = context.getClientState();
        if (clientState == null) {
            return null;
        }
        return commandBuffer.getStore().getExternalData().getRefFromNetworkId(clientState.entityId);
    }

    @Nullable
    private static UUID uuid(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUIDComponent uc = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            uc = commandBuffer.getStore().getComponent(ref, UUIDComponent.getComponentType());
        }
        return uc != null ? uc.getUuid() : null;
    }
}
