package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.PoiEffectTable;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Orchestrates town-villager POI autonomy in phases. Scoring, registry access, and transform stepping stay here;
 * facing/Status playback for {@code USE} is {@link PoiAutonomyVisuals}; horizontal motion is {@link VillagerPoiMovement}.
 * Role {@code StateTransitions} clear Status when leaving {@link AetherhavenConstants#NPC_STATE_AUTONOMY_POI} for Idle.
 */
public final class VillagerAutonomySystem extends EntityTickingSystem<EntityStore> {
    private static final double REACH_SQ = 1.45 * 1.45;
    private static final double MOVE_BLOCKS_PER_SEC = 3.2;

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
        String stateName = npc.getRole().getStateSupport().getStateName();
        if (stateName.contains("Interaction")) {
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
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), VillagerAutonomyState.fresh(now));
            return;
        }

        World world = store.getExternalData().getWorld();
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        List<PoiEntry> pois = reg.listByTown(binding.getTownId());

        switch (autonomy.getPhase()) {
            case VillagerAutonomyState.PHASE_USE -> tickUse(ref, store, commandBuffer, npc, reg, needs, autonomy, now);
            case VillagerAutonomyState.PHASE_TRAVEL -> tickTravel(ref, store, commandBuffer, npc, reg, needs, autonomy, dt, now);
            default -> tickIdle(ref, store, commandBuffer, world, npc, pois, needs, binding, autonomy, now);
        }
    }

    private static void tickIdle(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull List<PoiEntry> pois,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        long now
    ) {
        if (now < autonomy.getNextDecisionEpochMs()) {
            return;
        }
        PoiEntry pick = PoiScoring.pickBest(pois, needs, binding);
        if (pick == null) {
            autonomy.setNextDecisionEpochMs(now + 4000L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            return;
        }
        autonomy.setPhase(VillagerAutonomyState.PHASE_TRAVEL);
        autonomy.setTravelTarget(pick.getX() + 0.5, pick.getY(), pick.getZ() + 0.5, pick.getId());
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            String path = VillagerPoiPathfinder.findPath(world, tc.getPosition(), pick);
            autonomy.setTravelPath(path != null ? path : "");
        } else {
            autonomy.setTravelPath("");
        }
        autonomy.setNextDecisionEpochMs(now + 120_000L);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static void tickTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull VillagerNeeds needs,
        @Nonnull VillagerAutonomyState autonomy,
        float dt,
        long now
    ) {
        UUID poiId = autonomy.getTargetPoiUuid();
        PoiEntry poi = poiId != null ? reg.get(poiId) : null;
        if (poi == null) {
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
            autonomy.setTravelPath("");
            autonomy.setNextDecisionEpochMs(now + 2000L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            clearAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        if (store.getComponent(ref, TransformComponent.getComponentType()) == null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        String tp = autonomy.getTravelPath();
        int wpCount = waypointCount(tp);
        int wpIdx = autonomy.getTravelPathIndex();
        double aimX = autonomy.getTargetX();
        double aimZ = autonomy.getTargetZ();
        double aimY = autonomy.getTargetY();
        if (wpCount > 0 && wpIdx < wpCount) {
            double[] xz = new double[2];
            if (parseWaypointCenter(tp, wpIdx, xz)) {
                aimX = xz[0];
                aimZ = xz[1];
                int bx = (int) Math.floor(aimX);
                int bz = (int) Math.floor(aimZ);
                int sy = VillagerPoiPathfinder.findStandY(world, bx, bz, (int) Math.floor(aimY) + 3);
                if (sy != Integer.MIN_VALUE) {
                    aimY = sy;
                }
            }
        }
        double reachSq = wpCount > 0 && wpIdx < wpCount ? 0.42 * 0.42 : REACH_SQ;
        boolean arrived = VillagerPoiMovement.stepHorizontalToward(
            ref,
            store,
            commandBuffer,
            aimX,
            aimY,
            aimZ,
            world,
            MOVE_BLOCKS_PER_SEC,
            dt,
            reachSq
        );
        if (!arrived) {
            npc.playAnimation(ref, AnimationSlot.Movement, "Walk", store);
        }
        if (arrived) {
            if (wpCount > 0 && wpIdx < wpCount) {
                autonomy.setTravelPathIndex(wpIdx + 1);
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                applyAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
            npc.playAnimation(ref, AnimationSlot.Movement, null, store);
            float dur = PoiEffectTable.useDurationSeconds(poi.getInteractionKind());
            autonomy.setPhase(VillagerAutonomyState.PHASE_USE);
            autonomy.setPhaseEndEpochMs(now + (long) (dur * 1000L));
            autonomy.setTravelPath("");
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            PoiAutonomyVisuals.beginPoiUse(ref, store, commandBuffer, poi);
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        applyAutonomyRoleState(ref, npc, commandBuffer);
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
        autonomy.setTravelPath("");
        autonomy.setNextDecisionEpochMs(now + 2500L);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        clearAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static int waypointCount(@Nonnull String tp) {
        if (tp.isBlank()) {
            return 0;
        }
        int c = 0;
        for (String s : tp.split(";")) {
            if (!s.isBlank()) {
                c++;
            }
        }
        return c;
    }

    private static boolean parseWaypointCenter(@Nonnull String tp, int logicalIndex, @Nonnull double[] outXZ) {
        int i = 0;
        for (String seg : tp.split(";")) {
            if (seg.isBlank()) {
                continue;
            }
            if (i == logicalIndex) {
                String[] xz = seg.split(",");
                if (xz.length < 2) {
                    return false;
                }
                outXZ[0] = Integer.parseInt(xz[0].trim()) + 0.5;
                outXZ[1] = Integer.parseInt(xz[1].trim()) + 0.5;
                return true;
            }
            i++;
        }
        return false;
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

    private static void applyAutonomyRoleState(
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
