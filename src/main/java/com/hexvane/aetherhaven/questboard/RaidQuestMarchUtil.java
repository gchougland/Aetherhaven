package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerFly;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Shared raid march leash + role state helpers. */
public final class RaidQuestMarchUtil {
    public static final long MARCH_ADVANCE_INTERVAL_MS = 15_000L;

    private static final double CHARTER_STANDOFF_HORIZONTAL = 10.0;
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
        Vector3d first = computeNextWaypoint(spawnPos, charterPos, binding);
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
            return false;
        }
        if (nowMs < binding.getNextMarchAdvanceEpochMs()) {
            return false;
        }
        Vector3d leash = binding.getMarchLeash();
        double dx = mobPos.x - leash.x;
        double dz = mobPos.z - leash.z;
        boolean reachedLeash =
            dx * dx + dz * dz <= MARCH_WAYPOINT_REACHED_HORIZONTAL * MARCH_WAYPOINT_REACHED_HORIZONTAL;
        return reachedLeash;
    }

    @Nonnull
    public static Vector3d computeNextWaypoint(
        @Nonnull Vector3d from,
        @Nonnull Vector3d charterPos,
        @Nonnull RaidQuestMobBinding binding
    ) {
        double dx = charterPos.x - from.x;
        double dz = charterPos.z - from.z;
        double distHoriz = Math.sqrt(dx * dx + dz * dz);
        double waypointY = resolveMarchWaypointY(from, binding);
        if (distHoriz <= CHARTER_STANDOFF_HORIZONTAL) {
            return resolveCharterStandoff(from, charterPos, waypointY);
        }
        double maxStep = distHoriz - CHARTER_STANDOFF_HORIZONTAL;
        double step = Math.min(MARCH_WAYPOINT_STEP, maxStep);
        return new Vector3d(from.x + dx / distHoriz * step, waypointY, from.z + dz / distHoriz * step);
    }

    /** Backward compatible helper for tests and spawn setup. */
    @Nonnull
    public static Vector3d computeNextWaypoint(@Nonnull Vector3d from, @Nonnull Vector3d charterPos) {
        RaidQuestMobBinding binding = new RaidQuestMobBinding(java.util.UUID.randomUUID(), "test");
        return computeNextWaypoint(from, charterPos, binding);
    }

    static double resolveMarchWaypointY(@Nonnull Vector3d from, @Nonnull RaidQuestMobBinding binding) {
        if (binding.hasMarchFlyCruiseY()) {
            return binding.getMarchFlyCruiseY();
        }
        return from.y;
    }

    /** Final march goal: a ring near the charter, not on top of the stone. */
    @Nonnull
    static Vector3d resolveCharterStandoff(@Nonnull Vector3d from, @Nonnull Vector3d charterCenter, double waypointY) {
        double dx = from.x - charterCenter.x;
        double dz = from.z - charterCenter.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) {
            return new Vector3d(
                charterCenter.x + CHARTER_STANDOFF_HORIZONTAL,
                waypointY,
                charterCenter.z
            );
        }
        double scale = CHARTER_STANDOFF_HORIZONTAL / dist;
        return new Vector3d(
            charterCenter.x + dx * scale,
            waypointY,
            charterCenter.z + dz * scale
        );
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
        @Nonnull Vector3d mobPos,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (isEngagedInCombat(npc)) {
            return;
        }
        Vector3d leash = binding.getMarchLeash();
        if (isFlyingNpc(npc)) {
            leash = new Vector3d(leash.x, mobPos.y, leash.z);
        }
        npc.setLeashPoint(leash);
        applyFlyingCruiseAltitude(npc);
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

    /** Moves the march leash to the next waypoint. Mobs walk there; only stuck recovery may teleport. */
    public static void applyMarchAdvance(
        @Nonnull NPCEntity npc,
        @Nonnull RaidQuestMobBinding binding,
        @Nonnull Vector3d charterPos,
        long nowMs,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Vector3d mobPos
    ) {
        Vector3d next = computeNextWaypoint(binding.getMarchLeash(), charterPos, binding);
        binding.setMarchLeash(next);
        binding.setNextMarchAdvanceEpochMs(nowMs + MARCH_ADVANCE_INTERVAL_MS);
        Vector3d leash = next;
        if (isFlyingNpc(npc)) {
            leash = new Vector3d(next.x, mobPos.y, next.z);
        } else if (binding.hasMarchFlyCruiseY()) {
            leash = new Vector3d(next.x, binding.getMarchFlyCruiseY(), next.z);
        }
        npc.setLeashPoint(leash);
        applyFlyingCruiseAltitude(npc);
        applyMarchState(ref, npc, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        commandBuffer.putComponent(ref, RaidQuestMobBinding.getComponentType(), binding);
    }

    public static boolean isEngagedInCombat(@Nonnull NPCEntity npc) {
        if (hasActiveCombatTarget(npc)) {
            return true;
        }
        if (npc.getRole() == null) {
            return false;
        }
        String stateName = npc.getRole().getStateSupport().getStateName();
        if (isInMarchState(stateName) && !isCombatStateName(stateName)) {
            return false;
        }
        return isCombatStateName(stateName);
    }

    public static boolean isEngagedInCombat(@Nonnull String stateName) {
        return isCombatStateName(stateName);
    }

    static boolean isCombatStateName(@Nonnull String stateName) {
        return stateName.contains("Combat")
            || stateName.contains("Chase")
            || stateName.contains("Search")
            || stateName.contains("Alerted")
            || stateName.contains("Angry")
            || stateName.contains("Flee")
            || stateName.contains("Shoot")
            || stateName.contains("Attack")
            || stateName.contains("Investigate")
            || stateName.contains("Warn")
            || stateName.contains("Defend")
            || stateName.contains("Melee")
            || stateName.contains("Ranged");
    }

    static boolean hasActiveCombatTarget(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return false;
        }
        MarkedEntitySupport marked = npc.getRole().getMarkedEntitySupport();
        Ref<EntityStore> target = marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        return target != null && target.isValid();
    }

    public static boolean isFlyingNpc(@Nonnull NPCEntity npc) {
        return MotionControllerFly.TYPE.equals(npc.getActiveMotionControllerName());
    }

    static void applyFlyingCruiseAltitude(@Nonnull NPCEntity npc) {
        if (!isFlyingNpc(npc) || npc.getRole() == null) {
            return;
        }
        var mc = npc.getRole().getActiveMotionController();
        if (mc instanceof MotionControllerFly fly) {
            fly.setDesiredAltitudeOverride(new double[] {8.0, 12.0});
        }
    }
}
