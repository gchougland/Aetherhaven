package com.hexvane.aetherhaven.festival.firework;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
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

/** Secondary use on a block: consume firework and launch a rising rocket entity. */
public final class FireworkLaunchInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<FireworkLaunchInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(FireworkLaunchInteraction.class, FireworkLaunchInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Launch a firework rocket from the targeted ground block.")
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
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.FESTIVALS)) {
            return;
        }
        if (itemInHand == null || itemInHand.isEmpty() || !FireworkIds.ITEM_ID.equals(itemInHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> spawned = FireworkSpawnService.spawnAtBlock(commandBuffer, targetBlock);
        if (spawned == null || !spawned.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        context.getState().state = InteractionState.Finished;
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
