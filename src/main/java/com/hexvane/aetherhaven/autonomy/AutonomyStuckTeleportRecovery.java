package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import com.hexvane.aetherhaven.tourist.TouristDestinationResolver;
import com.hexvane.aetherhaven.tourist.TouristPlotVisit;
import com.hexvane.aetherhaven.tourist.TouristPortalBlockUtil;
import com.hexvane.aetherhaven.tourist.TouristPortalRecord;
import com.hexvane.aetherhaven.tourist.TouristPortalRegistry;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hexvane.aetherhaven.villager.AetherhavenNpcTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Position-stall detection and teleport-to-destination recovery for autonomy NPCs. */
public final class AutonomyStuckTeleportRecovery {
    private AutonomyStuckTeleportRecovery() {}

    public record TouristRecoveryTarget(@Nonnull Vector3d position, boolean portalReturn) {}

    public static void updateStall(@Nonnull AutonomyStallTrackable state, @Nonnull Vector3d pos) {
        updateStall(state, pos, Double.NaN, Double.NaN);
    }

    /**
     * Tracks lack of progress using a stall anchor and optional leash goal. Small jitter inside a tight area does not
     * reset the counter; only net displacement beyond {@link AetherhavenConstants#AUTONOMY_STALL_ANCHOR_RADIUS} or
     * meaningful progress toward the goal does.
     */
    public static void updateStall(
        @Nonnull AutonomyStallTrackable state,
        @Nonnull Vector3d pos,
        double goalX,
        double goalZ
    ) {
        double ax = state.getAutonomyAnchorX();
        double az = state.getAutonomyAnchorZ();
        if (!Double.isFinite(ax) || !Double.isFinite(az)) {
            state.setAutonomyAnchorPosition(pos.x, pos.z);
            state.setAutonomySamplePosition(pos.x, pos.z);
            if (Double.isFinite(goalX) && Double.isFinite(goalZ)) {
                double gdx = pos.x - goalX;
                double gdz = pos.z - goalZ;
                state.setAutonomyGoalDistSq(gdx * gdx + gdz * gdz);
            }
            state.setAutonomyStallTicks(0);
            return;
        }

        double anchorDx = pos.x - ax;
        double anchorDz = pos.z - az;
        double anchorRadius = AetherhavenConstants.AUTONOMY_STALL_ANCHOR_RADIUS;
        boolean madeAnchorProgress =
            anchorDx * anchorDx + anchorDz * anchorDz >= anchorRadius * anchorRadius;

        boolean madeGoalProgress = false;
        if (Double.isFinite(goalX) && Double.isFinite(goalZ)) {
            double gdx = pos.x - goalX;
            double gdz = pos.z - goalZ;
            double goalDistSq = gdx * gdx + gdz * gdz;
            double prevGoalDistSq = state.getAutonomyGoalDistSq();
            if (Double.isFinite(prevGoalDistSq)) {
                double prevDist = Math.sqrt(prevGoalDistSq);
                double newDist = Math.sqrt(goalDistSq);
                if (prevDist - newDist >= AetherhavenConstants.AUTONOMY_STALL_GOAL_PROGRESS) {
                    madeGoalProgress = true;
                }
            }
            state.setAutonomyGoalDistSq(goalDistSq);
        }

        if (madeAnchorProgress || madeGoalProgress) {
            state.setAutonomyAnchorPosition(pos.x, pos.z);
            state.setAutonomySamplePosition(pos.x, pos.z);
            state.setAutonomyStallTicks(0);
            return;
        }

        state.setAutonomySamplePosition(pos.x, pos.z);
        state.setAutonomyStallTicks(state.getAutonomyStallTicks() + 1);
    }

    public static boolean isStallTeleportDue(@Nonnull AutonomyStallTrackable state) {
        return state.getAutonomyStallTicks() >= AetherhavenConstants.AUTONOMY_STALL_TELEPORT_TICKS;
    }

    public static void resetAfterRecovery(@Nonnull AutonomyStallTrackable state) {
        state.resetAutonomyStallTracking();
    }

    public static boolean shouldTrackTouristStall(int phase, boolean leaveDue) {
        if (leaveDue) {
            return true;
        }
        if (phase == TouristAutonomyState.PHASE_POI || phase == TouristAutonomyState.PHASE_IDLE) {
            return false;
        }
        return true;
    }

    public static boolean shouldTrackVillagerStall(int phase) {
        return phase == VillagerAutonomyState.PHASE_TRAVEL;
    }

    @Nullable
    public static TouristRecoveryTarget resolveTouristRecoveryTarget(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull TouristAutonomyState autonomy,
        @Nonnull Vector3d pos,
        boolean leaveDue,
        long nowMs,
        int salt
    ) {
        if (leaveDue
            || autonomy.getPhase() == TouristAutonomyState.PHASE_RETURNING
            || AetherhavenConstants.isTouristPortalReturnPoi(autonomy.getTargetPoiUuid())) {
            Vector3d portalStand = resolvePortalReturnStand(world, plugin, autonomy);
            if (portalStand != null) {
                return new TouristRecoveryTarget(portalStand, true);
            }
            if (leaveDue) {
                return null;
            }
        }

        if (autonomy.getPhase() == TouristAutonomyState.PHASE_TRAVEL
            || autonomy.getPhase() == TouristAutonomyState.PHASE_RETURNING) {
            return new TouristRecoveryTarget(
                new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ()),
                false
            );
        }

