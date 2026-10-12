package com.hexvane.aetherhaven.patrol;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * NPCs that recently damaged a player or town guard — survives NPC {@code DamageData} reset each tick.
 *
 * <p>Every entry is kept under the attacker's own {@link Ref}, never under its entity index. The game hands a freed
 * index to the next entity that enters the world, so an index key passed a dead attacker's guilt to whatever spawned
 * next: guards then struck a pet, a summon or a fresh raider the moment it appeared, with no line of sight needed.
 */
public final class GuardNpcAttackerMemory implements Resource<EntityStore> {
    public static final long MEMORY_MS = GuardPlayerProvokedTargets.PROVOKE_MEMORY_MS;

    @Nullable
    private static volatile ResourceType<EntityStore, GuardNpcAttackerMemory> resourceType;

    // Full references avoid treating a reused entity index as the same attacker. A Ref has identity equality.
    private final java.util.Map<Ref<EntityStore>, Long> expireByAttacker = new java.util.HashMap<>();
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
        // Entries for creatures that have left the world or been forgiven go as new ones arrive.
        expireByAttacker.entrySet().removeIf(entry -> !entry.getKey().isValid() || nowMs > entry.getValue());
        expireByAttacker.put(attackerRef, nowMs + MEMORY_MS);
    }

    public boolean isMarkedAttacker(@Nonnull Ref<EntityStore> attackerRef, long nowMs) {
        if (!attackerRef.isValid()) {
            return false;
        }
        Long expireAt = expireByAttacker.get(attackerRef);
        if (expireAt == null) {
            return false;
        }
        if (nowMs > expireAt) {
            expireByAttacker.remove(attackerRef);
            return false;
        }
        return true;
    }

    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        GuardNpcAttackerMemory copy = new GuardNpcAttackerMemory();
        copy.expireByAttacker.putAll(expireByAttacker);
        attackersByPlayer.forEach((player, attackers) -> copy.attackersByPlayer.put(player, new java.util.HashMap<>(attackers)));
        return copy;
    }
}
