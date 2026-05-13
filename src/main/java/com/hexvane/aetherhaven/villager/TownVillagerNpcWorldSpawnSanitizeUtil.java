package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.SpawnBeaconReference;
import com.hypixel.hytale.server.npc.components.SpawnMarkerReference;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;

/**
 * Town NPCs are normal {@link NPCEntity} instances. The mod spawns them from code, but saved entities can still carry
 * spawn configuration, environment indices, or spawn reference components, and other engine systems can set despawn
 * state. This clears those fields for entities with {@link TownVillagerBinding} so ambient NPC despawn rules stay off.
 */
public final class TownVillagerNpcWorldSpawnSanitizeUtil {
    private TownVillagerNpcWorldSpawnSanitizeUtil() {}

    public static boolean needsSanitize(@Nonnull Ref<EntityStore> ref, @Nonnull NPCEntity npc, @Nonnull Store<EntityStore> store) {
        if (store.getComponent(ref, SpawnBeaconReference.getComponentType()) != null) {
            return true;
        }
        if (store.getComponent(ref, SpawnMarkerReference.getComponentType()) != null) {
            return true;
        }
        if (npc.getSpawnConfiguration() != Integer.MIN_VALUE) {
            return true;
        }
        if (npc.getEnvironment() != Integer.MIN_VALUE) {
            return true;
        }
        return npc.isDespawning() || npc.isPlayingDespawnAnim();
    }

    public static void sanitize(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.tryRemoveComponent(ref, SpawnBeaconReference.getComponentType());
        commandBuffer.tryRemoveComponent(ref, SpawnMarkerReference.getComponentType());
        npc.setSpawnConfiguration(Integer.MIN_VALUE);
        npc.setEnvironment(Integer.MIN_VALUE);
        npc.setDespawning(false);
        npc.setPlayingDespawnAnim(false);
    }
}
