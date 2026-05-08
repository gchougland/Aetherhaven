package com.hexvane.aetherhaven.floatinggift;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pops the balloon when {@link Damage} is queued (melee / interactions that apply damage to non-living targets).
 * <p>
 * Engine projectile collisions only call {@link com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems#executeDamage}
 * for {@link com.hypixel.hytale.server.core.entity.LivingEntity} targets — see {@code ProjectileComponent} hit handling — so
 * arrows and spell bolts are handled separately in {@link FloatingGiftSystem} via tangible overlap with projectile entities.
 */
public final class FloatingGiftDamagePopSystem extends DamageEventSystem {
    private static final Query<EntityStore> QUERY = FloatingGiftComponent.getComponentType();

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        FloatingGiftComponent gift = archetypeChunk.getComponent(index, FloatingGiftComponent.getComponentType());
        if (gift == null || gift.getState() != FloatingGiftState.FLOATING) {
            return;
        }
        damage.setCancelled(true);
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        Velocity velocity = archetypeChunk.getComponent(index, Velocity.getComponentType());
        if (transform == null || velocity == null) {
            return;
        }
        FloatingGiftTriggers.beginPop(commandBuffer, ref, gift, transform, velocity);
    }
}
