package com.hexvane.aetherhaven.poi.tool;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Places or clears the per-POI autonomy interaction target (leash goal) for the selected POI. */
public final class PoiToolSetTargetInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PoiToolSetTargetInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec.builder(
                PoiToolSetTargetInteraction.class,
                PoiToolSetTargetInteraction::new,
                SimpleBlockInteraction.CODEC
            )
            .documentation("Set/clear interaction target for selected POI (autonomy path goal).")
            .build();

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull Vector3i targetBlock,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (!PoiToolInteractions.isPoiToolItem(itemInHand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PoiToolInteractions.handleSetInteractionTarget(context.getEntity(), commandBuffer, world, targetBlock, context);
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
