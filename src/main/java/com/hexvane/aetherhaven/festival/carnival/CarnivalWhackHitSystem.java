package com.hexvane.aetherhaven.festival.carnival;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Counts melee hits on carnival whack goblins. Cancels damage so the prop is never destroyed by combat.
 */
public final class CarnivalWhackHitSystem extends DamageEventSystem {
    private static final Query<EntityStore> QUERY = CarnivalWhackComponent.getComponentType();

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
        CarnivalWhackComponent whack = archetypeChunk.getComponent(index, CarnivalWhackComponent.getComponentType());
        if (whack == null) {
            return;
        }
        damage.setCancelled(true);
        if (!whack.canAcceptHit()) {
            return;
        }
        UUID townId = whack.getTownId();
        if (townId == null) {
            return;
        }
        CarnivalWhackSession session = CarnivalWhackSessionIndex.get(townId);
        if (session == null || session.getPhase() != CarnivalWhackSession.Phase.PLAYING) {
            return;
        }
        UUID playerUuid = session.getPlayerUuid();
        Ref<EntityStore> attackerRef = sessionPlayerAttacker(store, damage, playerUuid);
        if (attackerRef == null) {
            return;
        }
        // Only the carnival Goblin Whacker scores hits.
        if (!CarnivalWhackClubUtil.holdingWhacker(store, attackerRef)) {
            return;
        }
        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }
        // MaxTargets alone is not enough: selectors can still fork one entity per tick across a swing.
        if (!session.tryClaimHit()) {
            return;
        }
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        session.markHit(uuidComponent.getUuid());
        whack.setState(CarnivalWhackComponent.State.HIT);
        if (transform != null) {
            CarnivalAudio.playGoblinHurt(store, transform.getPosition());
        }
        CarnivalWhackSystem.playFinishIfNeeded(store, session, townId);
    }

    @Nullable
    private static Ref<EntityStore> sessionPlayerAttacker(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nullable UUID playerUuid
    ) {
        if (playerUuid == null || !(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return null;
        }
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return null;
        }
        if (store.getComponent(attackerRef, Player.getComponentType()) == null) {
            return null;
        }
        UUIDComponent uc = store.getComponent(attackerRef, UUIDComponent.getComponentType());
        if (uc == null || !playerUuid.equals(uc.getUuid())) {
            return null;
        }
        return attackerRef;
    }
}
