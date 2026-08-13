package com.hexvane.aetherhaven.festival.firework;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Use on a block: launch a rising rocket. Consume is a sibling vanilla ModifyInventory. */
public final class FireworkLaunchInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<FireworkLaunchInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(FireworkLaunchInteraction.class, FireworkLaunchInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Launch a firework rocket from the targeted ground block.")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.FESTIVALS)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> playerRef = context.getEntity();
        ItemStack itemInHand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (itemInHand == null || itemInHand.isEmpty() || !FireworkIds.ITEM_ID.equals(itemInHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Vector3i targetBlock = resolveTargetBlock(commandBuffer, context, playerRef);
        if (targetBlock == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> spawned = FireworkSpawnService.spawnAtBlock(commandBuffer, targetBlock);
        if (spawned == null || !spawned.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
    }

    @Nullable
    private static Vector3i resolveTargetBlock(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        BlockPosition fromContext = context.getTargetBlock();
        if (fromContext != null) {
            return new Vector3i(fromContext.x, fromContext.y, fromContext.z);
        }
        InteractionSyncData clientState = context.getClientState();
        if (clientState != null && clientState.blockPosition != null) {
            BlockPosition fromClient = clientState.blockPosition;
            return new Vector3i(fromClient.x, fromClient.y, fromClient.z);
        }
        Vector3i looked = TargetUtil.getTargetBlock(playerRef, InteractionManager.MAX_REACH_DISTANCE, commandBuffer);
        if (looked != null) {
            return looked;
        }
        TransformComponent transform = commandBuffer.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }
        Vector3d pos = transform.getPosition();
        return new Vector3i(floor(pos.x), floor(pos.y) - 1, floor(pos.z));
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    @Override
    protected void simulateFirstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {}
}
