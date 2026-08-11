package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityChunkUtil;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceLanes;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Raises, holds, and retracts carnival whack goblins. Hit handling is in {@link CarnivalWhackHitSystem}. */
public final class CarnivalWhackSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            CarnivalWhackComponent.getComponentType(),
            TransformComponent.getComponentType(),
            Velocity.getComponentType(),
            UUIDComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        CarnivalWhackComponent whack = archetypeChunk.getComponent(index, CarnivalWhackComponent.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        Velocity velocity = archetypeChunk.getComponent(index, Velocity.getComponentType());
        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        HeadRotation head = archetypeChunk.getComponent(index, HeadRotation.getComponentType());
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (whack == null || transform == null || velocity == null || uuidComponent == null || ref == null) {
            return;
        }
        UUID townId = whack.getTownId();
        UUID goblinUuid = uuidComponent.getUuid();
        if (townId == null) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        CarnivalWhackSession session = CarnivalWhackSessionIndex.get(townId);
        if (session == null || session.getPhase() != CarnivalWhackSession.Phase.PLAYING) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }

        velocity.set(0.0, 0.0, 0.0);
        whack.addStateSeconds(dt);
        Vector3d pos = transform.getPosition();
        faceSessionPlayer(store, session, transform, head, pos);
        double buryY = whack.getStandY() - CarnivalIds.WHACK_POP_HEIGHT;
        World world = store.getExternalData().getWorld();

        switch (whack.getState()) {
            case RISING -> {
                float t = Math.min(1f, whack.getStateSeconds() / CarnivalIds.WHACK_RISE_SECONDS);
                double y = buryY + (whack.getStandY() - buryY) * t;
                Vector3d next = new Vector3d(pos.x, y, pos.z);
                if (!EntityChunkUtil.isPositionChunkInMemory(world, next)) {
                    finishMiss(session, whack, goblinUuid, townId, store, commandBuffer, ref);
                    return;
                }
                transform.setPosition(next);
                if (t >= 1f) {
                    whack.setState(CarnivalWhackComponent.State.UP);
                    transform.setPosition(new Vector3d(pos.x, whack.getStandY(), pos.z));
                }
            }
            case UP -> {
                if (whack.getStateSeconds() >= CarnivalIds.WHACK_UP_SECONDS) {
                    whack.setState(CarnivalWhackComponent.State.RETRACTING);
                }
            }
            case HIT -> whack.setState(CarnivalWhackComponent.State.RETRACTING);
            case RETRACTING -> {
                float t = Math.min(1f, whack.getStateSeconds() / CarnivalIds.WHACK_RETRACT_SECONDS);
                double y = whack.getStandY() + (buryY - whack.getStandY()) * t;
                Vector3d next = new Vector3d(pos.x, y, pos.z);
                if (!EntityChunkUtil.isPositionChunkInMemory(world, next) || t >= 1f) {
                    // Hits are counted in CarnivalWhackHitSystem; markMissed is a no-op after a hit.
                    finishLeave(session, whack, goblinUuid, townId, store, commandBuffer, ref);
                    return;
                }
                transform.setPosition(next);
            }
        }
    }

    private static void faceSessionPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull CarnivalWhackSession session,
        @Nonnull TransformComponent transform,
        @Nullable HeadRotation head,
        @Nonnull Vector3d from
    ) {
        UUID playerUuid = session.getPlayerUuid();
        if (playerUuid == null) {
            return;
        }
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }
        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            return;
        }
        Vector3d playerPos = playerTransform.getPosition();
        float yaw = PigRaceLanes.facingYawRadians(playerPos.x - from.x, playerPos.z - from.z);
        Rotation3f body = transform.getRotation();
        if (body != null) {
            body.setYaw(yaw);
        } else {
            transform.setRotation(new Rotation3f(0f, yaw, 0f));
        }
        if (head != null) {
            Rotation3f headRot = head.getRotation();
            if (headRot != null) {
                headRot.setYaw(yaw);
            } else {
                head.setRotation(new Rotation3f(0f, yaw, 0f));
            }
        }
    }

    private static void finishMiss(
        @Nonnull CarnivalWhackSession session,
        @Nonnull CarnivalWhackComponent whack,
        @Nonnull UUID goblinUuid,
        @Nonnull UUID townId,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref
    ) {
        finishLeave(session, whack, goblinUuid, townId, store, commandBuffer, ref);
    }

    private static void finishLeave(
        @Nonnull CarnivalWhackSession session,
        @Nonnull CarnivalWhackComponent whack,
        @Nonnull UUID goblinUuid,
        @Nonnull UUID townId,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref
    ) {
        session.freeHole(whack.getHoleIndex());
        session.markMissed(goblinUuid);
        playFinishIfNeeded(store, session, townId);
        commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
    }

    static void playFinishIfNeeded(
        @Nonnull Store<EntityStore> store,
        @Nonnull CarnivalWhackSession session,
        @Nonnull UUID townId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || !session.consumeFinishSfxPending()) {
            return;
        }
        TownRecord town =
            AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin).getTown(townId);
        if (town == null) {
            return;
        }
        CarnivalAudio.playWhackFinish(store, CarnivalAudio.squareCenter(plugin, town));
        UUID playerUuid = session.getPlayerUuid();
        if (playerUuid != null) {
            CarnivalWhackClubUtil.removeAllWhackersForPlayer(store, playerUuid);
            CarnivalAnnounce.announceWhackHitCount(store, playerUuid, session.getHits(), session.getSpawned());
        }
        CarnivalResultSettlement.settleWhackAndPresent(store, town, session);
    }
}
