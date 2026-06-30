package com.hexvane.aetherhaven.poi.tool;



import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;

import com.hypixel.hytale.component.Ref;

import com.hypixel.hytale.protocol.InteractionState;

import com.hypixel.hytale.protocol.InteractionType;

import com.hypixel.hytale.protocol.WaitForDataFrom;

import com.hypixel.hytale.server.core.entity.InteractionContext;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;

import org.joml.Vector3i;



/**

 * Secondary click: POI edit (move), POI placement (GUI), POI remove, or adventurer spawn marker place/remove.

 */

public final class PoiToolSecondaryInteraction extends SimpleInstantInteraction {

    @Nonnull

    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PoiToolSecondaryInteraction> CODEC =

        com.hypixel.hytale.codec.builder.BuilderCodec.builder(

                PoiToolSecondaryInteraction.class,

                PoiToolSecondaryInteraction::new,

                SimpleInstantInteraction.CODEC

            )

            .documentation("POI tool secondary: edit, place, or adventurer spawn markers.")

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

    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {

        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.ADMIN_TOOLS)) {

            return;

        }

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        if (commandBuffer == null) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        if (type != InteractionType.Secondary) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        @Nullable

        Ref<EntityStore> playerRef = context.getEntity();

        if (playerRef == null) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        @Nullable

        ItemStack itemInHand = com.hypixel.hytale.server.core.inventory.InventoryComponent.getItemInHand(commandBuffer, playerRef);

        if (!PoiToolInteractions.isPoiToolItem(itemInHand)) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        PoiToolInteractions.ensureState(playerRef, commandBuffer);

        PoiToolPlayerComponent state = commandBuffer.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());

        if (state == null) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        World world = commandBuffer.getStore().getExternalData().getWorld();

        if (state.getMode() == PoiToolMode.AdventurerSpawnMarker) {

            PoiToolSpawnMarkerActions.handleSecondary(playerRef, commandBuffer, world, context);

            return;

        }

        if (state.getMode() == PoiToolMode.PoiPlacement) {
            PoiToolPlacementActions.handleSecondary(playerRef, commandBuffer, world, context);
            return;
        }

        if (state.getMode() == PoiToolMode.PoiRemove) {
            PoiToolRemoveActions.handleSecondary(playerRef, commandBuffer, world, context);
            return;
        }

        if (state.getMode() != PoiToolMode.PoiEdit) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        Vector3i targetBlock = PoiToolSpawnMarkerActions.resolveTargetBlock(playerRef, commandBuffer.getStore(), context);
        if (targetBlock == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PoiToolInteractions.handleMove(playerRef, commandBuffer, world, targetBlock, context);
    }



    @Override

    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        if (commandBuffer == null || type != InteractionType.Secondary) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        @Nullable

        Ref<EntityStore> playerRef = context.getEntity();

        @Nullable

        ItemStack itemInHand = playerRef == null ? null : com.hypixel.hytale.server.core.inventory.InventoryComponent.getItemInHand(commandBuffer, playerRef);

        if (!PoiToolInteractions.isPoiToolItem(itemInHand)) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        context.getState().state = InteractionState.Finished;

    }

}

