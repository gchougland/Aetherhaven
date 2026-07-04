package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.joml.Vector3d;
import org.joml.Vector3i;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Commits building-staff brush placement on the entity tick so block writes align with marker growth (preview updates
 * every entity tick; charging interactions only tick on client sync and can lag by hundreds of ms).
 */
public final class BuildingStaffAssemblyChannelExecutor {
    private static final int TRACER_STEPS = 14;
    private static final Color TRACER_TINT = new Color((byte) 170, (byte) 255, (byte) 230);
    private static final ConcurrentHashMap<UUID, Long> LAST_NO_MANA_HINT_NS = new ConcurrentHashMap<>();
    private static final long NO_MANA_HINT_INTERVAL_NS = 2_000_000_000L;

    private BuildingStaffAssemblyChannelExecutor() {}

    /**
     * When the channel timer has elapsed, place or break blocks in the locked brush volume.
     *
     * @return {@code true} if the brush lock was released after a placement attempt (charged gate passed).
     */
    public static boolean tryExecuteChargedBrush(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull BuildingStaffAssemblyChannelComponent channel,
        @Nonnull UUID playerUuid,
        long nowNs
    ) {
        if (!channel.hasBrushLock() || !channel.isFresh(nowNs)) {
            return false;
        }
        long elapsed = nowNs - channel.getChannelStartNs();
        if (elapsed < BuildingStaffAssemblyChannelComponent.CHANNEL_DURATION_NS) {
            return false;
        }
        Vector3i activeCell = channel.getBrushLockWorld();
        PlotAssemblyJob job = PlotAssemblyService.findJobContainingPreview(world, plugin, activeCell);
        if (job == null) {
            channel.resetChargeSession();
            return true;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownOwningPlot(job.plotId());
        if (town == null) {
            channel.resetChargeSession();
            return true;
        }
        PlotInstance plot = town.findPlotById(job.plotId());
        if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
            channel.resetChargeSession();
            return true;
        }
        if (!town.playerCanManageConstructions(playerUuid)) {
            channel.resetChargeSession();
            return true;
        }
        PlotAssemblyPhase phase = AssemblyWorldRegistry.phase(world, job.plotId());
        if (phase == null) {
            channel.resetChargeSession();
            return true;
        }
        int brushRadiusBlocks = channel.getBrushChebyshevRadius();
        if (phase == PlotAssemblyPhase.PLACING) {
            if (PlotAssemblyService.resolveFrontierPlacementIndex(world, job, plot, activeCell) < 0) {
                // No frontier cell under the brush — if growth is exhausted, finalize like finishassembly.
                PlotAssemblyService.tryFinalizeWhenFrontierExhausted(world, plugin, town, plot, job);
                channel.resetChargeSession();
                return true;
            }
        } else if (PlotAssemblyService.findClearingJobForObstructedCell(world, plugin, activeCell) == null) {
            channel.resetChargeSession();
            return true;
        }
        if (!BuildingStaffMana.canAffordBlock(playerRef, store)) {
            maybeNotifyNoMana(playerRef, store, playerUuid, nowNs);
            return false;
        }
        boolean anyAction = false;
        if (phase == PlotAssemblyPhase.CLEARING) {
            ArrayList<Vector3i> batch =
                PlotAssemblyService.obstructionCellsNearChebyshev(world, job, plot, activeCell, brushRadiusBlocks);
            for (int bi = 0; bi < batch.size(); bi++) {
                if (!BuildingStaffMana.canAffordBlock(playerRef, store)) {
                    maybeNotifyNoMana(playerRef, store, playerUuid, nowNs);
                    break;
                }
                Vector3i cell = batch.get(bi);
                PlotAssemblyService.ClearingAdvanceOutcome outcome =
                    PlotAssemblyService.advanceClearingAtCell(
                        world, plugin, commandBuffer, store, town, plot, job, cell, playerUuid
                    );
                if (!outcome.progressed()) {
                    continue;
                }
                if (outcome.brokeBlock()) {
                    if (!BuildingStaffMana.consumeForBlock(playerRef, commandBuffer)) {
                        break;
                    }
                    anyAction = true;
                }
                if (plot.getState() != PlotInstanceState.ASSEMBLING) {
                    break;
                }
                if (AssemblyWorldRegistry.phase(world, job.plotId()) != PlotAssemblyPhase.CLEARING) {
                    break;
                }
            }
        } else {
            IntArrayList batch =
                PlotAssemblyService.frontierPlacementIndicesNearChebyshev(
                    world,
                    job,
                    plot,
                    activeCell,
                    brushRadiusBlocks
                );
            for (int bi = 0; bi < batch.size(); bi++) {
                if (!BuildingStaffMana.canAffordBlock(playerRef, store)) {
                    maybeNotifyNoMana(playerRef, store, playerUuid, nowNs);
                    break;
                }
                int idx = batch.getInt(bi);
                PlotAssemblyService.PlacementAdvanceOutcome outcome =
                    PlotAssemblyService.advancePlacementAtIndex(
                        world, plugin, store, town, plot, job, idx, true, playerUuid, true
                    );
                if (!outcome.progressed()) {
                    continue;
                }
                if (outcome.wroteBlock()) {
                    if (!BuildingStaffMana.consumeForBlock(playerRef, commandBuffer)) {
                        break;
                    }
                    anyAction = true;
                }
                if (plot.getState() != PlotInstanceState.ASSEMBLING) {
                    break;
                }
            }
        }
        channel.releaseBrushLockAfterPlacement(nowNs);
        if (anyAction) {
            spawnTracerBeadsAlongBeam(playerRef, store, activeCell);
            Vector3d p = new Vector3d(activeCell.x + 0.5, activeCell.y + 0.5, activeCell.z + 0.5);
            ParticleUtil.spawnParticleEffect(AetherhavenConstants.BUILDING_STAFF_STEP_PARTICLE_SYSTEM_ID, p, store);
        }
        return true;
    }

