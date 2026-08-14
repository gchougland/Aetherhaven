package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Player teleport and freeze for the maze countdown. */
public final class HallowsEveTeleport {
    private HallowsEveTeleport() {}

    /**
     * World body yaw so a player at {@code from} looks toward {@code toward}. Hytale forward is opposite
     * {@code atan2(dx, dz)}.
     */
    public static float yawDegreesToward(@Nonnull Vector3d from, @Nonnull Vector3d toward) {
        double dx = toward.x - from.x;
        double dz = toward.z - from.z;
        if (dx * dx + dz * dz < 1.0e-8) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, -dz));
    }

    /** Sets the maze start pad in world space and faces the player toward the maze center. */
    public static void bindStartPad(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival,
        @Nonnull HallowsEveSession session
    ) {
        FestivalDefinition.MazeStartLocalRow start = festival.getMazeStartLocal();
        if (start == null) {
            return;
        }
        Vector3d pos =
            FestivalPrefabSwapService.spotWorldPosition(
                plugin,
                square,
                start.getLocalX(),
                start.getLocalY(),
                start.getLocalZ()
            );
        Vector3d center = mazeCenterWorld(plugin, square, festival, pos.y);
        session.setStartPad(pos.x, pos.y, pos.z, yawDegreesToward(pos, center));
    }

    @Nonnull
    private static Vector3d mazeCenterWorld(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival,
        double fallbackY
    ) {
        double[] local = festival.getCenterpieceLocalExact();
        if (local != null) {
            return FestivalPrefabSwapService.spotWorldPosition(
                plugin,
                square,
                (int) Math.round(local[0]),
                (int) Math.round(local[1]),
                (int) Math.round(local[2])
            );
        }
        return FestivalPrefabSwapService.spotWorldPosition(plugin, square, 0, (int) Math.round(fallbackY), 0);
    }

    public static void applyStartPad(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull HallowsEveSession session,
        boolean freeze
    ) {
        float yawRad = (float) Math.toRadians(session.getStartYawDegrees());
        Rotation3f rot = new Rotation3f(0f, yawRad, 0f);
        Vector3d dest = new Vector3d(session.getStartX(), session.getStartY(), session.getStartZ());
        commandBuffer.putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(dest, rot));
        Velocity vel = chunk.getComponent(index, Velocity.getComponentType());
        if (vel != null) {
            vel.setZero();
            commandBuffer.putComponent(ref, Velocity.getComponentType(), vel);
        }
        if (freeze) {
            commandBuffer.putComponent(ref, Frozen.getComponentType(), Frozen.get());
        }
    }

    public static void thaw(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
    }
}
