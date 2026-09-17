package com.hexvane.aetherhaven.battlehorn;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Calls guards, or dismisses the player's followers when crouching, at the start of horn use. */
public final class BattleHornSummonInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<BattleHornSummonInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(
                BattleHornSummonInteraction.class,
                BattleHornSummonInteraction::new,
                SimpleInstantInteraction.CODEC
            )
            .documentation("Summon town guards, or crouch to dismiss all of the player's followers.")
            .build();

    @Override
    protected void firstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.REPUTATION_UNLOCKS)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            return;
        }
        @Nullable Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            return;
        }
        @Nullable ItemStack itemInHand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (itemInHand == null
            || itemInHand.isEmpty()
            || !AetherhavenConstants.ITEM_BATTLE_HORN.equals(itemInHand.getItemId())) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        var movement = commandBuffer.getComponent(playerRef, MovementStatesComponent.getComponentType());
        var states = movement != null ? movement.getMovementStates() : null;
        if (states != null && (states.crouching || states.forcedCrouching)) {
            BattleHornService.dismissFollowers(playerRef, commandBuffer);
        } else {
            BattleHornService.callGuards(playerRef, commandBuffer, plugin);
        }
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }
}
