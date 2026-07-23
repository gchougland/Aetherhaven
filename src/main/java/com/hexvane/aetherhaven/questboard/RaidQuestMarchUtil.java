package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Shared raid march leash + role state helpers. */
public final class RaidQuestMarchUtil {
    public static final long MARCH_ADVANCE_INTERVAL_MS = 15_000L;

    private static final double MARCH_ARRIVE_HORIZONTAL = 6.0;
    private static final double MARCH_WAYPOINT_REACHED_HORIZONTAL = 6.0;
    private static final double MARCH_WAYPOINT_STEP = 20.0;

    private RaidQuestMarchUtil() {}

    public static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        TimeModule mod = TimeModule.get();
        if (mod != null) {
            TimeResource tr = store.getResource(mod.getTimeResourceType());
            if (tr != null) {
                return tr.getNow().toEpochMilli();
            }
        }
        return System.currentTimeMillis();
    }

    public static void bootstrapMarch(
        @Nonnull RaidQuestMobBinding binding,
        @Nonnull Vector3d spawnPos,
        @Nonnull Vector3d charterPos,
        long nowMs
    ) {
        binding.setMarchTarget(charterPos);
        Vector3d first = computeNextWaypoint(spawnPos, charterPos);
        binding.setMarchLeash(first);
        binding.setNextMarchAdvanceEpochMs(nowMs + MARCH_ADVANCE_INTERVAL_MS);
        binding.setMarchInitialized(true);
    }

    public static boolean shouldAdvanceMarch(
        @Nonnull Vector3d mobPos,
        @Nonnull RaidQuestMobBinding binding,
        long nowMs
    ) {
        if (!binding.isMarchInitialized()) {
            return true;
        }
        if (nowMs >= binding.getNextMarchAdvanceEpochMs()) {
            return true;
        }
        Vector3d leash = binding.getMarchLeash();
        double dx = mobPos.x - leash.x;
        double dz = mobPos.z - leash.z;
        return dx * dx + dz * dz <= MARCH_WAYPOINT_REACHED_HORIZONTAL * MARCH_WAYPOINT_REACHED_HORIZONTAL;
    }

    @Nonnull
    public static Vector3d computeNextWaypoint(@Nonnull Vector3d from, @Nonnull Vector3d charterPos) {
        double dx = charterPos.x - from.x;
        double dz = charterPos.z - from.z;
        double distHoriz = Math.sqrt(dx * dx + dz * dz);
        if (distHoriz <= MARCH_ARRIVE_HORIZONTAL) {
            return new Vector3d(charterPos);
        }
        double step = Math.min(MARCH_WAYPOINT_STEP, distHoriz);
        return new Vector3d(from.x + dx / distHoriz * step, charterPos.y, from.z + dz / distHoriz * step);
    }

    @Nullable
    public static String resolveMarchState(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return null;
        }
        var helper = npc.getRole().getStateSupport().getStateHelper();
        if (helper.getStateIndex(AetherhavenConstants.NPC_STATE_RAID_MARCH) >= 0) {
            return AetherhavenConstants.NPC_STATE_RAID_MARCH;
        }
        if (helper.getStateIndex("Leash") >= 0) {
            return "Leash";
        }
        if (helper.getStateIndex("ReturnHome") >= 0) {
            return "ReturnHome";
        }
        return null;
    }

    public static boolean isInMarchState(@Nonnull String stateName) {
        return stateName.contains(AetherhavenConstants.NPC_STATE_RAID_MARCH)
            || stateName.startsWith("ReturnHome")
            || stateName.startsWith("Leash");
    }

    public static void ensureMarchMotion(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull RaidQuestMobBinding binding,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Vector3d leash = binding.getMarchLeash();
        npc.setLeashPoint(leash);
        String marchState = resolveMarchState(npc);
        if (marchState != null && npc.getRole() != null) {
            String stateName = npc.getRole().getStateSupport().getStateName();
            if (!isInMarchState(stateName)) {
                npc.getRole().getStateSupport().setState(ref, marchState, null, commandBuffer);
            }
        }
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    public static void applyMarchState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        String marchState = resolveMarchState(npc);
        if (marchState != null && npc.getRole() != null) {
            npc.getRole().getStateSupport().setState(ref, marchState, null, commandBuffer);
        }
    }

    public static void applyMarchState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull Store<EntityStore> store
    ) {
        String marchState = resolveMarchState(npc);
        if (marchState != null && npc.getRole() != null) {
            npc.getRole().getStateSupport().setState(ref, marchState, null, store);
        }
    }

    public static void applyMarchAdvance(
        @Nonnull NPCEntity npc,
        @Nonnull RaidQuestMobBinding binding,
        @Nonnull Vector3d charterPos,
        long nowMs,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref
    ) {
        Vector3d next = computeNextWaypoint(binding.getMarchLeash(), charterPos);
        binding.setMarchLeash(next);
        binding.setNextMarchAdvanceEpochMs(nowMs + MARCH_ADVANCE_INTERVAL_MS);
        npc.setLeashPoint(next);
        applyMarchState(ref, npc, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        commandBuffer.putComponent(ref, RaidQuestMobBinding.getComponentType(), binding);
    }

    public static boolean isEngagedInCombat(@Nonnull String stateName) {
        return stateName.contains("Combat")
            || stateName.contains("Chase")
            || stateName.contains("Search")
            || stateName.contains("Alerted")
            || stateName.contains("Angry")
            || stateName.contains("Flee")
            || stateName.contains("Shoot");
    }
}
