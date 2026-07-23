package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * RTS commander: vanilla creative flight for WASD pan + top-down camera.
 * Zoom is flight altitude only (Space / Ctrl); the camera packet uses a fixed pull-back distance.
 */
public final class RtsCommanderCameraSystem {
    private RtsCommanderCameraSystem() {}

    public static final class Follow extends EntityTickingSystem<EntityStore> {
        @SuppressWarnings("unused")
        private final AetherhavenPlugin plugin;

        public Follow(@Nonnull AetherhavenPlugin plugin) {
            this.plugin = plugin;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(
                RtsCommandPlayerComponent.getComponentType(),
                Player.getComponentType(),
                TransformComponent.getComponentType(),
                PlayerRef.getComponentType()
            );
        }

        @Override
        public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            RtsCommandPlayerComponent session = chunk.getComponent(index, RtsCommandPlayerComponent.getComponentType());
            if (session == null || !session.isActive()) {
                return;
            }
            Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
            TransformComponent tc = chunk.getComponent(index, TransformComponent.getComponentType());
            if (tc == null) {
                return;
            }

            RtsMovementSupport.ensureFlying(playerRef, store);

            var pos = tc.getPosition();
            applyCameraFollow(session, playerRef, tc, store, commandBuffer);

            pos = tc.getPosition();
            session.trackFocus(pos.x, pos.y, pos.z);
            RtsScreenPickUtil.refreshPickViewHeight(session, store);

            clampPlayerToTerritory(session, playerRef, tc, store, commandBuffer);
        }

        private static void applyCameraFollow(
            @Nonnull RtsCommandPlayerComponent session,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull TransformComponent tc,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            UUID followId = session.getCameraFollowGuardUuid();
            if (followId == null) {
                return;
            }
            Ref<EntityStore> guardRef = RtsGuardDirectory.findByUuid(store, followId);
            if (guardRef == null) {
                session.clearCameraFollow();
                commandBuffer.putComponent(playerRef, RtsCommandPlayerComponent.getComponentType(), session);
                return;
            }
            TransformComponent guardTc = store.getComponent(guardRef, TransformComponent.getComponentType());
            if (guardTc == null) {
                session.clearCameraFollow();
                commandBuffer.putComponent(playerRef, RtsCommandPlayerComponent.getComponentType(), session);
                return;
            }
            org.joml.Vector3d guardPos = guardTc.getPosition();
            org.joml.Vector3d commanderPos = tc.getPosition();
            if (session.cameraFollowManualOverride(commanderPos.x, commanderPos.z, guardPos.x, guardPos.z)) {
                session.clearCameraFollow();
                commandBuffer.putComponent(playerRef, RtsCommandPlayerComponent.getComponentType(), session);
                return;
            }
            tc.getPosition().x = guardPos.x;
            tc.getPosition().z = guardPos.z;
            session.trackFocus(guardPos.x, commanderPos.y, guardPos.z);
            session.setCameraFollowSnap(commanderPos.x, commanderPos.z, guardPos.x, guardPos.z);
            commandBuffer.putComponent(playerRef, TransformComponent.getComponentType(), tc);
            commandBuffer.putComponent(playerRef, RtsCommandPlayerComponent.getComponentType(), session);
        }

        private void clampPlayerToTerritory(
            @Nonnull RtsCommandPlayerComponent session,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull TransformComponent tc,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
            try {
                TownRecord town = tm.getTown(UUID.fromString(session.getTownId()));
                if (town == null) {
                    return;
                }
                int overlap = com.hexvane.aetherhaven.AetherhavenConstants.RTS_TERRITORY_OVERLAP_BLOCKS;
                int cx = town.getCharterX();
                int cz = town.getCharterZ();
                int r = TownTerritoryClaims.maxCharterToClaimEdgeBlocks(town) + overlap;
                double minX = cx - r;
                double maxX = cx + r;
                double minZ = cz - r;
                double maxZ = cz + r;
                double x = tc.getPosition().x;
                double z = tc.getPosition().z;
                double clampedX = Math.max(minX, Math.min(maxX, x));
                double clampedZ = Math.max(minZ, Math.min(maxZ, z));
                if (Math.abs(clampedX - x) > 0.01 || Math.abs(clampedZ - z) > 0.01) {
                    tc.getPosition().x = clampedX;
                    tc.getPosition().z = clampedZ;
                    commandBuffer.putComponent(playerRef, TransformComponent.getComponentType(), tc);
                    session.trackFocus(clampedX, tc.getPosition().y, clampedZ);
                    commandBuffer.putComponent(playerRef, RtsCommandPlayerComponent.getComponentType(), session);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
