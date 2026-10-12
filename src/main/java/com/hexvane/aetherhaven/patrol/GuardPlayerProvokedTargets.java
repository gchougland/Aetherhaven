package com.hexvane.aetherhaven.patrol;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Remembers NPCs a player recently damaged so following guards can assist after NPC {@code DamageData} is cleared
 * each role tick.
 *
 * <p>Every entry is kept under the creature's own {@link Ref}, never under its entity index. The game hands a freed
 * index to the next entity that enters the world, so an index key made the next creature to spawn after a kill (a pet
 * the player summoned, for one) look like the creature the player had just been fighting, and the escort attacked it.
 */
public final class GuardPlayerProvokedTargets implements Resource<EntityStore> {
    /** How long guards keep assisting a target the player struck. */
    public static final long PROVOKE_MEMORY_MS = 10_000L;

    @Nullable
    private static volatile ResourceType<EntityStore, GuardPlayerProvokedTargets> resourceType;

    // A Ref has identity equality, so a new creature never matches an old entry.
    private final Map<Ref<EntityStore>, Entry> byVictim = new HashMap<>();

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
        // Entries for creatures that have left the world or that the fight has moved on from go as new ones arrive.
        byVictim.entrySet().removeIf(old -> !old.getKey().isValid() || nowMs > old.getValue().expireAtMs);
        byVictim.put(victimRef, entry);
    }

    public boolean isProvokedByPlayer(
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> victimRef,
        long nowMs
    ) {
        if (!victimRef.isValid()) {
            return false;
        }
        Entry entry = byVictim.get(victimRef);
        if (entry == null) {
            return false;
        }
        if (nowMs > entry.expireAtMs) {
            byVictim.remove(victimRef);
            return false;
        }
        return playerUuid.equals(entry.playerUuid);
    }

    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        GuardPlayerProvokedTargets copy = new GuardPlayerProvokedTargets();
        copy.byVictim.putAll(byVictim);
        return copy;
    }

    private record Entry(@Nonnull UUID playerUuid, long expireAtMs) {}
}
