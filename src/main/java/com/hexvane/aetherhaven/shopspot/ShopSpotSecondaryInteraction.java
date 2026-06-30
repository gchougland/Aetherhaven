package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
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
import org.joml.Vector3i;

/** Secondary: list or remove your player listing when holding an item. */
public final class ShopSpotSecondaryInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<ShopSpotSecondaryInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(ShopSpotSecondaryInteraction.class, ShopSpotSecondaryInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Shop spot: list or remove a player listing (item in hand).")
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
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.COMMERCE)) {
            return;
        }
        if (type != InteractionType.Secondary) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null || itemInHand == null || itemInHand.isEmpty()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ShopSpotPurchaseService.handleListOrRemove(playerRef, commandBuffer, context, targetBlock, true);
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
