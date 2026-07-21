package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.AutonomyStuckTeleportRecovery;
import com.hexvane.aetherhaven.autonomy.PoiAutonomyVisuals;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.autonomy.VillagerDoorUtil;
import com.hexvane.aetherhaven.autonomy.pathnav.PathNavGraphService;
import com.hexvane.aetherhaven.autonomy.pathnav.PathNavTravelSupport;
import com.hexvane.aetherhaven.autonomy.pathnav.PathNavTravelWaypoints;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiOccupancy;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.shopspot.NpcShopSpotBuyerService;
import com.hexvane.aetherhaven.shopspot.ShopSpotBrowseVisuals;
import com.hexvane.aetherhaven.shopspot.ShopSpotPurchaseService;
import com.hexvane.aetherhaven.shopspot.ShopSpotRecord;
import com.hexvane.aetherhaven.shopspot.ShopSpotRegistry;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.NavState;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class TouristAutonomySystem extends EntityTickingSystem<EntityStore> {
    /** Seek leash is ~0.5; stop arrival earlier so shoppers do not jog into counters/stalls. */
    private static final double ARRIVE_HORIZONTAL_SQ = 1.25 * 1.25;
    private static final double RETURN_ARRIVE_HORIZONTAL_SQ = 2.75 * 2.75;
    private static final long TRAVEL_PHASE_MAX_MS = 120_000L;
    private static final int BLOCKED_FAIL_TICKS = 100;
    private static final int DOOR_UNJAM_STUCK_TICKS = 30;
    private static final int RETURN_DOOR_FORCE_DESPAWN_TICKS = 60;
    private static final long VISIT_MIN_MS = 45_000L;
    private static final long VISIT_MAX_MS = 120_000L;
    private static final long SHOP_VISIT_MIN_MS = 20_000L;
    private static final long SHOP_VISIT_MAX_MS = 40_000L;
    private static final int SHOP_SPOTS_PER_VISIT_MAX = 2;
    /** Chance a visiting tourist buys one player listing after browsing a player shop (0–100). */
    private static final int PLAYER_SHOP_BUY_CHANCE_PERCENT = 60;
    /** Settled tourist citizens keep browsing towns, but buy from player shops less often. */
    private static final int PLAYER_SHOP_BUY_CHANCE_CITIZEN_PERCENT = 20;
    private static final long POI_USE_MIN_MS = 15_000L;
    private static final long POI_USE_MAX_MS = 35_000L;
    private static final long SHOP_SPOT_BROWSE_MIN_MS = 5_000L;
    private static final long SHOP_SPOT_BROWSE_MAX_MS = 12_000L;
    private static final long POI_PICK_MIN_DELAY_MS = 12_000L;
    private static final long POI_PICK_MAX_DELAY_MS = 28_000L;
    private static final long SHOP_SPOT_PICK_MIN_DELAY_MS = 3_000L;
    private static final long SHOP_SPOT_PICK_MAX_DELAY_MS = 7_000L;

    @Nonnull
    private final AetherhavenPlugin plugin;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public TouristAutonomySystem(@Nonnull AetherhavenPlugin plugin) {
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
            TownsfolkCharacterBinding.getComponentType(),
            TownVillagerBinding.getComponentType(),
            TouristAutonomyState.getComponentType(),
            NPCEntity.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TownsfolkCharacterBinding tb = chunk.getComponent(index, TownsfolkCharacterBinding.getComponentType());
        TouristAutonomyState autonomy = chunk.getComponent(index, TouristAutonomyState.getComponentType());
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        TownVillagerBinding binding = chunk.getComponent(index, TownVillagerBinding.getComponentType());
        if (tb == null || autonomy == null || npc == null || binding == null) {
            return;
        }
        if (!TownsfolkAssignmentKinds.isTourist(tb.getAssignmentKind())) {
            return;
        }
        if (NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        long now = resolveNowMs(store);
        World world = store.getExternalData().getWorld();
        if (!world.isAlive()) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            return;
        }
        if (tryLeaveIfDue(ref, store, commandBuffer, npc, autonomy, now, town, world, tb)) {
            return;
        }
        if (tryStallTeleportRecovery(ref, store, commandBuffer, npc, autonomy, now, town, world, tb)) {
            return;
        }
        PoiRegistry poiRegistry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        ConstructionCatalog catalog = plugin.getConstructionCatalog();

        switch (autonomy.getPhase()) {
            case TouristAutonomyState.PHASE_IDLE ->
                tickIdle(ref, store, commandBuffer, npc, autonomy, now, town, world, catalog);
            case TouristAutonomyState.PHASE_TRAVEL, TouristAutonomyState.PHASE_RETURNING ->
                tickTravel(ref, store, commandBuffer, npc, autonomy, now, town, world, poiRegistry, tb);
            case TouristAutonomyState.PHASE_VISIT ->
                tickPlotVisit(ref, store, commandBuffer, npc, autonomy, now, town, world, poiRegistry, catalog);
            case TouristAutonomyState.PHASE_POI ->
                tickPlotPoi(ref, store, commandBuffer, npc, autonomy, now, town, world, poiRegistry);
            default -> {
                autonomy.setPhase(TouristAutonomyState.PHASE_IDLE);
                commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            }
        }
    }

    /** When their visit window has elapsed, stop shopping/wandering and walk back to the portal. */
    private boolean tryLeaveIfDue(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull TownsfolkCharacterBinding tb
    ) {
        if (isReturningTravel(autonomy)) {
            return false;
        }
        TouristRecord rec = TouristPortalTickService.findTouristRecord(town, tb.getCharacterId());
        if (rec == null || rec.isInvitedToStay() || rec.isCitizen()) {
            return false;
        }
        if (!TouristPortalTickService.shouldTouristLeaveNow(rec, store)) {
            return false;
        }
        UUID portalId = rec.getPortalId();
        if (portalId != null) {
            autonomy.setHomePortalId(portalId);
        }
        autonomy.clearVisitPlot();
        autonomy.clearTravelWaypoints();
        if (beginReturnToPortalOnStore(ref, store, plugin, npc, autonomy, now, town, world)) {
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return true;
        }
        AutonomyStuckTeleportRecovery.resolvePortalForReturn(world, plugin, town, autonomy, rec);
        if (beginReturnToPortalOnStore(ref, store, plugin, npc, autonomy, now, town, world)) {
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return true;
        }
        finishReturnDespawn(ref, store, commandBuffer, autonomy, town, world);
        return true;
    }

    private boolean tryStallTeleportRecovery(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull TownsfolkCharacterBinding tb
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return false;
        }
        boolean leaveDue = AutonomyStuckTeleportRecovery.isTouristLeaveDue(town, store, tb.getCharacterId());
        if (!AutonomyStuckTeleportRecovery.shouldTrackTouristStall(autonomy.getPhase(), leaveDue)) {
            autonomy.resetAutonomyStallTracking();
            return false;
        }

        Vector3d pos = tc.getPosition();
        Vector3d leash = npc.getLeashPoint();
        AutonomyStuckTeleportRecovery.updateStall(
            autonomy,
            pos,
            Double.isFinite(leash.x) ? leash.x : autonomy.getTargetX(),
            Double.isFinite(leash.z) ? leash.z : autonomy.getTargetZ()
        );
        if (!AutonomyStuckTeleportRecovery.isStallTeleportDue(
            autonomy,
            AetherhavenConstants.TOURIST_STALL_TELEPORT_TICKS
        )) {
            return false;
        }

        AutonomyStuckTeleportRecovery.TouristRecoveryTarget target =
            AutonomyStuckTeleportRecovery.resolveTouristRecoveryTarget(
                plugin,
                town,
                world,
                autonomy,
                pos,
                leaveDue,
                now,
                ref.hashCode()
            );
        if (leaveDue && target == null) {
            finishReturnDespawn(ref, store, commandBuffer, autonomy, town, world);
            return true;
        }
        if (target == null) {
            AutonomyStuckTeleportRecovery.resetAfterRecovery(autonomy);
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            return false;
        }

        AutonomyStuckTeleportRecovery.teleportNpc(ref, commandBuffer, store, target.position(), npc);
        AutonomyStuckTeleportRecovery.applyPostTeleportTravel(npc, autonomy, target.position(), target.portalReturn());

        if (target.portalReturn()) {
            autonomy.setPhase(TouristAutonomyState.PHASE_RETURNING);
            beginTravelTo(autonomy, now, target.position().x, target.position().y, target.position().z, AetherhavenConstants.TOURIST_PORTAL_RETURN_POI_ID);
            TouristPortalRecord portal = resolveHomePortal(world, autonomy);
            if (portal != null
                && TouristPortalBlockUtil.isNearPortalDespawn(world, portal.getBlockPosition(), target.position())) {
                finishReturnDespawn(ref, store, commandBuffer, autonomy, town, world);
                return true;
            }
        } else if (autonomy.getPhase() == TouristAutonomyState.PHASE_IDLE) {
            ConstructionCatalog catalog = plugin.getConstructionCatalog();
            TouristPlotVisit plotVisit = findPlotVisitNearTarget(town, catalog, world, target.position());
            if (plotVisit != null) {
                beginTravelToPlotOnStore(ref, store, plugin, npc, autonomy, now, town, world, plotVisit, true);
            }
        }

        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        applyAutonomyRoleState(ref, npc, commandBuffer);
        return true;
    }

    private void tickIdle(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull ConstructionCatalog catalog
    ) {
        if (now < autonomy.getNextDecisionEpochMs()) {
            return;
        }
        Random random = new Random(now ^ ref.hashCode());
        TouristPlotVisit pick =
            TouristDestinationResolver.pickVisitPlot(town, catalog, world, autonomy.getVisitPlotUuid(), random);
        if (pick == null) {
            autonomy.setNextDecisionEpochMs(now + 8000L);
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            clearAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }
        beginTravelToPlot(ref, store, commandBuffer, npc, autonomy, now, town, world, pick);
    }

    /** Immediately pick a plot and start walking (used right after portal spawn). */
    public static void kickInitialVisitOnSpawn(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TouristAutonomyState autonomy,
        @Nonnull TownRecord town,
        @Nonnull World world
    ) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        long now = resolveNowMs(store);
        Random random = new Random(now ^ ref.hashCode() ^ 0x5DEECE66DL);
        TouristPlotVisit pick = TouristDestinationResolver.pickVisitPlot(town, catalog, world, null, random);
        if (pick == null) {
            autonomy.setNextDecisionEpochMs(now + 3000L);
            store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            clearAutonomyRoleStateOnStore(ref, npc, store);
            return;
        }
        beginTravelToPlotOnStore(ref, store, plugin, npc, autonomy, now, town, world, pick);
        store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        store.putComponent(ref, NPCEntity.getComponentType(), npc);
        applyAutonomyRoleStateOnStore(ref, npc, store);
    }

    private void beginTravelToPlot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull TouristPlotVisit plot
    ) {
        beginTravelToPlot(ref, store, commandBuffer, npc, autonomy, now, town, world, plot, true);
    }

    private void beginTravelToPlot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull TouristPlotVisit plot,
        boolean usePathNav
    ) {
        beginTravelToPlotOnStore(ref, store, plugin, npc, autonomy, now, town, world, plot, usePathNav);
        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static void beginTravelTo(
        @Nonnull TouristAutonomyState autonomy,
        long now,
        double x,
        double y,
        double z,
        @Nonnull UUID destinationId
    ) {
        if (AetherhavenConstants.isTouristPortalReturnPoi(destinationId)) {
            autonomy.setPhase(TouristAutonomyState.PHASE_RETURNING);
        } else {
            autonomy.setPhase(TouristAutonomyState.PHASE_TRAVEL);
        }
        autonomy.setTravelTarget(x, y, z, destinationId);
        autonomy.setTravelStuckTicks(0);
        autonomy.resetAutonomyStallTracking();
        autonomy.setTravelDirectFallback(false);
        autonomy.setNextDecisionEpochMs(now + TRAVEL_PHASE_MAX_MS);
    }

    private static void beginTravelToPlotOnStore(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull TouristPlotVisit plot
    ) {
        beginTravelToPlotOnStore(ref, store, plugin, npc, autonomy, now, town, world, plot, true);
    }

    private static void beginTravelToPlotOnStore(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull TouristPlotVisit plot,
        boolean usePathNav
    ) {
        autonomy.setVisitPlotId(plot.plotId());
        beginTravelTo(autonomy, now, plot.entryX(), plot.entryY(), plot.entryZ(), plot.destinationId());
        routeNpcToTarget(
            ref,
            store,
            plugin,
            npc,
            autonomy,
            town,
            world,
            new Vector3d(plot.entryX(), plot.entryY(), plot.entryZ()),
            usePathNav
        );
    }

    private void beginTravelToPoi(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull PoiEntry poi
    ) {
        double tx;
        double ty;
        double tz;
        if (poi.hasInteractionTarget()) {
            Double tpx = poi.getInteractionTargetX();
            Double tpy = poi.getInteractionTargetY();
            Double tpz = poi.getInteractionTargetZ();
            if (tpx == null || tpy == null || tpz == null) {
                return;
            }
            tx = tpx;
            ty = tpy;
            tz = tpz;
        } else {
            tx = poi.getX() + 0.5;
            ty = poi.getY() + 0.02;
            tz = poi.getZ() + 0.5;
        }
        beginTravelTo(autonomy, now, tx, ty, tz, poi.getId());
        routeNpcToTarget(ref, store, plugin, npc, autonomy, town, world, new Vector3d(tx, ty, tz), false);
        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static void routeNpcToTarget(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull Vector3d finalTarget,
        boolean usePathNav
    ) {
        if (!usePathNav || autonomy.isTravelDirectFallback()) {
            autonomy.clearTravelWaypoints();
            npc.setLeashPoint(finalTarget);
            return;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
            PathNavGraphService.PathNavFindResult navResult =
                AetherhavenWorldRegistries
                    .getOrCreatePathNavGraphService(world)
                    .findRouteResult(town.getTownId(), tc.getPosition(), finalTarget, plugin.getConfig().get());
            var route = navResult.waypoints();
            if (!route.isEmpty()) {
                route =
                    PathNavTravelWaypoints.prepareForSeek(
                        world,
                        tc.getPosition(),
                        route,
                        finalTarget,
                        (int) Math.floor(tc.getPosition().y),
                        plugin.getConfig().get().getPathNavNodeSpacing()
                    );
            }
            if (!route.isEmpty()) {
                autonomy.setTravelWaypoints(route);
                Vector3d first = autonomy.getCurrentTravelWaypoint();
                npc.setLeashPoint(first != null ? first : finalTarget);
            } else {
                autonomy.clearTravelWaypoints();
                npc.setLeashPoint(finalTarget);
            }
        } else {
            autonomy.clearTravelWaypoints();
            npc.setLeashPoint(finalTarget);
        }
    }

    private static void applyDirectTravelFallback(
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy
    ) {
        autonomy.setTravelDirectFallback(true);
        autonomy.clearTravelWaypoints();
        autonomy.setTravelStuckTicks(0);
        npc.setLeashPoint(new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ()));
    }

    private static boolean supportsAutonomyPoiRoleState(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return false;
        }
        return npc.getRole().getStateSupport().getStateHelper().getStateIndex(AetherhavenConstants.NPC_STATE_AUTONOMY_POI) >= 0;
    }

    private void beginReturnToPortal(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world
    ) {
        if (!beginReturnToPortalOnStore(ref, store, plugin, npc, autonomy, now, town, world)) {
            autonomy.setPhase(TouristAutonomyState.PHASE_IDLE);
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            return;
        }
        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    /** Routes a tourist back to their home portal (safe outside tick and from tick). */
    public static boolean beginReturnToPortalOnStore(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world
    ) {
        UUID portalId = autonomy.getHomePortalId();
        if (portalId == null) {
            return false;
        }
        TouristPortalRecord portal =
            AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin).get(portalId);
        if (portal == null) {
            return false;
        }
        Vector3i blockPos = portal.getBlockPosition();
        Vector3d feet = TouristPortalBlockUtil.returnStandPosition(world, blockPos);
        autonomy.clearVisitPlot();
        beginTravelTo(autonomy, now, feet.x, feet.y, feet.z, AetherhavenConstants.TOURIST_PORTAL_RETURN_POI_ID);
        routeNpcToTarget(ref, store, plugin, npc, autonomy, town, world, feet, true);
        return true;
    }

    public static boolean isReturningHome(@Nonnull TouristAutonomyState autonomy) {
        return autonomy.getPhase() == TouristAutonomyState.PHASE_RETURNING
            || AetherhavenConstants.isTouristPortalReturnPoi(autonomy.getTargetPoiUuid());
    }

    private static boolean isReturningTravel(@Nonnull TouristAutonomyState autonomy) {
        return isReturningHome(autonomy);
    }

    private void tickTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull PoiRegistry poiRegistry,
        @Nonnull TownsfolkCharacterBinding tb
    ) {
        boolean returning = isReturningTravel(autonomy);
        if (returning) {
            UUID target = autonomy.getTargetPoiUuid();
            if (target == null || !AetherhavenConstants.isTouristPortalReturnPoi(target)) {
                beginReturnToPortal(ref, store, commandBuffer, npc, autonomy, now, town, world);
                return;
            }
        }

        TransformComponent tcEarly = store.getComponent(ref, TransformComponent.getComponentType());
        if (returning && tcEarly != null) {
            TouristPortalRecord portal = resolveHomePortal(world, autonomy);
            if (portal != null
                && TouristPortalBlockUtil.isNearPortalDespawn(world, portal.getBlockPosition(), tcEarly.getPosition())) {
                finishReturnDespawn(ref, store, commandBuffer, autonomy, town, world);
                return;
            }
        }

        if (now >= autonomy.getNextDecisionEpochMs()) {
            failTravel(ref, store, commandBuffer, npc, autonomy, now, town, world, tb);
            return;
        }

        TransformComponent tc = tcEarly != null ? tcEarly : store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }

        Vector3d pos = tc.getPosition();
        Vector3d leash = npc.getLeashPoint();
        double horizSq = (pos.x - leash.x) * (pos.x - leash.x) + (pos.z - leash.z) * (pos.z - leash.z);
        double arriveSq = returning ? RETURN_ARRIVE_HORIZONTAL_SQ : ARRIVE_HORIZONTAL_SQ;

        PathNavTravelSupport.WaypointTickAction waypointAction =
            PathNavTravelSupport.tickTravelWaypoints(autonomy, pos, leash.x, leash.z, arriveSq, now);
        if (waypointAction == PathNavTravelSupport.WaypointTickAction.ADVANCED) {
            Vector3d next = autonomy.getCurrentTravelWaypoint();
            if (next != null) {
                npc.setLeashPoint(next);
            }
            autonomy.setTravelStuckTicks(0);
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }
        if (waypointAction == PathNavTravelSupport.WaypointTickAction.CLEARED_TO_FINAL) {
            autonomy.setTravelStuckTicks(0);
            npc.setLeashPoint(new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ()));
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        VillagerDoorUtil.tryOpenDoorsTowardLeash(
            world,
            pos,
            leash,
            (x, y, z) -> autonomy.addPendingDoorOpened(x, y, z)
        );

        MotionController mc = npc.getRole() != null ? npc.getRole().getActiveMotionController() : null;
        NavState nav = mc != null ? mc.getNavState() : NavState.INIT;
        if (nav == NavState.ABORTED) {
            if (returning && !autonomy.isTravelDirectFallback()) {
                applyDirectTravelFallback(npc, autonomy);
                commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
                applyAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
            failTravel(ref, store, commandBuffer, npc, autonomy, now, town, world, tb);
            return;
        }
        if (nav == NavState.BLOCKED || nav == NavState.DEFER) {
            autonomy.setTravelStuckTicks(autonomy.getTravelStuckTicks() + 1);
            if (autonomy.getTravelStuckTicks() >= DOOR_UNJAM_STUCK_TICKS) {
                VillagerDoorUtil.tryUnjamDoorsAlongPath(world, pos, leash);
            }
            if (returning && autonomy.getTravelStuckTicks() >= RETURN_DOOR_FORCE_DESPAWN_TICKS) {
                finishReturnDespawn(ref, store, commandBuffer, autonomy, town, world);
                return;
            }
            if (autonomy.getTravelStuckTicks() >= BLOCKED_FAIL_TICKS) {
                if (autonomy.hasTravelWaypoints()) {
                    if (autonomy.advanceTravelWaypoint()) {
                        Vector3d next = autonomy.getCurrentTravelWaypoint();
                        if (next != null) {
                            npc.setLeashPoint(next);
                        }
                    } else {
                        autonomy.clearTravelWaypoints();
                        npc.setLeashPoint(new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ()));
                    }
                    autonomy.setTravelStuckTicks(0);
                    commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                    commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
                    applyAutonomyRoleState(ref, npc, commandBuffer);
                    return;
                }
                Vector3d recovery =
                    new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ());
                AutonomyStuckTeleportRecovery.teleportNpc(ref, commandBuffer, store, recovery, npc);
                AutonomyStuckTeleportRecovery.applyPostTeleportTravel(npc, autonomy, recovery, returning);
                commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
                applyAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
        } else if (nav == NavState.PROGRESSING || nav == NavState.INIT) {
            autonomy.setTravelStuckTicks(0);
        }

        if (returning) {
            TouristPortalRecord portal = resolveHomePortal(world, autonomy);
            if (portal != null && TouristPortalBlockUtil.isNearPortalDespawn(world, portal.getBlockPosition(), pos)) {
                finishReturnDespawn(ref, store, commandBuffer, autonomy, town, world);
                return;
            }
        }

        if (horizSq <= arriveSq && !autonomy.hasTravelWaypoints()) {
            closePendingDoorsAfterTravelArrival(ref, store, world, autonomy, pos, arriveSq);
            UUID targetId = autonomy.getTargetPoiUuid();
            if (targetId != null && AetherhavenConstants.isTouristPortalReturnPoi(targetId)) {
                finishReturnDespawn(ref, store, commandBuffer, autonomy, town, world);
                return;
            }

            UUID visitPlotId = autonomy.getVisitPlotUuid();
            ShopSpotRegistry shopRegistry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
            ShopSpotRecord shopSpot = targetId != null ? shopRegistry.get(targetId) : null;
            if (shopSpot != null && visitPlotId != null && visitPlotId.equals(shopSpot.getPlotId())) {
                beginShopSpotBrowse(ref, store, commandBuffer, npc, autonomy, now, shopSpot);
                return;
            }

            PoiEntry poi = targetId != null ? poiRegistry.get(targetId) : null;
            if (poi != null && visitPlotId != null && visitPlotId.equals(poi.getPlotId())) {
                beginPlotPoiUse(ref, store, commandBuffer, npc, autonomy, now, poi);
                return;
            }

            if (visitPlotId != null && TouristPlotVisit.isPlotDestinationId(targetId, visitPlotId)) {
                beginPlotWanderVisit(ref, store, commandBuffer, npc, autonomy, now, ref.hashCode(), town, world);
                return;
            }

            autonomy.setPhase(TouristAutonomyState.PHASE_IDLE);
            autonomy.setNextDecisionEpochMs(now + 3000L);
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            clearAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    private void beginPlotWanderVisit(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        int salt,
        @Nonnull TownRecord town,
        @Nonnull World world
    ) {
        autonomy.setPhase(TouristAutonomyState.PHASE_VISIT);
        autonomy.clearTravelWaypoints();
        autonomy.setTravelStuckTicks(0);
        UUID plotId = autonomy.getVisitPlotUuid();
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        boolean shopPlot = plotId != null && TouristDestinationResolver.isTouristShopPlot(town, catalog, plotId);
        long dur = visitDurationMs(now, salt, shopPlot);
        autonomy.setPhaseEndEpochMs(now + dur);
        if (shopPlot) {
            scheduleNextShopSpotPick(autonomy, now, salt);
        } else {
            scheduleNextPoiPick(autonomy, now, salt);
        }
        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        if (shopPlot) {
            autonomy.setLastPlotShopSpotId(null);
            if (tryTravelToNextShopSpot(ref, store, commandBuffer, npc, autonomy, now, town, world)) {
                return;
            }
        }
        clearAutonomyRoleState(ref, npc, commandBuffer);
    }

    private void beginPlotPoiUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull PoiEntry poi
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID selfUuid = uc != null ? uc.getUuid() : null;
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        UUID townId = binding != null ? binding.getTownId() : null;
        if (townId != null) {
            PoiRegistry poiRegistry =
                AetherhavenWorldRegistries.getOrCreatePoiRegistry(store.getExternalData().getWorld(), plugin);
            if (!PoiOccupancy.canBeginUse(store, townId, poiRegistry, poi, selfUuid)) {
                autonomy.setPhase(TouristAutonomyState.PHASE_VISIT);
                autonomy.setNextDecisionEpochMs(now + 2000L);
                commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                clearAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
        }
        autonomy.setPhase(TouristAutonomyState.PHASE_POI);
        autonomy.clearTravelWaypoints();
        autonomy.setLastPlotPoiId(poi.getId());
        long dur = POI_USE_MIN_MS + Math.abs(now % (POI_USE_MAX_MS - POI_USE_MIN_MS + 1));
        autonomy.setPhaseEndEpochMs(now + dur);
        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        PoiAutonomyVisuals.beginPoiUse(ref, store, commandBuffer, poi);
        VillagerAutonomySystem.afterBeginPoiUseMotion(ref, store, commandBuffer, npc);
    }

    private void beginShopSpotBrowse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull ShopSpotRecord spot
    ) {
        autonomy.setPhase(TouristAutonomyState.PHASE_POI);
        autonomy.clearTravelWaypoints();
        autonomy.setLastPlotShopSpotId(spot.getSpotId());
        autonomy.incrementShopSpotsBrowsedThisVisit();
        long dur = shopSpotBrowseDurationMs(now);
        autonomy.setPhaseEndEpochMs(now + dur);
        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        TouristShopSpotBrowse.faceTowardShopSpot(ref, store, commandBuffer, spot);
        ShopSpotBrowseVisuals.beginPonder(ref, store, commandBuffer);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    private void beginTravelToShopSpot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull ShopSpotRecord spot
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d preferNear = tc != null ? tc.getPosition() : null;
        PoiRegistry poiRegistry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        Map<String, Integer> occupancy = PoiOccupancy.cellOccupancyForTown(world, town.getTownId(), store, poiRegistry);
        double[] stand = TouristShopSpotBrowse.customerStandWorld(
            world,
            spot,
            poiRegistry,
            preferNear,
            occupancy
        );
        if (stand == null) {
            return;
        }
        beginTravelToShopSpot(ref, store, commandBuffer, npc, autonomy, now, town, world, spot, stand, preferNear);
    }

    private void beginTravelToShopSpot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull ShopSpotRecord spot,
        @Nonnull double[] stand,
        @Nullable Vector3d preferNear
    ) {
        beginTravelTo(autonomy, now, stand[0], stand[1], stand[2], spot.getSpotId());
        if (preferNear != null) {
            double dx = preferNear.x - stand[0];
            double dz = preferNear.z - stand[2];
            if (dx * dx + dz * dz <= ARRIVE_HORIZONTAL_SQ) {
                // Already at the customer stand — browse now instead of Seek jittering in place.
                beginShopSpotBrowse(ref, store, commandBuffer, npc, autonomy, now, spot);
                return;
            }
        }
        routeNpcToTarget(ref, store, plugin, npc, autonomy, town, world, new Vector3d(stand[0], stand[1], stand[2]), false);
        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    private boolean tryTravelToNextShopSpot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world
    ) {
        UUID plotId = autonomy.getVisitPlotUuid();
        if (plotId == null) {
            return false;
        }
        if (autonomy.getShopSpotsBrowsedThisVisit() >= SHOP_SPOTS_PER_VISIT_MAX) {
            return false;
        }
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        List<ShopSpotRecord> spots = TouristShopSpotBrowse.listOnPlot(registry, plotId);
        if (spots.isEmpty()) {
            return false;
        }
        Random random = new Random(now ^ ref.hashCode() ^ plotId.hashCode());
        ShopSpotRecord spot = TouristShopSpotBrowse.pickNext(spots, autonomy.getLastPlotShopSpotUuid(), random);
        if (spot == null) {
            return false;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d preferNear = tc != null ? tc.getPosition() : null;
        PoiRegistry poiRegistry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        Map<String, Integer> occupancy = PoiOccupancy.cellOccupancyForTown(world, town.getTownId(), store, poiRegistry);
        double[] stand = TouristShopSpotBrowse.customerStandWorld(
            world,
            spot,
            poiRegistry,
            preferNear,
            occupancy
        );
        if (stand == null) {
            return false;
        }
        beginTravelToShopSpot(ref, store, commandBuffer, npc, autonomy, now, town, world, spot, stand, preferNear);
        return true;
    }

    private void tickPlotVisit(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull PoiRegistry poiRegistry,
        @Nonnull ConstructionCatalog catalog
    ) {
        UUID plotId = autonomy.getVisitPlotUuid();
        PlotInstance plot = TouristDestinationResolver.findVisitPlot(town, plotId);
        if (plot == null) {
            autonomy.clearVisitPlot();
            autonomy.setPhase(TouristAutonomyState.PHASE_IDLE);
            autonomy.setNextDecisionEpochMs(now + 3000L);
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            clearAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null
            && !TouristDestinationResolver.isInsidePlotFootprint(
                tc.getPosition().x,
                tc.getPosition().z,
                plot,
                TouristDestinationResolver.plotEdgePadding()
            )) {
            TouristPlotVisit entry = findPlotVisit(town, catalog, world, plotId);
            if (entry != null) {
                beginTravelToPlot(ref, store, commandBuffer, npc, autonomy, now, town, world, entry, false);
            }
            return;
        }

        if (now >= autonomy.getPhaseEndEpochMs()) {
            autonomy.clearVisitPlot();
            autonomy.setPhase(TouristAutonomyState.PHASE_IDLE);
            autonomy.setNextDecisionEpochMs(now + 2000L);
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            clearAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        if (TouristDestinationResolver.isTouristShopPlot(town, catalog, plotId)) {
            if (now >= autonomy.getNextPoiPickEpochMs()) {
                if (tryTravelToNextShopSpot(ref, store, commandBuffer, npc, autonomy, now, town, world)) {
                    return;
                }
                // No shop spot to walk to — use authored tourist stands instead of standing at the counter.
                Random random = new Random(now ^ ref.hashCode() ^ plotId.hashCode() ^ 0x51A7L);
                int npcFeetY = tc != null ? (int) Math.floor(tc.getPosition().y) : 64;
                Map<String, Integer> occupancy =
                    PoiOccupancy.cellOccupancyForTown(world, town.getTownId(), store, poiRegistry);
                PoiEntry poi =
                    TouristDestinationResolver.pickVisitPoiOnPlot(
                        town,
                        poiRegistry,
                        catalog,
                        plotId,
                        autonomy.getLastPlotPoiUuid(),
                        random,
                        world,
                        plugin,
                        npcFeetY,
                        store,
                        occupancy
                    );
                scheduleNextShopSpotPick(autonomy, now, ref.hashCode());
                if (poi != null) {
                    beginTravelToPoi(ref, store, commandBuffer, npc, autonomy, now, town, world, poi);
                    return;
                }
                commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            }
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        if (now >= autonomy.getNextPoiPickEpochMs()) {
            Random random = new Random(now ^ ref.hashCode() ^ plotId.hashCode());
            int npcFeetY = tc != null ? (int) Math.floor(tc.getPosition().y) : 64;
            Map<String, Integer> occupancy =
                PoiOccupancy.cellOccupancyForTown(world, town.getTownId(), store, poiRegistry);
            PoiEntry poi =
                TouristDestinationResolver.pickVisitPoiOnPlot(
                    town,
                    poiRegistry,
                    catalog,
                    plotId,
                    autonomy.getLastPlotPoiUuid(),
                    random,
                    world,
                    plugin,
                    npcFeetY,
                    store,
                    occupancy
                );
            scheduleNextPoiPick(autonomy, now, ref.hashCode());
            if (poi != null) {
                beginTravelToPoi(ref, store, commandBuffer, npc, autonomy, now, town, world, poi);
                return;
            }
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        }

        clearAutonomyRoleState(ref, npc, commandBuffer);
    }

    private void tickPlotPoi(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull PoiRegistry poiRegistry
    ) {
        if (now < autonomy.getPhaseEndEpochMs()) {
            if (VillagerAutonomySystem.isNpcBlockMounted(store, commandBuffer, ref)) {
                return;
            }
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }
        UUID targetId = autonomy.getTargetPoiUuid();
        ShopSpotRegistry shopRegistry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ShopSpotRecord shopSpot = targetId != null ? shopRegistry.get(targetId) : null;
        if (shopSpot != null) {
            ShopSpotBrowseVisuals.endPonder(ref, store, commandBuffer);
            tryTouristPlayerShopPurchase(ref, store, autonomy, town, world, shopSpot.getPlotId(), now);
            autonomy.setPhase(TouristAutonomyState.PHASE_VISIT);
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            if (tryTravelToNextShopSpot(ref, store, commandBuffer, npc, autonomy, now, town, world)) {
                return;
            }
            scheduleNextShopSpotPick(autonomy, now, ref.hashCode());
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        UUID visitPlotId = autonomy.getVisitPlotUuid();
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        if (visitPlotId != null && TouristDestinationResolver.isTouristShopPlot(town, catalog, visitPlotId)) {
            // Also try a buy after tourist-visit stands — shoppers often linger there without a stall browse.
            tryTouristPlayerShopPurchase(ref, store, autonomy, town, world, visitPlotId, now);
            autonomy.setPhase(TouristAutonomyState.PHASE_VISIT);
            scheduleNextShopSpotPick(autonomy, now, ref.hashCode());
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            if (tryTravelToNextShopSpot(ref, store, commandBuffer, npc, autonomy, now, town, world)) {
                return;
            }
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        UUID poiId = targetId;
        if (poiId != null) {
            PoiEntry poi = poiRegistry.get(poiId);
            if (poi != null) {
                PoiAutonomyVisuals.cleanupAfterPoiUse(ref, store, commandBuffer, poi);
            }
        }
        autonomy.setPhase(TouristAutonomyState.PHASE_VISIT);
        scheduleNextPoiPick(autonomy, now, ref.hashCode());
        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        clearAutonomyRoleState(ref, npc, commandBuffer);
    }

    private void tryTouristPlayerShopPurchase(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull TouristAutonomyState autonomy,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull UUID plotId,
        long now
    ) {
        if (autonomy.isShopPurchaseDoneThisVisit()) {
            return;
        }
        if (!ShopSpotPurchaseService.isPlayerShopPlot(plugin, town, plotId)) {
            return;
        }
        int buyChancePercent = PLAYER_SHOP_BUY_CHANCE_PERCENT;
        TownsfolkCharacterBinding tb = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        if (tb != null && !tb.getCharacterId().isBlank()) {
            TouristRecord rec = TouristPortalTickService.findTouristRecord(town, tb.getCharacterId());
            if (rec != null && rec.isCitizen()) {
                buyChancePercent = PLAYER_SHOP_BUY_CHANCE_CITIZEN_PERCENT;
            }
        }
        Random random = new Random(now ^ ref.hashCode() ^ plotId.hashCode());
        if (random.nextInt(100) >= buyChancePercent) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        String buyerName = "A visitor";
        if (tb != null && !tb.getCharacterId().isBlank()) {
            TownsfolkCharacterDefinition ch = plugin.getTownsfolkCharacterCatalog().byId(tb.getCharacterId().trim());
            if (ch != null && ch.getDisplayName() != null && !ch.getDisplayName().isBlank()) {
                buyerName = ch.getDisplayName().trim();
            }
        }
        // Run on this tick (tourist systems already run on the world thread). Only mark done on success so a
        // missed/empty listing can retry later in the same visit.
        boolean bought =
            NpcShopSpotBuyerService.tryBuyOneListingOnWorldThread(
                world,
                town.getTownId(),
                plotId,
                buyerName,
                uc.getUuid()
            );
        if (bought) {
            autonomy.setShopPurchaseDoneThisVisit(true);
        }
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

    @Nullable
    private static TouristPlotVisit findPlotVisitNearTarget(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull World world,
        @Nonnull Vector3d target
    ) {
        for (TouristPlotVisit visit : TouristDestinationResolver.listVisitPlots(town, catalog, world)) {
            double dx = visit.entryX() - target.x;
            double dy = visit.entryY() - target.y;
            double dz = visit.entryZ() - target.z;
            if (dx * dx + dy * dy + dz * dz <= 0.25) {
                return visit;
            }
        }
        return null;
    }

    private static long visitDurationMs(long now, int salt, boolean shopPlot) {
        if (shopPlot) {
            return SHOP_VISIT_MIN_MS + Math.abs((now + salt) % (SHOP_VISIT_MAX_MS - SHOP_VISIT_MIN_MS + 1));
        }
        return VISIT_MIN_MS + Math.abs((now + salt) % (VISIT_MAX_MS - VISIT_MIN_MS + 1));
    }

    private static long shopSpotBrowseDurationMs(long now) {
        return SHOP_SPOT_BROWSE_MIN_MS + Math.abs(now % (SHOP_SPOT_BROWSE_MAX_MS - SHOP_SPOT_BROWSE_MIN_MS + 1));
    }

    private static void scheduleNextPoiPick(@Nonnull TouristAutonomyState autonomy, long now, int salt) {
        long span = POI_PICK_MAX_DELAY_MS - POI_PICK_MIN_DELAY_MS + 1;
        autonomy.setNextPoiPickEpochMs(now + POI_PICK_MIN_DELAY_MS + Math.abs((now + salt) % span));
    }

    private static void scheduleNextShopSpotPick(@Nonnull TouristAutonomyState autonomy, long now, int salt) {
        long span = SHOP_SPOT_PICK_MAX_DELAY_MS - SHOP_SPOT_PICK_MIN_DELAY_MS + 1;
        autonomy.setNextPoiPickEpochMs(now + SHOP_SPOT_PICK_MIN_DELAY_MS + Math.abs((now + salt) % span));
    }

    private void finishReturnDespawn(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TouristAutonomyState autonomy,
        @Nonnull TownRecord town,
        @Nonnull World world
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            closePendingDoorsAfterTravelArrival(
                ref,
                store,
                world,
                autonomy,
                tc.getPosition(),
                RETURN_ARRIVE_HORIZONTAL_SQ
            );
        }
        UUID entityUuid = uc.getUuid();
        UUID portalId = autonomy.getHomePortalId();
        commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        world.execute(() ->
            TouristPortalTickService.finalizeTouristDeparture(world, plugin, town, tm, entityUuid, portalId, store)
        );
    }

    private static void closePendingDoorsAfterTravelArrival(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull TouristAutonomyState autonomy,
        @Nonnull Vector3d pos,
        double arriveHorizontalSq
    ) {
        if (autonomy.getPendingOpenDoorsMutable().isEmpty()) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            autonomy.clearPendingDoorClose();
            return;
        }
        Vector3d goal = new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ());
        VillagerDoorUtil.closePendingDoorsAfterGoalReached(
            world,
            store,
            uc.getUuid(),
            pos,
            goal,
            arriveHorizontalSq,
            autonomy.getPendingOpenDoorsMutable()
        );
    }

    private void failTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TouristAutonomyState autonomy,
        long now,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull TownsfolkCharacterBinding tb
    ) {
        if (isReturningTravel(autonomy)) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            TouristPortalRecord portal = resolveHomePortal(world, autonomy);
            if (tc != null
                && portal != null
                && TouristPortalBlockUtil.isNearPortalDespawn(world, portal.getBlockPosition(), tc.getPosition())) {
                finishReturnDespawn(ref, store, commandBuffer, autonomy, town, world);
                return;
            }
            TouristRecord rec = TouristPortalTickService.findTouristRecord(town, tb.getCharacterId());
            if (rec != null && TouristPortalTickService.shouldTouristLeaveNow(rec, store)) {
                finishReturnDespawn(ref, store, commandBuffer, autonomy, town, world);
                return;
            }
            if (!autonomy.isTravelDirectFallback()) {
                applyDirectTravelFallback(npc, autonomy);
                commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
                applyAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
            return;
        }
        if (autonomy.getVisitPlotUuid() != null) {
            // Timed-out travel must not soft-enter VISIT while still outside the plot — that restarts a seek/teleport loop.
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            UUID visitPlotId = autonomy.getVisitPlotUuid();
            PlotInstance plot = TouristDestinationResolver.findVisitPlot(town, visitPlotId);
            if (tc != null
                && plot != null
                && TouristDestinationResolver.isInsidePlotFootprint(
                    tc.getPosition().x,
                    tc.getPosition().z,
                    plot,
                    TouristDestinationResolver.plotEdgePadding()
                )) {
                autonomy.setPhase(TouristAutonomyState.PHASE_VISIT);
                autonomy.clearTravelWaypoints();
                commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                clearAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
            autonomy.clearVisitPlot();
        }
        autonomy.setPhase(TouristAutonomyState.PHASE_IDLE);
        autonomy.clearTravelWaypoints();
        autonomy.clearPendingDoorClose();
        autonomy.setNextDecisionEpochMs(now + 5000L);
        commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        clearAutonomyRoleState(ref, npc, commandBuffer);
    }

    @Nullable
    private static TouristPortalRecord resolveHomePortal(
        @Nonnull World world,
        @Nonnull TouristAutonomyState autonomy
    ) {
        UUID portalId = autonomy.getHomePortalId();
        if (portalId == null) {
            return null;
        }
        return AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, AetherhavenPlugin.get()).get(portalId);
    }

    private static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        TimeResource tr = store.getResource(TimeResource.getResourceType());
        return tr != null ? tr.getNow().toEpochMilli() : System.currentTimeMillis();
    }

    private static void applyAutonomyRoleState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null) {
            return;
        }
        if (npc.getRole().getStateSupport().getStateHelper().getStateIndex(AetherhavenConstants.NPC_STATE_AUTONOMY_POI) < 0) {
            return;
        }
        npc.getRole().getStateSupport().setState(ref, AetherhavenConstants.NPC_STATE_AUTONOMY_POI, null, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    public static void applyAutonomyRoleStateOnStore(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull Store<EntityStore> store
    ) {
        if (!supportsAutonomyPoiRoleState(npc)) {
            return;
        }
        npc.getRole().getStateSupport().setState(ref, AetherhavenConstants.NPC_STATE_AUTONOMY_POI, null, store);
        store.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    public static void clearAutonomyRoleStateOnStore(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull Store<EntityStore> store
    ) {
        if (npc.getRole() == null) {
            return;
        }
        String state = npc.getRole().getStateSupport().getStateName();
        if (state == null || !state.startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI)) {
            return;
        }
        npc.getRole().getStateSupport().setState(ref, "Idle", null, store);
        npc.playAnimation(ref, AnimationSlot.Action, null, store);
        npc.playAnimation(ref, AnimationSlot.Emote, null, store);
        npc.playAnimation(ref, AnimationSlot.Status, null, store);
        store.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    public static void clearAutonomyRoleState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null) {
            return;
        }
        String state = npc.getRole().getStateSupport().getStateName();
        if (state == null || !state.startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI)) {
            return;
        }
        npc.getRole().getStateSupport().setState(ref, "Idle", null, commandBuffer);
        NpcAnimationPlayback.clearOverlaySlots(ref, npc, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }
}
