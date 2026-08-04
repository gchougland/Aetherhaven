package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.AutonomyStuckTeleportRecovery;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Teleports stalled raid mobs to their current march waypoint when out of combat. */
public final class RaidQuestMarchStuckRecovery {
    private RaidQuestMarchStuckRecovery() {}

    /**
     * @return {@code true} if a recovery teleport was applied this tick
     */
    public static boolean tryRecoverIfStalled(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull RaidQuestMobBinding binding,
        @Nonnull Vector3d mobPos,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID mobUuid
    ) {
        Vector3d leash = binding.getMarchLeash();
        AutonomyStuckTeleportRecovery.updateStall(binding, mobPos, leash.x, leash.z);
        if (!AutonomyStuckTeleportRecovery.isStallTeleportDue(binding)) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        Vector3d spread = RaidQuestMarchSpread.offsetAround(leash, mobUuid);
        Vector3d target;
        if (RaidQuestMarchUtil.isFlyingNpc(npc)) {
            double flyY = binding.hasMarchFlyCruiseY() ? binding.getMarchFlyCruiseY() : mobPos.y;
            target = new Vector3d(spread.x, flyY, spread.z);
        } else {
            spread.y = mobPos.y;
            target = VillagerBlockUtil.snapNpcFeetToStand(world, spread);
        }
        int stallTicks = binding.getAutonomyStallTicks();
        AutonomyStuckTeleportRecovery.teleportNpc(ref, commandBuffer, store, target, npc);
        npc.setLeashPoint(leash);
        RaidQuestMarchUtil.applyMarchState(ref, npc, commandBuffer);
        AutonomyStuckTeleportRecovery.resetAfterRecovery(binding);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        RaidQuestMarchDebugLog.logStuckTeleport(plugin, mobUuid, mobPos, target, leash, stallTicks);
        return true;
    }
}
