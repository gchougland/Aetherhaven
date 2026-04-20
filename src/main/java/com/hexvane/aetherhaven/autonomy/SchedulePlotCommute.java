package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Paths residents onto their scheduled plot footprint before local {@code WanderInRect} (anchored at the NPC). */
public final class SchedulePlotCommute {
    private static final int EDGE_PADDING_BLOCKS = 2;

    private SchedulePlotCommute() {}

    /**
     * If {@code preferredPlotId} resolves to a complete plot and the NPC is outside its horizontal footprint, starts
     * {@link VillagerAutonomyState#PHASE_TRAVEL} toward the plot center (synthetic POI {@link
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
        if (TownVillagerBinding.isVisitorKind(binding.getKind())) {
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
        int cx = (fp.getMinX() + fp.getMaxX()) / 2;
        int cz = (fp.getMinZ() + fp.getMaxZ()) / 2;
        int yScan = (int) Math.floor(tc.getPosition().y) + 8;
        int standY = VillagerBlockUtil.findStandY(world, cx, cz, yScan);
        if (standY == Integer.MIN_VALUE) {
            standY = VillagerBlockUtil.findStandY(world, plot.getSignX(), plot.getSignZ(), yScan);
        }
        if (standY == Integer.MIN_VALUE) {
            return false;
        }
        double tx = cx + 0.5;
        double tz = cz + 0.5;
        double ty = standY + 0.02;
        autonomy.setPhase(VillagerAutonomyState.PHASE_TRAVEL);
        autonomy.setTravelTarget(tx, ty, tz, AetherhavenConstants.SCHEDULE_ZONE_COMMUTE_POI_ID);
        autonomy.setPathFailureReason("");
        autonomy.setTravelStuckTicks(0);
        npc.setLeashPoint(new Vector3d(tx, ty, tz));
        autonomy.setNextDecisionEpochMs(now + 120_000L);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        VillagerAutonomySystem.applyAutonomyRoleState(ref, npc, commandBuffer);
        return true;
    }
}
