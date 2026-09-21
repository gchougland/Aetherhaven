package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.autonomy.pathnav.PathNavGraphService;
import com.hexvane.aetherhaven.autonomy.pathnav.PathNavTravelWaypoints;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.IntBinaryOperator;
import org.joml.Vector3i;

/** Paths residents onto their scheduled plot footprint before local {@code WanderInRect} (anchored at the NPC). */
public final class SchedulePlotCommute {
    private static final int EDGE_PADDING_BLOCKS = 2;

    private SchedulePlotCommute() {}

    /**
     * If {@code preferredPlotId} resolves to a complete plot and the NPC is outside its horizontal footprint, starts
     * {@link VillagerAutonomyState#PHASE_TRAVEL} toward clear ground inside the plot (synthetic POI {@link
     * AetherhavenConstants#SCHEDULE_ZONE_COMMUTE_POI_ID}).
     *
     * @return true if travel was started
     */
    public static boolean tryBeginIfOffSchedulePlot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return false;
        }
        if (TownVillagerBinding.isScheduleSuppressedKind(binding.getKind())) {
            return false;
        }
        if (VillagerAutonomySystem.skipsPoiAutonomy(binding, npc)) {
            return false;
        }
        UUID plotUuid = binding.getPreferredPlotId();
        if (plotUuid == null) {
            return false;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            return false;
        }
        PlotInstance plot = town.findPlotById(plotUuid);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return false;
        }
        PlotFootprintRecord fp = plot.toFootprint();
        double x = tc.getPosition().x;
        double z = tc.getPosition().z;
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int pad = EDGE_PADDING_BLOCKS;
        if (bx >= fp.getMinX() - pad
            && bx <= fp.getMaxX() + pad
            && bz >= fp.getMinZ() - pad
            && bz <= fp.getMaxZ() + pad) {
            return false;
        }
        int npcFeetY = (int) Math.floor(tc.getPosition().y);
        ConstructionDefinition cdef = plugin.getConstructionCatalog().get(plot.getConstructionId());
        AutonomyNavBounds.NavVerticalRange range = AutonomyNavBounds.rangeForPlotFootprint(fp, cdef);
        Vector3d destination = safePlotStand(world, fp, cdef, npcFeetY);
        if (destination == null) return false;
        double tx = destination.x;
        double tz = destination.z;
        double ty = destination.y;
        autonomy.setPhase(VillagerAutonomyState.PHASE_TRAVEL);
        autonomy.setTravelTarget(tx, ty, tz, AetherhavenConstants.SCHEDULE_ZONE_COMMUTE_POI_ID);
        autonomy.setPathFailureReason("");
        autonomy.setTravelStuckTicks(0);
        Vector3d finalTarget = new Vector3d(tx, ty, tz);
        AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
        PathNavGraphService.PathNavFindResult navResult =
            AetherhavenWorldRegistries
                .getOrCreatePathNavGraphService(world)
                .findRouteResult(town.getTownId(), tc.getPosition(), finalTarget, plugin.getConfig().get());
        PathNavGraphService.logPathfindingSkip(
            plugin.getConfig().get(),
            "schedule_commute",
            town.getTownId(),
            AetherhavenConstants.SCHEDULE_ZONE_COMMUTE_POI_ID,
            navResult
        );
        var route = navResult.waypoints();
        if (!route.isEmpty()) {
            route =
                PathNavTravelWaypoints.prepareForSeek(
                    world,
                    tc.getPosition(),
                    route,
                    finalTarget,
                    (int) Math.floor(tc.getPosition().y),
                    plugin.getConfig().get().getPathNavNodeSpacing(),
                    range
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
        autonomy.setNextDecisionEpochMs(now + 120_000L);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        VillagerAutonomySystem.applyAutonomyRoleState(ref, npc, commandBuffer);
        return true;
    }

    /** Search neighboring columns rather than borrowing a floor height for an obstructed center. */
    @Nullable
    public static Vector3d safePlotStand(@Nonnull World world, @Nonnull PlotFootprintRecord fp,
                                         @Nullable ConstructionDefinition def, int feetHint) {
        var range = AutonomyNavBounds.rangeForPlotFootprint(fp, def);
        int floorHint = range != null ? range.minFeetY() : fp.getMinY() + 1;
        var cell = findClearColumn(fp.getMinX(), fp.getMaxX(), fp.getMinZ(), fp.getMaxZ(),
            (x, z) -> VillagerBlockUtil.findStandYForNav(world, x, z, floorHint, feetHint, range));
        return cell == null ? null : new Vector3d(cell.x + 0.5,
            VillagerBlockUtil.resolveFeetYForStandCell(world, cell.x, cell.y, cell.z), cell.z + 0.5);
    }

    @Nullable
    static Vector3i findClearColumn(int minX, int maxX, int minZ, int maxZ,
                                            IntBinaryOperator standY) {
        int cx = minX + (maxX - minX) / 2;
        int cz = minZ + (maxZ - minZ) / 2;
        int radius = Math.min(16, Math.max(maxX - minX, maxZ - minZ));
        for (int r = 0; r <= radius; r++) {
            for (int x = Math.max(minX, cx - r); x <= Math.min(maxX, cx + r); x++) {
                for (int z = Math.max(minZ, cz - r); z <= Math.min(maxZ, cz + r); z++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != r) continue;
                    int y = standY.applyAsInt(x, z);
                    if (y != Integer.MIN_VALUE) return new Vector3i(x, y, z);
                }
            }
        }
        return null;
    }

}