    private static void maybeNotifyNoMana(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        long nowNs
    ) {
        Long prev = LAST_NO_MANA_HINT_NS.get(playerUuid);
        if (prev != null && nowNs - prev < NO_MANA_HINT_INTERVAL_NS) {
            return;
        }
        LAST_NO_MANA_HINT_NS.put(playerUuid, nowNs);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("aetherhaven_misc.aetherhaven.assembly.staff.no_mana"));
        }
    }

    private static void spawnTracerBeadsAlongBeam(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i hit
    ) {
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        HeadRotation head = store.getComponent(playerRef, HeadRotation.getComponentType());
        Vector3d forward = head != null ? head.getDirection() : directionFromRotation(transform.getRotation());
        Vector3d tip = new Vector3d(transform.getPosition()).add(0.0, 1.32, 0.0).add(new Vector3d(forward).mul(0.42));
        Vector3d target = new Vector3d(hit.x + 0.5, hit.y + 0.52, hit.z + 0.5);
        List<Ref<EntityStore>> nearby = particleRecipientsForPlayer(playerRef, tip, store);
        for (int i = 1; i < TRACER_STEPS; i++) {
            double t = i / (double) TRACER_STEPS;
            double x = tip.x + (target.x - tip.x) * t;
            double y = tip.y + (target.y - tip.y) * t;
            double z = tip.z + (target.z - tip.z) * t;
            ParticleUtil.spawnParticleEffect(
                AetherhavenConstants.BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID,
                x,
                y,
                z,
                0.0F,
                0.0F,
                0.0F,
                0.42F,
                TRACER_TINT,
                null,
                nearby,
                store
            );
        }
    }

    @Nonnull
    private static List<Ref<EntityStore>> particleRecipientsForPlayer(
        @Nonnull Ref<EntityStore> self,
        @Nonnull Vector3d tip,
        @Nonnull Store<EntityStore> store
    ) {
        SpatialResource<Ref<EntityStore>, EntityStore> spatial = store.getResource(EntityModule.get().getPlayerSpatialResourceType());
        List<Ref<EntityStore>> fromSpatial = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(tip, ParticleUtil.DEFAULT_PARTICLE_DISTANCE, fromSpatial);
        ArrayList<Ref<EntityStore>> out = new ArrayList<>(fromSpatial.size() + 1);
        for (int i = 0; i < fromSpatial.size(); i++) {
            Ref<EntityStore> r = fromSpatial.get(i);
            if (r != null && r.isValid()) {
                out.add(r);
            }
        }
        if (self.isValid()) {
            boolean hasSelf = false;
            for (int i = 0; i < out.size(); i++) {
                if (self.equals(out.get(i))) {
                    hasSelf = true;
                    break;
                }
            }
            if (!hasSelf) {
                out.add(self);
            }
        }
        return out;
    }

    @Nonnull
    private static Vector3d directionFromRotation(@Nonnull Rotation3fc euler) {
        double pitch = euler.pitch();
        double yaw = euler.yaw();
        double len = Math.cos(pitch);
        double x = len * -Math.sin(yaw);
        double y = Math.sin(pitch);
        double z = len * -Math.cos(yaw);
        return new Vector3d(x, y, z);
    }
}
