package com.hexvane.aetherhaven.patrol;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** NPCs that recently damaged a player or town guard — survives NPC {@code DamageData} reset each tick. */
public final class GuardNpcAttackerMemory implements Resource<EntityStore> {
    public static final long MEMORY_MS = GuardPlayerProvokedTargets.PROVOKE_MEMORY_MS;

    @Nullable
    private static volatile ResourceType<EntityStore, GuardNpcAttackerMemory> resourceType;

    private final Int2LongMap expireByAttackerIndex = new Int2LongOpenHashMap();
    // Full references avoid treating a reused entity index as the same attacker.
    private final java.util.Map<java.util.UUID, java.util.Map<Ref<EntityStore>, Long>> attackersByPlayer = new java.util.HashMap<>();

    public void markPlayerAttacker(@Nonnull java.util.UUID player, @Nonnull Ref<EntityStore> attacker, long nowMs) {
        prunePlayerAttackers(nowMs);
        if (attacker.isValid()) {
            attackersByPlayer.computeIfAbsent(player, unused -> new java.util.HashMap<>()).put(attacker, nowMs + MEMORY_MS);
        }
    }

    public boolean attackedPlayer(@Nonnull java.util.UUID player, @Nonnull Ref<EntityStore> attacker, long nowMs) {
        var attackers = attackersByPlayer.get(player);
        if (attackers == null || !attacker.isValid()) return false;
        Long expiry = attackers.get(attacker);
        if (expiry == null) return false;
        if (nowMs >= expiry) {
            attackers.remove(attacker);
            if (attackers.isEmpty()) attackersByPlayer.remove(player);
            return false;
        }
        return true;
    }

    private void prunePlayerAttackers(long nowMs) {
        attackersByPlayer.values().removeIf(attackers -> {
            attackers.entrySet().removeIf(entry -> !entry.getKey().isValid() || nowMs >= entry.getValue());
            return attackers.isEmpty();
        });
    }

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        resourceType = registry.registerResource(GuardNpcAttackerMemory.class, GuardNpcAttackerMemory::new);
    }

    @Nonnull
    public static ResourceType<EntityStore, GuardNpcAttackerMemory> getResourceType() {
        ResourceType<EntityStore, GuardNpcAttackerMemory> t = resourceType;
        if (t == null) {
            throw new IllegalStateException("GuardNpcAttackerMemory not registered");
        }
        return t;
    }

    public void markAttacker(@Nonnull Ref<EntityStore> attackerRef, long nowMs) {
        if (!attackerRef.isValid()) {
            return;
        }
        expireByAttackerIndex.put(attackerRef.getIndex(), nowMs + MEMORY_MS);
    }

    public boolean isMarkedAttacker(@Nonnull Ref<EntityStore> attackerRef, long nowMs) {
        if (!attackerRef.isValid()) {
            return false;
        }
        long expireAt = expireByAttackerIndex.get(attackerRef.getIndex());
        if (expireAt == 0L) {
            return false;
        }
        if (nowMs > expireAt) {
            expireByAttackerIndex.remove(attackerRef.getIndex());
            return false;
        }
        return true;
    }

    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        GuardNpcAttackerMemory copy = new GuardNpcAttackerMemory();
        copy.expireByAttackerIndex.putAll(expireByAttackerIndex);
        attackersByPlayer.forEach((player, attackers) -> copy.attackersByPlayer.put(player, new java.util.HashMap<>(attackers)));
        return copy;
    }
}
