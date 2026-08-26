package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.npc.NpcSupportUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.rts.GuardRtsCommandState;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.rts.RtsGuardCombatSupport;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Drives hired guards along assigned patrol routes using Seek and leash points. */
public final class GuardPatrolSystem extends EntityTickingSystem<EntityStore> {
    private static final double ARRIVE_HORIZONTAL_SQ = 1.5 * 1.5;
    private static final double PATROL_COMBAT_LEASH_HORIZONTAL_SQ = 20.0 * 20.0;
    private static final long PAUSE_MIN_MS = 2000L;
    private static final long PAUSE_MAX_MS = 4000L;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public GuardPatrolSystem(@Nonnull AetherhavenPlugin plugin) {
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
        return Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TownVillagerBinding binding = chunk.getComponent(index, TownVillagerBinding.getComponentType());
        if (binding == null || !TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
            return;
        }
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }
        UUIDComponent uc = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        if (com.hexvane.aetherhaven.festival.snowball.SnowballSessionIndex.isLivingFighter(uc.getUuid())) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (chunk.getComponent(index, GuardRtsCommandState.getComponentType()) != null) {
            return;
        }
        if (GuardFollowPlayerSystem.shouldSkipPatrol(chunk.getComponent(index, GuardFollowPlayerState.getComponentType()))) {
            return;
        }
        World world = store.getExternalData().getWorld();
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        List<PatrolRouteRecord> assigned = reg.routesForGuard(uc.getUuid());
        String stateName = NpcSupportUtil.stateName(store, ref);
        if (stateName.contains("Combat") && !assigned.isEmpty()) {
            GuardPatrolState combatPatrol = chunk.getComponent(index, GuardPatrolState.getComponentType());
            TransformComponent tcCombat = store.getComponent(ref, TransformComponent.getComponentType());
            if (tcCombat != null) {
                PatrolRouteRecord leashRoute = pickActiveRoute(assigned, combatPatrol != null ? combatPatrol : new GuardPatrolState());
                if (leashRoute != null
                    && PatrolRouteGeometry.minHorizontalDistanceSqToRoute(tcCombat.getPosition(), leashRoute)
                        > PATROL_COMBAT_LEASH_HORIZONTAL_SQ) {
                    abortPatrolCombat(ref, npc, commandBuffer);
                }
            }
            return;
        }
        if (stateName.contains("Combat") || stateName.contains("Interaction")) {
            return;
        }
        if (assigned.isEmpty()) {
            if (stateName.contains("Patrol")) {
                NpcSupportUtil.setState(ref, "Idle", null, commandBuffer);
                commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            }
            return;
        }
        GuardPatrolState patrol = chunk.getComponent(index, GuardPatrolState.getComponentType());
        if (patrol == null) {
            patrol = new GuardPatrolState();
            commandBuffer.addComponent(ref, GuardPatrolState.getComponentType(), patrol);
        }
        long now = resolveNowMs(store);
        PatrolRouteRecord route = pickActiveRoute(assigned, patrol);
        if (route == null || route.nodes == null || route.nodes.size() < 2) {
            return;
        }
        UUID routeId = route.getIdUuid();
        if (routeId != null && !routeId.equals(patrol.getActiveRouteId())) {
            patrol.setActiveRouteId(routeId);
            patrol.resetProgress();
        }
        if (now < patrol.getPauseUntilMs()) {
            ensurePatrolState(ref, npc, commandBuffer);
            return;
        }
        int nodeCount = route.nodes.size();
        int idx = Math.min(Math.max(0, patrol.getNodeIndex()), nodeCount - 1);
        PatrolRouteNode node = route.nodes.get(idx);
        if (node == null) {
            patrol.setNodeIndex(0);
            patrol.setForward(true);
            commandBuffer.putComponent(ref, GuardPatrolState.getComponentType(), patrol);
            return;
        }
        Vector3d target = new Vector3d(node.x, node.y, node.z);
        npc.setLeashPoint(target);
        ensurePatrolState(ref, npc, commandBuffer);
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d pos = tc.getPosition();
        double dx = pos.x - target.x;
        double dz = pos.z - target.z;
        if (dx * dx + dz * dz <= ARRIVE_HORIZONTAL_SQ) {
            AdvanceResult next = advanceNodeIndex(idx, nodeCount, route.isClosedLoop(), patrol.isForward(), assigned.size());
            patrol.setNodeIndex(next.nodeIndex());
            patrol.setForward(next.forward());
            if (next.advanceRouteSlot()) {
                patrol.setRouteSlot((patrol.getRouteSlot() + 1) % assigned.size());
            }
            long pause = PAUSE_MIN_MS + ThreadLocalRandom.current().nextLong(PAUSE_MAX_MS - PAUSE_MIN_MS + 1);
            patrol.setPauseUntilMs(now + pause);
        }
        commandBuffer.putComponent(ref, GuardPatrolState.getComponentType(), patrol);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private record AdvanceResult(int nodeIndex, boolean forward, boolean advanceRouteSlot) {}

    @Nonnull
    private static AdvanceResult advanceNodeIndex(
        int idx,
        int nodeCount,
        boolean closedLoop,
        boolean forward,
        int assignedRouteCount
    ) {
        if (closedLoop) {
            int nextIdx = (idx + 1) % nodeCount;
            return new AdvanceResult(nextIdx, true, nextIdx == 0 && assignedRouteCount > 1);
        }
        if (forward) {
            if (idx >= nodeCount - 1) {
                return new AdvanceResult(Math.max(0, nodeCount - 2), false, false);
            }
            return new AdvanceResult(idx + 1, true, false);
        }
        if (idx <= 0) {
            int nextIdx = Math.min(1, nodeCount - 1);
            return new AdvanceResult(nextIdx, true, assignedRouteCount > 1);
        }
        return new AdvanceResult(idx - 1, false, false);
    }

    @Nullable
    private static PatrolRouteRecord pickActiveRoute(
        @Nonnull List<PatrolRouteRecord> assigned,
        @Nonnull GuardPatrolState patrol
    ) {
        if (assigned.isEmpty()) {
            return null;
        }
        if (assigned.size() == 1) {
            return assigned.get(0);
        }
        int slot = patrol.getRouteSlot() % assigned.size();
        return assigned.get(slot);
    }

    private static void abortPatrolCombat(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        RtsGuardCombatSupport.clearCombatTarget(npc, commandBuffer);
        NpcSupportUtil.setState(ref, AetherhavenConstants.NPC_STATE_GUARD_PATROL, null, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private static void ensurePatrolState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        String stateName = NpcSupportUtil.stateName(commandBuffer.getStore(), ref);
        if (!stateName.contains("Patrol")) {
            NpcSupportUtil.setState(ref, AetherhavenConstants.NPC_STATE_GUARD_PATROL, null, commandBuffer);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        }
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

    /** Clears patrol routes pointing at a dead or removed guard entity. */
    public static void clearAssignmentsForGuard(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID guardEntityUuid
    ) {
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        reg.clearGuardAssignment(guardEntityUuid);
        PatrolRoutePersistence.save(world, plugin, reg);
    }
}
