package com.hexvane.aetherhaven.festival.hallowseve;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Player Use (F) on a ready jack o lantern after a maze run. */
public final class HallowsEvePumpkinBurstInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<HallowsEvePumpkinBurstInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(
                HallowsEvePumpkinBurstInteraction.class,
                HallowsEvePumpkinBurstInteraction::new,
                SimpleInstantInteraction.CODEC
            )
            .documentation("Pop a ready Hallow's Eve jack o lantern with Use (F).")
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
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null || commandBuffer.getComponent(playerRef, Player.getComponentType()) == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUIDComponent uc = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        UUID playerUuid = uc != null ? uc.getUuid() : null;
        Ref<EntityStore> targetRef = resolveTarget(context, commandBuffer);
        if (targetRef == null || !targetRef.isValid() || playerUuid == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        HallowsEvePumpkinComponent pumpkin =
            commandBuffer.getComponent(targetRef, HallowsEvePumpkinComponent.getComponentType());
        TransformComponent tc = commandBuffer.getComponent(targetRef, TransformComponent.getComponentType());
        if (pumpkin == null) {
            pumpkin = commandBuffer.getStore().getComponent(targetRef, HallowsEvePumpkinComponent.getComponentType());
        }
        if (tc == null) {
            tc = commandBuffer.getStore().getComponent(targetRef, TransformComponent.getComponentType());
        }
        if (pumpkin == null || tc == null || !pumpkin.isReady()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUID townId = pumpkin.getTownId();
        HallowsEveSession session = townId != null ? HallowsEveSessionIndex.get(townId) : null;
        if (session == null || !session.isReadyToBurst()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        boolean started =
            HallowsEvePumpkinBurstSystem.tryBeginBurst(
                commandBuffer.getStore(),
                pumpkin,
                new Vector3d(tc.getPosition())
            );
        if (!started) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        commandBuffer.putComponent(targetRef, HallowsEvePumpkinComponent.getComponentType(), pumpkin);
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
}
