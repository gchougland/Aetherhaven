package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import javax.annotation.Nonnull;

/** Applies mod-authorized teleports for town NPCs (autonomy recovery, admin tools). */
public final class AetherhavenNpcTeleport {
    private AetherhavenNpcTeleport() {}

    public static boolean isManagedNpc(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (store.getComponent(ref, TownVillagerBinding.getComponentType()) != null) {
            return true;
        }
        if (store.getComponent(ref, TownsfolkCharacterBinding.getComponentType()) != null) {
            return true;
        }
        return store.getComponent(ref, AetherhavenVillagerHandle.getComponentType()) != null;
    }

    public static void apply(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Teleport teleport
    ) {
        commandBuffer.putComponent(ref, AetherhavenAllowedTeleport.getComponentType(), AetherhavenAllowedTeleport.INSTANCE);
        commandBuffer.putComponent(ref, Teleport.getComponentType(), teleport);
    }

    public static void apply(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Teleport teleport
    ) {
        store.putComponent(ref, AetherhavenAllowedTeleport.getComponentType(), AetherhavenAllowedTeleport.INSTANCE);
        store.putComponent(ref, Teleport.getComponentType(), teleport);
    }
}
