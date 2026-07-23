package com.hexvane.aetherhaven.patrol;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Remembers NPCs a player recently damaged so following guards can assist after NPC {@code DamageData} is cleared
 * each role tick.
 */
public final class GuardPlayerProvokedTargets implements Resource<EntityStore> {
    /** How long guards keep assisting a target the player struck. */
    public static final long PROVOKE_MEMORY_MS = 10_000L;

    @Nullable
    private static volatile ResourceType<EntityStore, GuardPlayerProvokedTargets> resourceType;

    private final Int2ObjectMap<Entry> byVictimIndex = new Int2ObjectOpenHashMap<>();

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        resourceType = registry.registerResource(GuardPlayerProvokedTargets.class, GuardPlayerProvokedTargets::new);
    }

    @Nonnull
    public static ResourceType<EntityStore, GuardPlayerProvokedTargets> getResourceType() {
        ResourceType<EntityStore, GuardPlayerProvokedTargets> t = resourceType;
        if (t == null) {
            throw new IllegalStateException("GuardPlayerProvokedTargets not registered");
        }
        return t;
    }

    public void markPlayerHit(@Nonnull UUID playerUuid, @Nonnull Ref<EntityStore> victimRef, long nowMs) {
        if (!victimRef.isValid()) {
            return;
        }
        long expireAt = nowMs + PROVOKE_MEMORY_MS;
        Entry entry = new Entry(playerUuid, expireAt);
        byVictimIndex.put(victimRef.getIndex(), entry);
    }

    public boolean isProvokedByPlayer(
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> victimRef,
        long nowMs
    ) {
        if (!victimRef.isValid()) {
            return false;
        }
        Entry entry = byVictimIndex.get(victimRef.getIndex());
        if (entry == null) {
            return false;
        }
        if (nowMs > entry.expireAtMs) {
            byVictimIndex.remove(victimRef.getIndex());
            return false;
        }
        return playerUuid.equals(entry.playerUuid);
    }

    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        GuardPlayerProvokedTargets copy = new GuardPlayerProvokedTargets();
        copy.byVictimIndex.putAll(byVictimIndex);
        return copy;
    }

    private record Entry(@Nonnull UUID playerUuid, long expireAtMs) {}
}
