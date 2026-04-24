package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiOccupancy;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.schedule.VillagerScheduleService;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTickState;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Starts autonomy travel toward the best POI in the current schedule plot immediately (used after Gaia revival so NPCs
 * do not idle-wander at the statue).
 */
public final class VillagerAutonomyTravelKick {
    private VillagerAutonomyTravelKick() {}

    /**
     * Applies weekly schedule (time-jump) then begins travel toward a scored POI for the resident with {@code npcUuid},
     * if any POI is available. Must run on the world/store thread (use inside {@link World#execute}).
     */
    public static void kickTravelToSchedulePoi(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID npcUuid
    ) {
        kickTravelToSchedulePoi(plugin, world, store, npcUuid, true);
    }

    /**
     * @param applyScheduleForWorld if true, runs {@link VillagerScheduleService#applyForWorld} first (time-jump). Set
     *     false when the caller already applied the schedule for this store/world in the same flow (e.g. Gaia revival).
     */
    public static void kickTravelToSchedulePoi(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID npcUuid,
        boolean applyScheduleForWorld
    ) {
        if (applyScheduleForWorld) {
            VillagerScheduleService.applyForWorld(world, store, plugin, true);
        }
        store.forEachChunk(
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                int n = chunk.size();
                for (int i = 0; i < n; i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !npcUuid.equals(uc.getUuid())) {
                        continue;
                    }
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    VillagerNeeds needs = chunk.getComponent(i, VillagerNeeds.getComponentType());
                    NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                    if (binding == null || needs == null || npc == null || npc.getRole() == null) {
                        return true;
                    }
                    if (TownVillagerBinding.isVisitorKind(binding.getKind())) {
                        return true;
                    }
                    long now = resolveNowMs(store);
                    VillagerAutonomyState autonomy = chunk.getComponent(i, VillagerAutonomyState.getComponentType());
                    if (autonomy == null) {
                        autonomy = VillagerAutonomyState.fresh(now);
                    }
                    if (SchedulePlotCommute.tryBeginIfOffSchedulePlot(ref, store, commandBuffer, world, npc, binding, autonomy, now, plugin)) {
                        return true;
                    }
                    PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
                    List<PoiEntry> pois = reg.listByTown(binding.getTownId());
                    Map<String, Integer> cellOcc = PoiOccupancy.cellOccupancyForTown(world, binding.getTownId(), store, reg);
                    TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                    double npcX = tc != null ? tc.getPosition().x : Double.NaN;
                    double npcZ = tc != null ? tc.getPosition().z : Double.NaN;
                    VillagerScheduleTickState schedTick =
                        chunk.getComponent(i, VillagerScheduleTickState.getComponentType());
                    String scheduleSeg = schedTick != null ? schedTick.getLastAppliedScheduleSegment() : null;
                    PoiEntry pick = PoiScoring.pickBest(pois, needs, binding, cellOcc, npcX, npcZ, scheduleSeg);
                    if (pick == null) {
                        return true;
                    }
                    TownManager tmm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                    TownRecord townRow = tmm.getTown(binding.getTownId());
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
                            return true;
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
                                    townRow,
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
                    npc.getRole().getStateSupport().setState(ref, AetherhavenConstants.NPC_STATE_AUTONOMY_POI, null, commandBuffer);
                    commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
                    return true;
                }
                return false;
            }
        );
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
}
