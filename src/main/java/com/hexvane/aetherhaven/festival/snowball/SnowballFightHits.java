package com.hexvane.aetherhaven.festival.snowball;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared snowball fight scoring. Counts a hit only when the thrower is still in the fight. */
final class SnowballFightHits {
    private SnowballFightHits() {}

    static boolean tryScore(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull UUID victimUuid,
        @Nonnull UUID attackerUuid,
        int projectileToken
    ) {
        SnowballSession session = SnowballSessionIndex.sessionForFighter(victimUuid);
        if (session == null) {
            return false;
        }
        if (!session.isLivingFighter(attackerUuid) || !session.isLivingFighter(victimUuid)) {
            return false;
        }
        if (!session.wouldHit(victimUuid, attackerUuid)) {
            return false;
        }
        if (!session.consumeHitProjectile(projectileToken)) {
            return false;
        }
        if (!session.tryHit(victimUuid, attackerUuid)) {
            return false;
        }
        SnowballSession.Fighter victim = session.fighter(victimUuid);
        int livesLeft = victim != null ? victim.lives() : 0;
        boolean victimOut = session.isOutFighter(victimUuid);
        SnowballHitFeedback.play(store, commandBuffer, victimRef, attackerUuid, livesLeft, victimOut);
        return true;
    }

    static int projectileToken(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> projectileRef) {
        NetworkId networkId = store.getComponent(projectileRef, NetworkId.getComponentType());
        if (networkId != null) {
            return networkId.getId();
        }
        UUIDComponent uuid = store.getComponent(projectileRef, UUIDComponent.getComponentType());
        if (uuid != null && uuid.getUuid() != null) {
            return uuid.getUuid().hashCode();
        }
        return System.identityHashCode(projectileRef);
    }

    @Nullable
    static UUID uuidOf(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuid != null ? uuid.getUuid() : null;
    }
}
