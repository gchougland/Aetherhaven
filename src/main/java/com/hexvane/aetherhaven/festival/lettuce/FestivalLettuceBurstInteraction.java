package com.hexvane.aetherhaven.festival.lettuce;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Player Use (F) on a full Springheart Lettuce to pop it into seeds and festival tickets. */
public final class FestivalLettuceBurstInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<FestivalLettuceBurstInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(FestivalLettuceBurstInteraction.class, FestivalLettuceBurstInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Pop a full festival lettuce with Use (F).")
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
        if (targetRef == null || !targetRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        FestivalLettuceComponent lettuce =
            commandBuffer.getComponent(targetRef, FestivalLettuceComponent.getComponentType());
        TransformComponent tc = commandBuffer.getComponent(targetRef, TransformComponent.getComponentType());
        if (lettuce == null) {
            lettuce = commandBuffer.getStore().getComponent(targetRef, FestivalLettuceComponent.getComponentType());
        }
        if (tc == null) {
            tc = commandBuffer.getStore().getComponent(targetRef, TransformComponent.getComponentType());
        }
        if (lettuce == null || tc == null || !lettuce.isReadyToBurst()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        boolean started =
            FestivalLettuceBurstSystem.tryBeginBurst(
                commandBuffer.getStore(),
                lettuce,
                new Vector3d(tc.getPosition())
            );
        if (!started) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        commandBuffer.putComponent(targetRef, FestivalLettuceComponent.getComponentType(), lettuce);
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
