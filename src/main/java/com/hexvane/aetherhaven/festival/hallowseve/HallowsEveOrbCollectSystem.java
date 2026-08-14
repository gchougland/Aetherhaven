package com.hexvane.aetherhaven.festival.hallowseve;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Lets the current racer collect nearby maze orbs. */
public final class HallowsEveOrbCollectSystem extends TickingSystem<EntityStore> {
    private static final double RADIUS_XZ_SQ =
        HallowsEveIds.ORB_COLLECT_RADIUS_XZ * HallowsEveIds.ORB_COLLECT_RADIUS_XZ;

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (!HallowsEveOrbComponent.isRegistered()) {
            return;
        }
        Map<UUID, RacerPos> racers = new HashMap<>();
        for (var entry : HallowsEveSessionIndex.entries()) {
            HallowsEveSession session = entry.getValue();
            if (session == null || session.getPhase() != HallowsEveSession.Phase.RACING || session.getPlayerUuid() == null) {
                continue;
            }
            racers.put(session.getPlayerUuid(), new RacerPos(entry.getKey(), session, null, null));
        }
        if (racers.isEmpty()) {
            return;
        }
        store.forEachChunk(
            Query.and(Player.getComponentType(), UUIDComponent.getComponentType(), TransformComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null) {
                        continue;
                    }
                    RacerPos racer = racers.get(uc.getUuid());
                    if (racer == null) {
                        continue;
                    }
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    racer.pos = new Vector3d(tc.getPosition());
                    racer.playerRef = chunk.getReferenceTo(i);
                }
            }
        );
        store.forEachChunk(
            Query.and(
                HallowsEveOrbComponent.getComponentType(),
                TransformComponent.getComponentType(),
                UUIDComponent.getComponentType()
            ),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    HallowsEveOrbComponent orb = chunk.getComponent(i, HallowsEveOrbComponent.getComponentType());
                    TransformComponent orbTc = chunk.getComponent(i, TransformComponent.getComponentType());
                    UUIDComponent orbUuid = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (orb == null || orbTc == null || orbUuid == null) {
                        continue;
                    }
                    UUID townId = orb.getTownId();
                    RacerPos match = null;
                    for (RacerPos racer : racers.values()) {
                        if (racer.townId.equals(townId) && racer.pos != null) {
                            match = racer;
                            break;
                        }
                    }
                    if (match == null || match.pos == null) {
                        continue;
                    }
                    Vector3d orbPos = orbTc.getPosition();
                    double dx = match.pos.x - orbPos.x;
                    double dz = match.pos.z - orbPos.z;
                    if (dx * dx + dz * dz > RADIUS_XZ_SQ) {
                        continue;
                    }
                    if (Math.abs(match.pos.y - orbPos.y) > HallowsEveIds.ORB_COLLECT_RADIUS_Y) {
                        continue;
                    }
                    match.session.addCollected();
                    match.session.removeActiveOrb(orbUuid.getUuid());
                    HallowsEveAudio.playOrbCollect(match.playerRef, store, orbPos, match.session.getCollected());
                    HallowsEveOrbSpawnService.despawnOrb(commandBuffer, chunk.getReferenceTo(i));
                }
            }
        );
    }

    private static final class RacerPos {
        @Nonnull
        private final UUID townId;
        @Nonnull
        private final HallowsEveSession session;
        private Vector3d pos;
        private com.hypixel.hytale.component.Ref<EntityStore> playerRef;

        private RacerPos(
            @Nonnull UUID townId,
            @Nonnull HallowsEveSession session,
            Vector3d pos,
            com.hypixel.hytale.component.Ref<EntityStore> playerRef
        ) {
            this.townId = townId;
            this.session = session;
            this.pos = pos;
            this.playerRef = playerRef;
        }
    }
}
