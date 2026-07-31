package com.hexvane.aetherhaven.poi.tool;



import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerEntity;

import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerSpawner;

import com.hexvane.aetherhaven.marker.MarkerEntityProximity;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.marker.MarkerFacingYaw;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import com.hypixel.hytale.component.AddReason;

import com.hypixel.hytale.component.CommandBuffer;

import com.hypixel.hytale.component.Holder;

import com.hypixel.hytale.component.Ref;

import com.hypixel.hytale.component.RemoveReason;

import com.hypixel.hytale.component.Store;

import com.hypixel.hytale.protocol.BlockPosition;

import com.hypixel.hytale.protocol.InteractionState;

import com.hypixel.hytale.protocol.InteractionSyncData;

import com.hypixel.hytale.server.core.Message;

import com.hypixel.hytale.server.core.entity.InteractionContext;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;

import org.joml.Vector3d;

import org.joml.Vector3i;



/** Places and removes adventurer spawn marker entities while the POI tool is in spawn marker mode. */

public final class PoiToolSpawnMarkerActions {

    private PoiToolSpawnMarkerActions() {}



    public static void handleSecondary(

        @Nonnull Ref<EntityStore> playerRef,

        @Nonnull CommandBuffer<EntityStore> commandBuffer,

        @Nonnull World world,

        @Nonnull InteractionContext context

    ) {

        if (!PoiToolInteractions.hasPoiToolPermission(playerRef, commandBuffer)) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        PoiToolInteractions.ensureState(playerRef, commandBuffer);

        PoiToolPlayerComponent state = commandBuffer.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());

        if (state == null || state.getMode() != PoiToolMode.AdventurerSpawnMarker) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        Store<EntityStore> store = commandBuffer.getStore();

        @Nullable

        Vector3i targetBlock = resolveTargetBlock(playerRef, store, context);

        @Nullable

        Ref<EntityStore> markerRef =

            MarkerEntityProximity.resolveTarget(

                store,

                context,

                AdventurerSpawnMarkerEntity.getComponentType(),

                targetBlock,

                2.0

            );

        if (markerRef != null && markerRef.isValid()) {

            commandBuffer.removeEntity(markerRef, RemoveReason.REMOVE);

            PoiToolInteractions.send(playerRef, commandBuffer, Message.translation("aetherhaven_world_debug.aetherhaven.adventurerSpawnMarker.removed"));

            context.getState().state = InteractionState.Finished;

            return;

        }

        if (targetBlock == null) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        Vector3i clicked = new Vector3i(targetBlock.x(), targetBlock.y(), targetBlock.z());
        Vector3i standBlock = VillagerBlockUtil.resolveStandBlockFromClick(world, clicked);
        Vector3d spawnPos = new Vector3d(standBlock.x + 0.5, standBlock.y, standBlock.z + 0.5);

        if (MarkerEntityProximity.isDuplicatePosition(store, AdventurerSpawnMarkerEntity.getComponentType(), spawnPos)) {

            PoiToolInteractions.send(playerRef, commandBuffer, Message.translation("aetherhaven_world_debug.aetherhaven.adventurerSpawnMarker.alreadyHere"));

            context.getState().state = InteractionState.Finished;

            return;

        }

        float yaw = 0f;
        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3i seatBlock = VillagerBlockUtil.findGuildHallSeatBelowSpawn(world, spawnPos);
        if (seatBlock != null) {
            Float seatYaw = VillagerBlockUtil.seatForwardYawRadians(world, seatBlock);
            if (seatYaw != null) {
                yaw = seatYaw;
            } else if (playerTc != null) {
                yaw = MarkerFacingYaw.yawFacingToward(spawnPos, playerTc.getPosition());
            }
        } else if (playerTc != null) {
            yaw = MarkerFacingYaw.yawFacingToward(spawnPos, playerTc.getPosition());
        }

        Holder<EntityStore> holder = AdventurerSpawnMarkerSpawner.createHolder(world, spawnPos, yaw);

        if (holder == null) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        Ref<EntityStore> spawned = commandBuffer.addEntity(holder, AddReason.SPAWN);

        if (spawned == null || !spawned.isValid()) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        PoiToolInteractions.send(playerRef, commandBuffer, Message.translation("aetherhaven_world_debug.aetherhaven.adventurerSpawnMarker.placed"));
        PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (pr != null && plugin != null) {
            PoiToolVisualizationSystem.scheduleRefreshForPlayer(world, pr.getUuid(), plugin);
        }
        context.getState().state = InteractionState.Finished;

    }



    @Nullable

    public static Vector3i resolveTargetBlock(

        @Nonnull Ref<EntityStore> playerRef,

        @Nonnull Store<EntityStore> store,

        @Nonnull InteractionContext context

    ) {

        @Nullable

        InteractionSyncData sync = context.getClientState();

        @Nullable

        BlockPosition blockPosition = sync != null ? sync.blockPosition : context.getTargetBlock();

        if (blockPosition != null) {

            return new Vector3i(blockPosition.x, blockPosition.y, blockPosition.z);

        }

        return TargetUtil.getTargetBlock(playerRef, 8.0, store);

    }

}

