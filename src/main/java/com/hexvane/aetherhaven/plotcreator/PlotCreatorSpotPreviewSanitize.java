package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.SpawnBeaconReference;
import com.hypixel.hytale.server.npc.components.SpawnMarkerReference;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps plot-creator preview NPCs out of ambient spawn/despawn rules and stops walk/run cycles while Frozen.
 * Frozen entities do not refresh movement states; {@link NpcStandStill#forceIdleMovementStates} clears leftover walk.
 */
public final class PlotCreatorSpotPreviewSanitize {
    private PlotCreatorSpotPreviewSanitize() {}

    public static void applyOnSpawn(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        store.putComponent(ref, Frozen.getComponentType(), Frozen.get());
        store.tryRemoveComponent(ref, SpawnBeaconReference.getComponentType());
        store.tryRemoveComponent(ref, SpawnMarkerReference.getComponentType());
        NpcStandStill.forceIdleMovementStates(store, ref);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        npc.setSpawnConfiguration(Integer.MIN_VALUE);
        npc.setEnvironment(Integer.MIN_VALUE);
        npc.setDespawning(false);
        npc.setPlayingDespawnAnim(false);
        npc.playAnimation(ref, AnimationSlot.Movement, null, store);
        AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, store);
    }

    public static void applyEachTick(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        NpcStandStill.freeze(ref, commandBuffer);
        commandBuffer.tryRemoveComponent(ref, SpawnBeaconReference.getComponentType());
        commandBuffer.tryRemoveComponent(ref, SpawnMarkerReference.getComponentType());
        NpcStandStill.clearResidualMotion(store, ref, commandBuffer);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        if (npc.getSpawnConfiguration() != Integer.MIN_VALUE) {
            npc.setSpawnConfiguration(Integer.MIN_VALUE);
        }
        if (npc.getEnvironment() != Integer.MIN_VALUE) {
            npc.setEnvironment(Integer.MIN_VALUE);
        }
        if (npc.isDespawning() || npc.isPlayingDespawnAnim()) {
            npc.setDespawning(false);
            npc.setPlayingDespawnAnim(false);
        }
    }

    public static void clearMovementAnim(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer != null) {
            commandBuffer.run(s -> {
                if (!ref.isValid()) {
                    return;
                }
                NpcStandStill.forceIdleMovementStates(s, ref);
                NPCEntity live = s.getComponent(ref, NPCEntity.getComponentType());
                if (live != null) {
                    live.playAnimation(ref, AnimationSlot.Movement, null, s);
                }
                AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, s);
            });
            return;
        }
        NpcStandStill.forceIdleMovementStates(store, ref);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) {
            npc.playAnimation(ref, AnimationSlot.Movement, null, store);
        }
        AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, store);
    }
}
