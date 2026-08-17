package com.hexvane.aetherhaven.festival.snowball;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Counts a physics snowball hit only when the thrower is still a living fighter.
 * Always finishes so the projectile can still despawn.
 */
public final class SnowballProjectileHitInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<SnowballProjectileHitInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(SnowballProjectileHitInteraction.class, SnowballProjectileHitInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Score a snowball fight hit when the thrower is still in the fight.")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    public boolean needsRemoteSync() {
        return false;
    }

    @Override
    protected void firstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        context.getState().state = InteractionState.Finished;
        if (type != InteractionType.ProjectileHit) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> projectileRef = context.getEntity();
        Ref<EntityStore> victimRef = context.getTargetEntity();
        if (commandBuffer == null || projectileRef == null || !projectileRef.isValid()
            || victimRef == null || !victimRef.isValid()) {
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        UUID victimUuid = SnowballFightHits.uuidOf(store, victimRef);
        UUID attackerUuid = attackerUuid(store, context, projectileRef);
        if (victimUuid == null || attackerUuid == null) {
            return;
        }
        SnowballFightHits.tryScore(
            store,
            commandBuffer,
            victimRef,
            victimUuid,
            attackerUuid,
            SnowballFightHits.projectileToken(store, projectileRef)
        );
    }

    @Nullable
    private static UUID attackerUuid(
        @Nonnull Store<EntityStore> store,
        @Nonnull InteractionContext context,
        @Nonnull Ref<EntityStore> projectileRef
    ) {
        UUID fromOwner = SnowballFightHits.uuidOf(store, context.getOwningEntity());
        if (fromOwner != null) {
            return fromOwner;
        }
        ProjectileComponent projectile = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
        if (projectile != null && projectile.getCreatorUuid() != null) {
            return projectile.getCreatorUuid();
        }
        StandardPhysicsProvider physics =
            store.getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (physics != null) {
            return physics.getCreatorUuid();
        }
        return null;
    }
}
