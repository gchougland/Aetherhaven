package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.PoiEffectTable;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiOccupancy;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTickState;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.ArrayList;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.NavState;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * POI autonomy: idle pick + leash target, travel via vanilla {@code Seek} (role JSON), use + visuals.
 * Role {@code StateTransitions} clear Status when leaving {@link AetherhavenConstants#NPC_STATE_AUTONOMY_POI} for Idle.
 */
public final class VillagerAutonomySystem extends EntityTickingSystem<EntityStore> {
    private static final double ARRIVE_HORIZONTAL_SQ = 1.55 * 1.55;
    /** Tighter leash arrival for SIT/SLEEP when there is no interaction target (leash is the POI block). */
    private static final double MOUNT_ARRIVE_HORIZONTAL_SQ = 0.88 * 0.88;
    /**
     * Horizontal distance to the entry leash (interaction target) that counts as "reached the POI entry". Seek often
     * stops 1–3 blocks short of the leash; we do not require walking to the bed after that when an interaction target
     * is set.
     */
    private static final double POI_ENTRY_ARRIVE_HORIZONTAL_SQ = 3.5 * 3.5;
    /**
     * {@link NavState#BLOCKED} and {@link NavState#DEFER}: DEFER is “delay path recompute” in vanilla Seek — it can
     * persist while an NPC is wedged against geometry (wall bug), and must not reset stuck ticks like PROGRESSING.
     */
    private static final int BLOCKED_FAIL_TICKS = 100;
    private static final int MOUNT_UNREACHABLE_FAIL_TICKS = 120;
    static void onUnloadSafetyDismount(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable NPCEntity npc,
        @Nullable VillagerAutonomyState autonomy,
        @Nullable VillagerNeeds needs,
        @Nonnull PoiRegistry reg,
        long now
    ) {
        if (autonomy != null && autonomy.getPhase() == VillagerAutonomyState.PHASE_USE) {
            UUID poiId = autonomy.getTargetPoiUuid();
            PoiEntry poi = poiId != null ? reg.get(poiId) : null;
            if (poi != null) {
                PoiAutonomyVisuals.cleanupAfterPoiUse(ref, store, commandBuffer, poi);
                if (needs != null) {
                    PoiEffectTable.applyUseComplete(needs, poi);
                    commandBuffer.putComponent(ref, VillagerNeeds.getComponentType(), needs);
                }
            }
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
            autonomy.setTargetPoiUuid(null);
            autonomy.setPathFailureReason("");
            autonomy.setTravelStuckTicks(0);
            autonomy.clearPendingDoorClose();
            autonomy.setNextDecisionEpochMs(now + 2500L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            if (npc != null) {
                clearAutonomyRoleState(ref, npc, commandBuffer);
            }
        }
        commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
    }

    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public VillagerAutonomySystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            TownVillagerBinding.getComponentType(),
            VillagerNeeds.getComponentType(),
            NPCEntity.getComponentType()
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
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }

        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        VillagerNeeds needs = archetypeChunk.getComponent(index, VillagerNeeds.getComponentType());
        if (binding == null || needs == null) {
            return;
        }

        long now = resolveNowMs(store);
        VillagerAutonomyState autonomy = archetypeChunk.getComponent(index, VillagerAutonomyState.getComponentType());
        if (autonomy == null) {
            autonomy = VillagerAutonomyState.fresh(now);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            applyAutonomyDebugOverlay(ref, store, commandBuffer, npc, autonomy);
            return;
        }

        applyAutonomyDebugOverlay(ref, store, commandBuffer, npc, autonomy);

        String stateName = npc.getRole().getStateSupport().getStateName();
        if (stateName.contains("Interaction")) {
            return;
        }

        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord townRecord = tm.getTown(binding.getTownId());
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        List<PoiEntry> pois = reg.listByTown(binding.getTownId());
        List<PoiEntry> poisForScoring = filterPoisForAutonomyScoring(townRecord, pois);

        switch (autonomy.getPhase()) {
            case VillagerAutonomyState.PHASE_USE -> tickUse(ref, store, commandBuffer, npc, reg, needs, autonomy, now);
            case VillagerAutonomyState.PHASE_TRAVEL -> tickTravel(ref, store, commandBuffer, npc, reg, autonomy, now);
            default ->
                tickIdle(
                    ref,
                    store,
                    commandBuffer,
                    world,
                    npc,
                    reg,
                    poisForScoring,
                    needs,
                    binding,
                    autonomy,
                    now,
                    this.plugin,
                    townRecord
                );
        }
    }

    @Nonnull
    private static List<PoiEntry> filterPoisForAutonomyScoring(@Nullable TownRecord town, @Nonnull List<PoiEntry> pois) {
        if (town == null) {
            List<PoiEntry> out = new ArrayList<>();
            for (PoiEntry e : pois) {
                if (!e.getTags().contains(AetherhavenConstants.POI_TAG_FEAST_EPHEMERAL)) {
                    out.add(e);
                }
            }
            return out;
        }
        UUID allow = null;
        String gid = town.getFeastGatherPoiId();
        if (gid != null && !gid.isBlank()) {
            try {
                allow = UUID.fromString(gid.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<PoiEntry> out = new ArrayList<>();
        for (PoiEntry e : pois) {
            if (e.getTags().contains(AetherhavenConstants.POI_TAG_FEAST_EPHEMERAL)) {
                if (allow != null && allow.equals(e.getId())) {
                    out.add(e);
                }
            } else {
                out.add(e);
            }
        }
        return out;
    }

    private static void tickIdle(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull List<PoiEntry> pois,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord
    ) {
        if (SchedulePlotCommute.tryBeginIfOffSchedulePlot(ref, store, commandBuffer, world, npc, binding, autonomy, now, plugin)) {
            return;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tryBeginFeastGatherTravel(ref, store, commandBuffer, world, npc, reg, binding, autonomy, townRecord, now, plugin, tc)) {
            return;
        }
        if (now < autonomy.getNextDecisionEpochMs()) {
            return;
        }
        Map<String, Integer> cellOcc = PoiOccupancy.cellOccupancyForTown(world, binding.getTownId(), store, reg);
        double npcX = tc != null ? tc.getPosition().x : Double.NaN;
        double npcZ = tc != null ? tc.getPosition().z : Double.NaN;
        VillagerScheduleTickState schedTick = store.getComponent(ref, VillagerScheduleTickState.getComponentType());
        String scheduleSeg = schedTick != null ? schedTick.getLastAppliedScheduleSegment() : null;
        PoiEntry pick = PoiScoring.pickBest(pois, needs, binding, cellOcc, npcX, npcZ, scheduleSeg);
        if (pick == null) {
            autonomy.setNextDecisionEpochMs(now + 4000L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            return;
        }
        beginTravelToPoi(ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, tc, pick);
    }

    /**
     * When a feast is active, residents in {@link VillagerAutonomyState#PHASE_IDLE} path to the ephemeral feast POI
     * immediately (bypasses {@link VillagerAutonomyState#getNextDecisionEpochMs}).
     */
    private static boolean tryBeginFeastGatherTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        @Nullable TownRecord townRecord,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TransformComponent tc
    ) {
        if (townRecord == null || TownVillagerBinding.isVisitorKind(binding.getKind())) {
            return false;
        }
        if (autonomy.getPhase() != VillagerAutonomyState.PHASE_IDLE) {
            return false;
        }
        String gid = townRecord.getFeastGatherPoiId();
        if (gid == null || gid.isBlank()) {
            return false;
        }
        UUID pid;
        try {
            pid = UUID.fromString(gid.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (pid.equals(autonomy.getTargetPoiUuid())) {
            return false;
        }
        PoiEntry feastPoi = reg.get(pid);
        if (feastPoi == null) {
            return false;
        }
        beginTravelToPoi(ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, tc, feastPoi);
        return true;
    }

    private static void beginTravelToPoi(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord,
        @Nullable TransformComponent tc,
        @Nonnull PoiEntry pick
    ) {
        autonomy.setPhase(VillagerAutonomyState.PHASE_TRAVEL);
        double tx;
        double tz;
        double leashY;
        if (pick.hasInteractionTarget()) {
            Double tpx = pick.getInteractionTargetX();
            Double tpy = pick.getInteractionTargetY();
            Double tpz = pick.getInteractionTargetZ();
            if (tpx == null || tpy == null || tpz == null) {
                autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                autonomy.setNextDecisionEpochMs(now + 4000L);
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                return;
            }
            tx = tpx;
            tz = tpz;
            leashY = tpy;
        } else {
            int bx = pick.getX();
            int bz = pick.getZ();
            tx = bx + 0.5;
            tz = bz + 0.5;
            int standY =
                tc != null
                    ? AutonomyNavBounds.standBlockYForPoiWithoutTarget(
                        world,
                        plugin,
                        townRecord,
                        pick,
                        bx,
                        bz,
                        (int) Math.floor(tc.getPosition().y)
                    )
                    : Integer.MIN_VALUE;
            leashY = standY != Integer.MIN_VALUE ? standY + 0.02 : pick.getY();
        }
        autonomy.setTravelTarget(tx, leashY, tz, pick.getId());
        autonomy.setPathFailureReason("");
        autonomy.setTravelStuckTicks(0);
        npc.setLeashPoint(new Vector3d(tx, leashY, tz));
        autonomy.setNextDecisionEpochMs(now + 120_000L);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static void tickTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull VillagerAutonomyState autonomy,
        long now
    ) {
        UUID poiId = autonomy.getTargetPoiUuid();
        if (poiId == null) {
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
            autonomy.setPathFailureReason("");
            autonomy.setTravelStuckTicks(0);
            autonomy.setNextDecisionEpochMs(now + 2000L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            clearAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }

        Vector3d pos = tc.getPosition();
        Vector3d leash = npc.getLeashPoint();
        double dx = pos.x - leash.x;
        double dz = pos.z - leash.z;
        double horizSq = dx * dx + dz * dz;

        PoiEntry poiEarly = reg.get(poiId);
        double maxArriveSq = ARRIVE_HORIZONTAL_SQ;
        boolean mountKind =
            poiEarly != null
                && (poiEarly.getInteractionKind() == PoiInteractionKind.SIT
                    || poiEarly.getInteractionKind() == PoiInteractionKind.SLEEP);
        if (mountKind && poiEarly != null && poiEarly.hasInteractionTarget()) {
            maxArriveSq = POI_ENTRY_ARRIVE_HORIZONTAL_SQ;
        } else if (mountKind) {
            maxArriveSq = MOUNT_ARRIVE_HORIZONTAL_SQ;
        }

        World world = store.getExternalData().getWorld();

        VillagerDoorUtil.closePendingDoorsWhenPassed(world, pos, leash, autonomy.getPendingOpenDoorsMutable());
        VillagerDoorUtil.tryOpenDoorsTowardLeash(
            world,
            pos,
            leash,
            (x, y, z) -> autonomy.addPendingDoorOpened(x, y, z)
        );

        boolean mountLimbo =
            mountKind
                && horizSq <= maxArriveSq
                && poiEarly != null
                && !poiEarly.hasInteractionTarget()
                && !VillagerBlockUtil.canNpcMountBlockPoi(world, pos.x, pos.y, pos.z, poiEarly.getX(), poiEarly.getY(), poiEarly.getZ());

        NavState nav = NavState.INIT;
        MotionController mc = npc.getRole() != null ? npc.getRole().getActiveMotionController() : null;
        if (mc != null) {
            nav = mc.getNavState();
        }

        if (nav == NavState.ABORTED) {
            failTravel(autonomy, now, "NO_PATH", commandBuffer, ref, npc);
            return;
        }

        if (mountLimbo) {
            autonomy.setTravelStuckTicks(autonomy.getTravelStuckTicks() + 1);
            if (autonomy.getTravelStuckTicks() >= MOUNT_UNREACHABLE_FAIL_TICKS) {
                failTravel(autonomy, now, "MOUNT_UNREACHABLE", commandBuffer, ref, npc);
                return;
            }
        } else if (nav == NavState.BLOCKED || nav == NavState.DEFER) {
            autonomy.setTravelStuckTicks(autonomy.getTravelStuckTicks() + 1);
            if (autonomy.getTravelStuckTicks() >= BLOCKED_FAIL_TICKS) {
                failTravel(autonomy, now, nav == NavState.DEFER ? "DEFER" : "BLOCKED", commandBuffer, ref, npc);
                return;
            }
        } else if (nav == NavState.PROGRESSING || nav == NavState.INIT) {
            autonomy.setTravelStuckTicks(0);
        }

        boolean arrived = horizSq <= maxArriveSq;
        if (arrived) {
            if (AetherhavenConstants.isScheduleZoneCommutePoi(poiId)) {
                autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                autonomy.setTargetPoiUuid(null);
                autonomy.setPathFailureReason("");
                autonomy.setTravelStuckTicks(0);
                autonomy.clearPendingDoorClose();
                autonomy.setNextDecisionEpochMs(now);
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                clearAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
            PoiEntry poi = poiEarly;
            if (poi == null) {
                failTravel(autonomy, now, "POI_GONE", commandBuffer, ref, npc);
                return;
            }
            if (mountKind
                && !poi.hasInteractionTarget()
                && !VillagerBlockUtil.canNpcMountBlockPoi(world, pos.x, pos.y, pos.z, poi.getX(), poi.getY(), poi.getZ())) {
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                applyAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
            autonomy.setTravelStuckTicks(0);
            npc.playAnimation(ref, AnimationSlot.Movement, null, store);
            float dur = PoiEffectTable.useDurationSeconds(poi.getInteractionKind());
            autonomy.setPhase(VillagerAutonomyState.PHASE_USE);
            autonomy.setPhaseEndEpochMs(now + (long) (dur * 1000L));
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            PoiAutonomyVisuals.beginPoiUse(ref, store, commandBuffer, poi);
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static void failTravel(
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull String reason,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc
    ) {
        autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
        autonomy.setTargetPoiUuid(null);
        autonomy.setPathFailureReason(reason);
        autonomy.setTravelStuckTicks(0);
        autonomy.clearPendingDoorClose();
        autonomy.setNextDecisionEpochMs(now + 5000L);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        clearAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static void tickUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull VillagerNeeds needs,
        @Nonnull VillagerAutonomyState autonomy,
        long now
    ) {
        if (now < autonomy.getPhaseEndEpochMs()) {
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }
        UUID poiId = autonomy.getTargetPoiUuid();
        PoiEntry poi = poiId != null ? reg.get(poiId) : null;
        if (poi != null) {
            PoiAutonomyVisuals.cleanupAfterPoiUse(ref, store, commandBuffer, poi);
            PoiEffectTable.applyUseComplete(needs, poi);
            commandBuffer.putComponent(ref, VillagerNeeds.getComponentType(), needs);
        }
        autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
        autonomy.setTargetPoiUuid(null);
        autonomy.setPathFailureReason("");
        autonomy.setTravelStuckTicks(0);
        autonomy.setNextDecisionEpochMs(now + 2500L);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        clearAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static void applyAutonomyDebugOverlay(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerAutonomyState autonomy
    ) {
        if (npc.getRole() == null) {
            return;
        }
        boolean showAhDebug = store.getComponent(ref, VillagerAutonomyDebugTag.getComponentType()) != null;
        if (!showAhDebug) {
            VillagerAutonomyDebug.clearAutonomyDebugForNpc(ref, commandBuffer, npc);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            return;
        }
        VillagerAutonomyDebug.ensureAutonomyDebugRoleFlags(npc);
        StringBuilder sb = new StringBuilder();
        sb.append("AH ");
        sb.append(switch (autonomy.getPhase()) {
            case VillagerAutonomyState.PHASE_TRAVEL -> "TR";
            case VillagerAutonomyState.PHASE_USE -> "USE";
            default -> "IDLE";
        });
        UUID tgtPoi = autonomy.getTargetPoiUuid();
        if (tgtPoi != null) {
            String u = tgtPoi.toString();
            sb.append(" POI:").append(u, 0, Math.min(8, u.length()));
        }
        if (!autonomy.getPathFailureReason().isEmpty()) {
            sb.append(" FAIL:").append(autonomy.getPathFailureReason());
        }
        Vector3d leash = npc.getLeashPoint();
        sb.append(" L:").append((int) leash.x).append(',').append((int) leash.z);
        NavState nav = NavState.INIT;
        MotionController mc = npc.getRole().getActiveMotionController();
        if (mc != null) {
            nav = mc.getNavState();
        }
        sb.append(" NAV:").append(nav);
        npc.getRole().getDebugSupport().setDisplayCustomString(sb.toString());
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        TimeModule mod = TimeModule.get();
        if (mod != null) {
            TimeResource tr = store.getResource(mod.getTimeResourceType());
            if (tr != null) {
                return tr.getNow().toEpochMilli();
            }
        }
        return System.currentTimeMillis();
    }

    static void applyAutonomyRoleState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null) {
            return;
        }
        npc.getRole().getStateSupport().setState(ref, AetherhavenConstants.NPC_STATE_AUTONOMY_POI, null, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private static void clearAutonomyRoleState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null) {
            return;
        }
        String state = npc.getRole().getStateSupport().getStateName();
        if (!state.startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI)) {
            return;
        }
        npc.getRole().getStateSupport().setState(ref, "Idle", null, commandBuffer);
        npc.playAnimation(ref, AnimationSlot.Action, null, commandBuffer);
        npc.playAnimation(ref, AnimationSlot.Emote, null, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }
}
