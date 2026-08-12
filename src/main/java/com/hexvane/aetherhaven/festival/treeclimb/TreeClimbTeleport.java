package com.hexvane.aetherhaven.festival.treeclimb;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Player teleports for tree climb start/return. Uses CommandBuffer when inside chunk iteration. */
public final class TreeClimbTeleport {
    private TreeClimbTeleport() {}

    /** Teleports joined racers to start pads by join order. */
    public static void teleportJoinedToStartPads(
        @Nonnull Store<EntityStore> store,
        @Nonnull TreeClimbSession session
    ) {
        List<UUID> racers = session.joinedView();
        List<TreeClimbSession.StartPad> pads = session.startPadsView();
        if (racers.isEmpty() || pads.isEmpty()) {
            return;
        }
        store.forEachChunk(
            Query.and(
                Player.getComponentType(),
                PlayerRef.getComponentType(),
                UUIDComponent.getComponentType(),
                TransformComponent.getComponentType()
            ),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null) {
                        continue;
                    }
                    int slot = racers.indexOf(uc.getUuid());
                    if (slot < 0) {
                        continue;
                    }
                    TreeClimbSession.StartPad pad = pads.get(Math.min(slot, pads.size() - 1));
                    applyPad(chunk.getReferenceTo(i), chunk, i, commandBuffer, pad);
                }
            }
        );
    }

    /** Teleports players to their assigned return pads (race end). */
    public static void teleportToPads(
        @Nonnull Store<EntityStore> store,
        @Nonnull Map<UUID, TreeClimbSession.StartPad> padsByUuid
    ) {
        if (padsByUuid.isEmpty()) {
            return;
        }
        store.forEachChunk(
            Query.and(
                Player.getComponentType(),
                PlayerRef.getComponentType(),
                UUIDComponent.getComponentType(),
                TransformComponent.getComponentType()
            ),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null) {
                        continue;
                    }
                    TreeClimbSession.StartPad pad = padsByUuid.get(uc.getUuid());
                    if (pad == null) {
                        continue;
                    }
                    applyPad(chunk.getReferenceTo(i), chunk, i, commandBuffer, pad);
                }
            }
        );
    }

    private static void applyPad(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TreeClimbSession.StartPad pad
    ) {
        float yawRad = (float) Math.toRadians(pad.yawDegrees());
        Rotation3f rot = new Rotation3f(0f, yawRad, 0f);
        Vector3d dest = new Vector3d(pad.x(), pad.y(), pad.z());
        commandBuffer.putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(dest, rot));
        Velocity vel = chunk.getComponent(index, Velocity.getComponentType());
        if (vel != null) {
            vel.setZero();
            commandBuffer.putComponent(ref, Velocity.getComponentType(), vel);
        }
    }
}
