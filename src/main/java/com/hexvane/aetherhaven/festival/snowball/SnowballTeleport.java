package com.hexvane.aetherhaven.festival.snowball;

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
import com.hexvane.aetherhaven.villager.AetherhavenNpcTeleport;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Player and villager teleports for snowball pads. Uses CommandBuffer inside chunk iteration. */
public final class SnowballTeleport {
    private SnowballTeleport() {}

    public static void teleportFightersToPads(
        @Nonnull Store<EntityStore> store,
        @Nonnull SnowballSession session
    ) {
        store.forEachChunk(
            fighterQuery(),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null) {
                        continue;
                    }
                    SnowballSession.Fighter fighter = session.fighter(uc.getUuid());
                    if (fighter == null) {
                        continue;
                    }
                    applyPad(chunk.getReferenceTo(i), chunk, i, commandBuffer, fighter.pad(), isPlayer(chunk, i));
                }
            }
        );
    }

    public static void teleportToOut(
        @Nonnull Store<EntityStore> store,
        @Nonnull SnowballSession session,
        @Nonnull Set<UUID> uuids
    ) {
        if (uuids.isEmpty()) {
            return;
        }
        SnowballSession.StartPad out = session.outPad();
        store.forEachChunk(
            fighterQuery(),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !uuids.contains(uc.getUuid())) {
                        continue;
                    }
                    applyPad(chunk.getReferenceTo(i), chunk, i, commandBuffer, out, isPlayer(chunk, i));
                    if (!isPlayer(chunk, i)) {
                        NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                        if (npc != null) {
                            SnowballPin.unpin(chunk.getReferenceTo(i), npc, commandBuffer);
                        }
                    }
                }
            }
        );
    }

    public static void teleportUuidToPad(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID uuid,
        @Nonnull SnowballSession.StartPad pad
    ) {
        store.forEachChunk(
            fighterQuery(),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !uuid.equals(uc.getUuid())) {
                        continue;
                    }
                    applyPad(chunk.getReferenceTo(i), chunk, i, commandBuffer, pad, isPlayer(chunk, i));
                }
            }
        );
    }

    public static void thawVillagerFighters(
        @Nonnull Store<EntityStore> store,
        @Nonnull SnowballSession session
    ) {
        store.forEachChunk(
            fighterQuery(),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !session.isVillagerFighter(uc.getUuid())) {
                        continue;
                    }
                    NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                    if (npc != null) {
                        SnowballPin.unpin(chunk.getReferenceTo(i), npc, commandBuffer);
                    }
                }
            }
        );
    }

    @Nonnull
    private static Query<EntityStore> fighterQuery() {
        return Query.or(
            Query.and(
                Player.getComponentType(),
                PlayerRef.getComponentType(),
                UUIDComponent.getComponentType(),
                TransformComponent.getComponentType()
            ),
            Query.and(
                NPCEntity.getComponentType(),
                UUIDComponent.getComponentType(),
                TransformComponent.getComponentType()
            )
        );
    }

    private static boolean isPlayer(@Nonnull ArchetypeChunk<EntityStore> chunk, int index) {
        return chunk.getComponent(index, Player.getComponentType()) != null
            && chunk.getComponent(index, PlayerRef.getComponentType()) != null;
    }

    private static void applyPad(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull SnowballSession.StartPad pad,
        boolean player
    ) {
        float yawRad = (float) Math.toRadians(pad.yawDegrees());
        Rotation3f rot = new Rotation3f(0f, yawRad, 0f);
        Vector3d dest = new Vector3d(pad.x(), pad.y(), pad.z());
        if (player) {
            commandBuffer.putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(dest, rot));
        } else {
            AetherhavenNpcTeleport.apply(ref, commandBuffer, Teleport.createExact(dest, rot));
        }
        Velocity vel = chunk.getComponent(index, Velocity.getComponentType());
        if (vel != null) {
            vel.setZero();
            commandBuffer.putComponent(ref, Velocity.getComponentType(), vel);
        }
    }
}