        UUID visitPlotId = autonomy.getVisitPlotUuid();
        if (autonomy.getPhase() == TouristAutonomyState.PHASE_VISIT && visitPlotId != null) {
            ConstructionCatalog catalog = plugin.getConstructionCatalog();
            PlotInstance plot = TouristDestinationResolver.findVisitPlot(town, visitPlotId);
            if (plot != null
                && !TouristDestinationResolver.isInsidePlotFootprint(
                    pos.x,
                    pos.z,
                    plot,
                    TouristDestinationResolver.plotEdgePadding()
                )) {
                TouristPlotVisit entry = findPlotVisit(town, catalog, world, visitPlotId);
                if (entry != null) {
                    return new TouristRecoveryTarget(
                        new Vector3d(entry.entryX(), entry.entryY(), entry.entryZ()),
                        false
                    );
                }
            }
        }

        if (autonomy.getPhase() == TouristAutonomyState.PHASE_IDLE && visitPlotId == null) {
            ConstructionCatalog catalog = plugin.getConstructionCatalog();
            Random random = new Random(nowMs ^ salt ^ 0x7F4A7C15L);
            TouristPlotVisit pick =
                TouristDestinationResolver.pickVisitPlot(town, catalog, world, null, random);
            if (pick != null) {
                return new TouristRecoveryTarget(
                    new Vector3d(pick.entryX(), pick.entryY(), pick.entryZ()),
                    false
                );
            }
        }

        if (autonomy.getPhase() == TouristAutonomyState.PHASE_VISIT && visitPlotId != null) {
            ConstructionCatalog catalog = plugin.getConstructionCatalog();
            TouristPlotVisit entry = findPlotVisit(town, catalog, world, visitPlotId);
            if (entry != null) {
                return new TouristRecoveryTarget(
                    new Vector3d(entry.entryX(), entry.entryY(), entry.entryZ()),
                    false
                );
            }
        }

        return null;
    }

    @Nullable
    public static Vector3d resolveVillagerRecoveryTarget(
        @Nonnull VillagerAutonomyState autonomy
    ) {
        if (autonomy.getPhase() != VillagerAutonomyState.PHASE_TRAVEL) {
            return null;
        }
        return new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ());
    }

    public static void teleportNpc(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d target,
        @Nonnull NPCEntity npc
    ) {
        Vector3d feet = VillagerBlockUtil.snapNpcFeetToStand(store.getExternalData().getWorld(), target);
        Rotation3f rotation = resolveBodyRotation(store, ref);
        AetherhavenNpcTeleport.apply(ref, commandBuffer, Teleport.createExact(feet, rotation));
    }

    public static void applyPostTeleportTravel(
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        @Nonnull Vector3d target,
        boolean portalReturn
    ) {
        autonomy.resetAutonomyStallTracking();
        autonomy.setTravelStuckTicks(0);
        autonomy.clearTravelWaypoints();
        autonomy.setTravelDirectFallback(portalReturn);
        npc.setLeashPoint(target);
    }

    public static void applyPostTeleportTravel(
        @Nonnull NPCEntity npc,
        @Nonnull VillagerAutonomyState autonomy,
        @Nonnull Vector3d target
    ) {
        autonomy.resetAutonomyStallTracking();
        autonomy.setTravelStuckTicks(0);
        autonomy.clearTravelWaypoints();
        npc.setLeashPoint(target);
    }

    @Nullable
    public static Vector3d resolvePortalReturnStand(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TouristAutonomyState autonomy
    ) {
        TouristPortalRecord portal = resolveHomePortalRecord(world, plugin, autonomy);
        if (portal == null) {
            return null;
        }
        return TouristPortalBlockUtil.returnStandPosition(world, portal.getBlockPosition());
    }

    @Nullable
    public static TouristPortalRecord resolveHomePortalRecord(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TouristAutonomyState autonomy
    ) {
        UUID portalId = autonomy.getHomePortalId();
        if (portalId == null) {
            return null;
        }
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        return registry.get(portalId);
    }

    /**
     * When the home portal record is missing, try the tourist record portal id or any portal in the town.
     */
    @Nullable
    public static TouristPortalRecord resolvePortalForReturn(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TouristAutonomyState autonomy,
        @Nullable TouristRecord rec
    ) {
        TouristPortalRecord portal = resolveHomePortalRecord(world, plugin, autonomy);
        if (portal != null) {
            return portal;
        }
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        if (rec != null && rec.getPortalId() != null) {
            portal = registry.get(rec.getPortalId());
            if (portal != null) {
                autonomy.setHomePortalId(rec.getPortalId());
                return portal;
            }
        }
        List<TouristPortalRecord> townPortals = registry.recordsForTown(town.getTownId());
        if (townPortals.isEmpty()) {
            return null;
        }
        TouristPortalRecord pick = townPortals.get(0);
        autonomy.setHomePortalId(pick.getPortalId());
        return pick;
    }

    public static boolean isTouristLeaveDue(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull String characterId
    ) {
        TouristRecord rec = TouristPortalTickService.findTouristRecord(town, characterId);
        if (rec == null || rec.isInvitedToStay() || rec.isCitizen()) {
            return false;
        }
        return TouristPortalTickService.shouldTouristLeaveNow(rec, store);
    }

    @Nonnull
    private static Rotation3f resolveBodyRotation(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            return new Rotation3f(tc.getRotation());
        }
        return new Rotation3f(0.0F, 0.0F, 0.0F);
    }

    @Nullable
    private static TouristPlotVisit findPlotVisit(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull World world,
        @Nonnull UUID plotId
    ) {
        for (TouristPlotVisit visit : TouristDestinationResolver.listVisitPlots(town, catalog, world)) {
            if (plotId.equals(visit.plotId())) {
                return visit;
            }
        }
        return null;
    }
}
