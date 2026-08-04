package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.entity.EntityRotationUtil;
import com.hexvane.aetherhaven.villager.AetherhavenNpcTeleport;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Teleports followers beside the player when they fall behind or stop making progress. */
public final class FollowPlayerTeleportRecovery {
    /** Horizontal distance beyond which the follower snaps immediately. */
    private static final double TELEPORT_DISTANCE = 28.0;
    private static final double TELEPORT_DISTANCE_SQ = TELEPORT_DISTANCE * TELEPORT_DISTANCE;
    private static final int STALL_TELEPORT_TICKS = 75;

    private static final ConcurrentHashMap<UUID, StallState> STALL_BY_NPC = new ConcurrentHashMap<>();

    private FollowPlayerTeleportRecovery() {}

    public static void clearTracking(@Nullable UUID npcUuid) {
        if (npcUuid != null) {
            STALL_BY_NPC.remove(npcUuid);
        }
    }

    /**
     * @return {@code true} if a recovery teleport was applied this tick
     */
    public static boolean tryRecover(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npc,
        @Nonnull Vector3d playerPos,
        @Nonnull Vector3d npcPos,
        double horizDistSq,
        double followStopDistSq,
        double followStandOffDistance
    ) {
        UUID npcUuid = npcEntityUuid(store, npcRef);
        if (npcUuid == null) {
            return false;
        }

        if (horizDistSq <= followStopDistSq) {
            STALL_BY_NPC.remove(npcUuid);
            return false;
        }

        if (horizDistSq >= TELEPORT_DISTANCE_SQ) {
            teleportBesidePlayer(
                npcRef,
                commandBuffer,
                store,
                npc,
                playerPos,
                npcPos,
                followStandOffDistance
            );
            STALL_BY_NPC.remove(npcUuid);
            return true;
        }

        StallState stall = STALL_BY_NPC.computeIfAbsent(npcUuid, u -> new StallState());
        AutonomyStuckTeleportRecovery.updateStall(stall, npcPos, playerPos.x, playerPos.z);
        if (!AutonomyStuckTeleportRecovery.isStallTeleportDue(stall, STALL_TELEPORT_TICKS)) {
            return false;
        }

        teleportBesidePlayer(
            npcRef,
            commandBuffer,
            store,
            npc,
            playerPos,
            npcPos,
            followStandOffDistance
        );
        AutonomyStuckTeleportRecovery.resetAfterRecovery(stall);
        return true;
    }

    @Nonnull
    public static Vector3d standOffBesidePlayer(
        @Nonnull Vector3d playerPos,
        @Nonnull Vector3d npcPos,
        double followStandOffDistance
    ) {
        double dx = npcPos.x - playerPos.x;
        double dz = npcPos.z - playerPos.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.05) {
            return new Vector3d(playerPos.x + followStandOffDistance, playerPos.y, playerPos.z);
        }
        double scale = followStandOffDistance / horiz;
        return new Vector3d(playerPos.x + dx * scale, playerPos.y, playerPos.z + dz * scale);
    }

    private static void teleportBesidePlayer(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npc,
        @Nonnull Vector3d playerPos,
        @Nonnull Vector3d npcPos,
        double followStandOffDistance
    ) {
        Vector3d target = standOffBesidePlayer(playerPos, npcPos, followStandOffDistance);
        Vector3d feet =
            VillagerBlockUtil.snapNpcFeetToStand(store.getExternalData().getWorld(), target);
        Rotation3f rotation = resolveBodyRotation(store, npcRef);
        AetherhavenNpcTeleport.apply(npcRef, commandBuffer, Teleport.createExact(feet, rotation));
        npc.setLeashPoint(feet);
        commandBuffer.putComponent(npcRef, NPCEntity.getComponentType(), npc);
    }

    @Nonnull
    private static Rotation3f resolveBodyRotation(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            return EntityRotationUtil.repair(tc.getRotation());
        }
        return new Rotation3f(0.0F, 0.0F, 0.0F);
    }

    @Nullable
    private static UUID npcEntityUuid(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    private static final class StallState implements AutonomyStallTrackable {
        private double sampleX = Double.NaN;
        private double sampleZ = Double.NaN;
        private double anchorX = Double.NaN;
        private double anchorZ = Double.NaN;
        private double goalDistSq = Double.NaN;
        private int stallTicks;

        @Override
        public double getAutonomySampleX() {
            return sampleX;
        }

        @Override
        public double getAutonomySampleZ() {
            return sampleZ;
        }

        @Override
        public double getAutonomyAnchorX() {
            return anchorX;
        }

        @Override
        public double getAutonomyAnchorZ() {
            return anchorZ;
        }

        @Override
        public double getAutonomyGoalDistSq() {
            return goalDistSq;
        }

        @Override
        public int getAutonomyStallTicks() {
            return stallTicks;
        }

        @Override
        public void setAutonomySamplePosition(double x, double z) {
            sampleX = x;
            sampleZ = z;
        }

        @Override
        public void setAutonomyAnchorPosition(double x, double z) {
            anchorX = x;
            anchorZ = z;
        }

        @Override
        public void setAutonomyGoalDistSq(double distSq) {
            goalDistSq = distSq;
        }

        @Override
        public void setAutonomyStallTicks(int ticks) {
            stallTicks = ticks;
        }

        @Override
        public void resetAutonomyStallTracking() {
            sampleX = Double.NaN;
            sampleZ = Double.NaN;
            anchorX = Double.NaN;
            anchorZ = Double.NaN;
            goalDistSq = Double.NaN;
            stallTicks = 0;
        }
    }
}
