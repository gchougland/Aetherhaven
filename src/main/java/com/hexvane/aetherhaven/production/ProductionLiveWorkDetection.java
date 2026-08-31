package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.autonomy.PoiScoring;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTickState;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hexvane.aetherhaven.schedule.VillagerScheduleRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Decides whether a loaded production worker should accrue live ticks this entity frame.
 *
 * <p>Strict {@link VillagerAutonomyState#PHASE_USE} at a WORK POI is insufficient: {@code WORK_SURFACE} spots end USE
 * every ~8s so the villager can rotate stations, and the production UI bar stalls during travel/idle even though the
 * worker is visibly on shift. During the scheduled {@code work} segment we also accrue while the worker is present at
 * their job plot (inside the footprint or near a work POI), except during an active break USE (eat / sleep / fun).
 */
public final class ProductionLiveWorkDetection {
    /** Horizontal slack when standing beside a work marker just outside the plot AABB. */
    private static final double WORK_POI_REACH_HORIZONTAL_SQ = 5.0 * 5.0;

    private ProductionLiveWorkDetection() {}

    public static boolean shouldAccrueEntityTick(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        @Nonnull NPCEntity npc,
        @Nonnull PlotInstance jobPlot,
        @Nonnull UUID jobPlotId,
        @Nonnull PoiRegistry poiRegistry,
        @Nonnull VillagerDefinitionCatalog villagerCatalog,
        @Nonnull VillagerScheduleRegistry scheduleRegistry,
        long nowMs
    ) {
        if (!isOnWorkSchedule(store, ref, npc, villagerCatalog, scheduleRegistry)) {
            return false;
        }

        UUID targetPoiId = autonomy.getTargetPoiUuid();
        PoiEntry targetPoi = targetPoiId != null ? poiRegistry.get(targetPoiId) : null;

        if (isStrictWorkUse(autonomy, targetPoi, jobPlotId, nowMs)) {
            return true;
        }

        if (isActiveNonWorkBreakUse(autonomy, targetPoi, nowMs, store, ref)) {
            return false;
        }

        return isPresentAtWorkplace(store, ref, binding, autonomy, jobPlot, jobPlotId, poiRegistry);
    }

    private static boolean isStrictWorkUse(
        @Nonnull VillagerAutonomyState autonomy,
        @Nullable PoiEntry poi,
        @Nonnull UUID jobPlotId,
        long nowMs
    ) {
        if (autonomy.getPhase() != VillagerAutonomyState.PHASE_USE || poi == null || nowMs >= autonomy.getPhaseEndEpochMs()) {
            return false;
        }
        return isWorkPoiOnPlot(poi, jobPlotId);
    }

    private static boolean isActiveNonWorkBreakUse(
        @Nonnull VillagerAutonomyState autonomy,
        @Nullable PoiEntry poi,
        long nowMs,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        if (autonomy.getPhase() != VillagerAutonomyState.PHASE_USE || poi == null || nowMs >= autonomy.getPhaseEndEpochMs()) {
            return false;
        }
        if (PoiScoring.isWorkPoi(poi)) {
            return false;
        }
        if (PoiScoring.isEatPoi(poi)
            || poi.getInteractionKind() == PoiInteractionKind.SLEEP
            || poi.getTags().contains("SLEEP")
            || poi.getTags().contains("ENERGY")
            || poi.getInteractionKind() == PoiInteractionKind.SIT
            || poi.getTags().contains("SIT")
            || poi.getTags().contains("FUN")) {
            return true;
        }
        VillagerNeeds needs = store.getComponent(ref, VillagerNeeds.getComponentType());
        if (needs != null && PoiScoring.needsBreakForSchedule(needs, com.hexvane.aetherhaven.shopspot.ShopSpotOpenService.isGameDay(store))) {
            return true;
        }
        return false;
    }

    private static boolean isPresentAtWorkplace(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        @Nonnull PlotInstance jobPlot,
        @Nonnull UUID jobPlotId,
        @Nonnull PoiRegistry poiRegistry
    ) {
        if (autonomy.getPhase() == VillagerAutonomyState.PHASE_TRAVEL) {
            UUID targetId = autonomy.getTargetPoiUuid();
            if (targetId != null) {
                PoiEntry travelTarget = poiRegistry.get(targetId);
                if (travelTarget != null
                    && isWorkPoiOnPlot(travelTarget, jobPlotId)
                    && PoiScoring.matchesWorkPoiForBindingKind(travelTarget, binding.getKind())) {
                    return true;
                }
            }
        }

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return false;
        }
        Vector3d pos = tc.getPosition();
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);
        var fp = jobPlot.toFootprint();
        // Footprint includes roof slabs; standing on the roof must not count as being at work.
        int roofCut = Math.max(fp.getMinY(), fp.getMaxY() - 4);
        if (bx >= fp.getMinX()
            && bx <= fp.getMaxX()
            && bz >= fp.getMinZ()
            && bz <= fp.getMaxZ()
            && by >= fp.getMinY()
            && by <= roofCut) {
            return true;
        }
        return isNearWorkPoiOnPlot(pos, binding, jobPlotId, poiRegistry);
    }

    private static boolean isNearWorkPoiOnPlot(
        @Nonnull Vector3d pos,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID jobPlotId,
        @Nonnull PoiRegistry poiRegistry
    ) {
        List<PoiEntry> pois = poiRegistry.listByTown(binding.getTownId());
        for (PoiEntry poi : pois) {
            if (!isWorkPoiOnPlot(poi, jobPlotId) || !PoiScoring.matchesWorkPoiForBindingKind(poi, binding.getKind())) {
                continue;
            }
            double dx = pos.x - (poi.getX() + 0.5);
            double dy = pos.y - poi.getY();
            double dz = pos.z - (poi.getZ() + 0.5);
            if (dx * dx + dz * dz <= WORK_POI_REACH_HORIZONTAL_SQ && Math.abs(dy) <= 2.5) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWorkPoiOnPlot(@Nonnull PoiEntry poi, @Nonnull UUID jobPlotId) {
        return PoiScoring.isWorkPoi(poi) && Objects.equals(poi.getPlotId(), jobPlotId);
    }

    private static boolean isOnWorkSchedule(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerDefinitionCatalog villagerCatalog,
        @Nonnull VillagerScheduleRegistry scheduleRegistry
    ) {
        VillagerScheduleTickState sched = store.getComponent(ref, VillagerScheduleTickState.getComponentType());
        if (sched != null && PoiScoring.isWorkScheduleSegment(sched.getLastAppliedScheduleSegment())) {
            return true;
        }
        String roleId = npc.getRoleName();
        if (roleId == null || roleId.isBlank()) {
            return false;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return false;
        }
        VillagerScheduleDefinition def = villagerCatalog.effectiveSchedule(roleId.trim(), scheduleRegistry);
        if (def == null || def.getTransitions().isEmpty()) {
            return false;
        }
        String loc = VillagerScheduleResolver.activeLocationSymbol(def, wtr.getGameDateTime());
        return PoiScoring.isWorkScheduleSegment(loc);
    }
}
