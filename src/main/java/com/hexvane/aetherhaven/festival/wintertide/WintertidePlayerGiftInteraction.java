package com.hexvane.aetherhaven.festival.wintertide;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation.canPlayerInteractWithEntity;

/** Player Use (F) on another town member to give them a Wintertide gift. */
public final class WintertidePlayerGiftInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<WintertidePlayerGiftInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(WintertidePlayerGiftInteraction.class, WintertidePlayerGiftInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Give a Wintertide gift to another town member with Use (F).")
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
        Ref<EntityStore> giverRef = context.getEntity();
        Ref<EntityStore> receiverRef = resolveTarget(context, commandBuffer);
        if (giverRef == null || !giverRef.isValid() || receiverRef == null || !receiverRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!canPlayerInteractWithEntity(giverRef, commandBuffer, context.getHeldItem(), receiverRef)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (commandBuffer.getComponent(receiverRef, Player.getComponentType()) == null
            && commandBuffer.getStore().getComponent(receiverRef, Player.getComponentType()) == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = commandBuffer.getExternalData().getWorld();
        UUID giverUuid = uuid(giverRef, commandBuffer);
        if (plugin == null || world == null || giverUuid == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(giverUuid);
        if (!WintertideGiftService.isWintertideActive(town)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        WintertideGiftService.beginPlayerGift(giverRef, receiverRef, commandBuffer.getStore());
        context.getState().state = InteractionState.Finished;
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
