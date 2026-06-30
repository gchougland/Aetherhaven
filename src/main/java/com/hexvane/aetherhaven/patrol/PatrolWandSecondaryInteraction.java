package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PatrolWandSecondaryInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PatrolWandSecondaryInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(
                PatrolWandSecondaryInteraction.class,
                PatrolWandSecondaryInteraction::new,
                SimpleBlockInteraction.CODEC
            )
            .documentation("Patrol wand: add or remove patrol point.")
            .build();

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull Vector3i targetBlock,
        @Nonnull com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler cooldownHandler
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.PATROL_ROUTES)) {
            return;
        }
        if (!PatrolWandInteractions.isPatrolWandItem(itemInHand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PatrolWandInteractions.handleSecondary(
            context.getEntity(),
            commandBuffer,
            world,
            targetBlock,
            context,
            commandBuffer.getStore()
        );
    }

    @Override
    protected void simulateInteractWithBlock(
        @Nonnull InteractionType interactionType,
        @Nonnull InteractionContext interactionContext,
        @Nullable ItemStack itemStack,
        @Nonnull World world,
        @Nonnull Vector3i vector3i
    ) {}
}
