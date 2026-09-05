package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Vanilla tops NPC health only on {@code SPAWN}. On {@code LOAD}, role max is reapplied and armor Health modifiers
 * raise max on the first Recalculate without healing current — health bars flash for every villager. Queue a one-shot
 * top-up after that Recalculate (and after equipping armor).
 */
public final class TownVillagerHealthTopUp {
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private TownVillagerHealthTopUp() {}

    public static void request(@Nonnull UUID entityUuid) {
        PENDING.add(entityUuid);
    }

    public static void request(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc != null) {
            PENDING.add(uc.getUuid());
        }
    }

    public static boolean consume(@Nonnull UUID entityUuid) {
        return PENDING.remove(entityUuid);
    }

    /** Fills Health to the current max (after armor modifiers). Safe no-op when already full. */
    public static void maximizeHealth(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        EntityStatMap map =
            commandBuffer != null
                ? commandBuffer.getComponent(ref, EntityStatMap.getComponentType())
                : null;
        if (map == null) {
            map = store.getComponent(ref, EntityStatMap.getComponentType());
        }
        if (map == null) {
            return;
        }
        int healthIndex = DefaultEntityStatTypes.getHealth();
        if (map.get(healthIndex) == null) {
            return;
        }
        map.maximizeStatValue(healthIndex);
        if (commandBuffer != null) {
            commandBuffer.putComponent(ref, EntityStatMap.getComponentType(), map);
        } else {
            store.putComponent(ref, EntityStatMap.getComponentType(), map);
        }
    }
}
