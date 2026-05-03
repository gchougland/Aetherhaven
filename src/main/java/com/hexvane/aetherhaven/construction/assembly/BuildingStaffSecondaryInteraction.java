package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Hold secondary (charging): same cadence as passive ticks while the client reports the input held; ends when
 * released like vanilla staff/wand charge interactions.
 */
public final class BuildingStaffSecondaryInteraction extends ChargingInteraction {
    /** Client still holding the charge input ({@link com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction} convention). */
    private static final float CHARGING_HELD = -1.0F;

    private static final double RAY_MAX = 14.0;
    private static final long STREAM_INTERVAL_NS = 72_000_000L;
    private static final int TRACER_STEPS = 14;
    private static final Color TRACER_TINT = new Color((byte) 170, (byte) 255, (byte) 230);
    private static final ConcurrentHashMap<UUID, Long> LAST_STREAM_NS = new ConcurrentHashMap<>();
    /** Short creative brush ticks while charging (non-looping — stops when RMB releases). */
    private static final ConcurrentHashMap<UUID, Long> LAST_BRUSH_AMBIENT_NS = new ConcurrentHashMap<>();
    private static final long BRUSH_AMBIENT_INTERVAL_NS = 200_000_000L;

    @Nonnull
    public static final BuilderCodec<BuildingStaffSecondaryInteraction> CODEC =
        BuilderCodec
            .builder(BuildingStaffSecondaryInteraction.class, BuildingStaffSecondaryInteraction::new, ChargingInteraction.CODEC)
            .documentation(
                "Hold secondary for half a second; aim locks until you release or the target becomes invalid. Preview cubes grow, then frontier blocks in that area are placed."
            )
            .build();

    @Override
    protected void tick0(
        boolean firstRun,
        float time,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (context.getState().state != InteractionState.NotFinished) {
            return;
        }
        InteractionSyncData clientData = context.getClientState();
        if (clientData == null || clientData.chargeValue != CHARGING_HELD) {
            return;
        }

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        ItemStack hand = context.getHeldItem();
        if (hand == null
            || hand.isEmpty()
            || !AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(hand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        UUID uuid = uc.getUuid();
        long nowNs = System.nanoTime();
        maybeSpawnDirectedStream(playerRef, store, uuid, nowNs);

        World world = store.getExternalData().getWorld();
        BuildingStaffAssemblyChannelComponent channel = commandBuffer.getComponent(playerRef, BuildingStaffAssemblyChannelComponent.getComponentType());
        if (channel == null) {
            channel = new BuildingStaffAssemblyChannelComponent();
            commandBuffer.addComponent(playerRef, BuildingStaffAssemblyChannelComponent.getComponentType(), channel);
        }
        if (firstRun) {
            channel.resetChargeSession();
        }
        if (!channel.hasBrushLock()) {
            Vector3i rayHit = AssemblyPreviewRay.findPenetratingPreviewCellHit(playerRef, world, plugin, RAY_MAX, store);
            if (rayHit == null) {
                return;
            }
            PlotAssemblyJob jPick = PlotAssemblyService.findJobContainingPreview(world, plugin, rayHit);
            if (jPick == null) {
                return;
            }
            TownRecord tPick = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownOwningPlot(jPick.plotId());
            if (tPick == null) {
                return;
            }
            PlotInstance pPick = tPick.findPlotById(jPick.plotId());
            if (pPick == null || pPick.getState() != PlotInstanceState.ASSEMBLING) {
                return;
            }
            if (!tPick.playerCanManageConstructions(uuid)) {
                return;
            }
            if (PlotAssemblyService.resolveFrontierPlacementIndex(jPick, pPick, rayHit) < 0) {
                return;
            }
            channel.setBrushLock(rayHit);
        }
        Vector3i activeCell = channel.getBrushLockWorld();
        PlotAssemblyJob job = PlotAssemblyService.findJobContainingPreview(world, plugin, activeCell);
        if (job == null) {
            channel.resetChargeSession();
            return;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownOwningPlot(job.plotId());
        if (town == null) {
            channel.resetChargeSession();
            return;
        }
        PlotInstance plot = town.findPlotById(job.plotId());
        if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
            channel.resetChargeSession();
            return;
        }
        if (!town.playerCanManageConstructions(uuid)) {
            channel.resetChargeSession();
            return;
        }
        if (PlotAssemblyService.resolveFrontierPlacementIndex(job, plot, activeCell) < 0) {
            channel.resetChargeSession();
            return;
        }
        channel.beginOrContinueChannel(activeCell, nowNs);
        maybePlayBrushAmbientDuringChannel(uuid, playerRef, store, nowNs, channel);
        long elapsed = nowNs - channel.getChannelStartNs();
        if (elapsed < BuildingStaffAssemblyChannelComponent.CHANNEL_DURATION_NS) {
            return;
        }
        IntArrayList batch =
            PlotAssemblyService.frontierPlacementIndicesNearChebyshev(
                job,
                plot,
                activeCell,
                AetherhavenConstants.BUILDING_STAFF_ASSEMBLY_BRUSH_CHEBYSHEV_RADIUS
            );
        boolean anyPlaced = false;
        for (int bi = 0; bi < batch.size(); bi++) {
            int idx = batch.getInt(bi);
            if (!PlotAssemblyService.advancePlacementAtIndex(world, plugin, store, town, plot, job, idx, true, uuid)) {
                continue;
            }
            anyPlaced = true;
            if (plot.getState() != PlotInstanceState.ASSEMBLING) {
                break;
            }
        }
        channel.releaseBrushLockAfterPlacement(nowNs);
        if (anyPlaced) {
            spawnTracerBeadsAlongBeam(playerRef, store, activeCell);
            Vector3d p = new Vector3d(activeCell.x + 0.5, activeCell.y + 0.5, activeCell.z + 0.5);
            ParticleUtil.spawnParticleEffect(AetherhavenConstants.BUILDING_STAFF_STEP_PARTICLE_SYSTEM_ID, p, store);
        }
    }

    private static void maybePlayBrushAmbientDuringChannel(
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        long nowNs,
        @Nonnull BuildingStaffAssemblyChannelComponent channel
    ) {
        long elapsed = nowNs - channel.getChannelStartNs();
        if (elapsed >= BuildingStaffAssemblyChannelComponent.CHANNEL_DURATION_NS || !channel.hasActiveTarget()) {
            return;
        }
        Long prev = LAST_BRUSH_AMBIENT_NS.get(playerUuid);
        if (prev != null && nowNs - prev < BRUSH_AMBIENT_INTERVAL_NS) {
            return;
        }
        LAST_BRUSH_AMBIENT_NS.put(playerUuid, nowNs);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        int idx = SoundEvent.getAssetMap().getIndex(AetherhavenConstants.BUILDING_STAFF_BRUSH_AMBIENT_SOUND_EVENT_ID);
        if (idx != 0 && idx != Integer.MIN_VALUE) {
            SoundUtil.playSoundEvent2dToPlayer(pr, idx, SoundCategory.UI);
        }
    }

    private static void maybeSpawnDirectedStream(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        long nowNs
    ) {
        Long last = LAST_STREAM_NS.get(playerUuid);
        if (last != null && nowNs - last < STREAM_INTERVAL_NS) {
            return;
        }
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        HeadRotation head = store.getComponent(playerRef, HeadRotation.getComponentType());
        Vector3d forward = head != null ? head.getDirection() : directionFromRotation(transform.getRotation());
        Vector3d tip = transform.getPosition().clone().add(0.0, 1.32, 0.0).add(forward.clone().scale(0.42));
        float yaw = head != null ? head.getRotation().getYaw() : transform.getRotation().getYaw();
        float pitch = head != null ? head.getRotation().getPitch() : transform.getRotation().getPitch();
        float roll = head != null ? head.getRotation().getRoll() : transform.getRotation().getRoll();
        List<Ref<EntityStore>> nearby = particleRecipientsForPlayer(playerRef, tip, store);
        ParticleUtil.spawnParticleEffect(
            AetherhavenConstants.BUILDING_STAFF_STREAM_PARTICLE_SYSTEM_ID,
            tip.getX(),
            tip.getY(),
            tip.getZ(),
            yaw,
            pitch,
            roll,
            1.0F,
            null,
            null,
            nearby,
            store
        );
        LAST_STREAM_NS.put(playerUuid, nowNs);
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
        Vector3d tip = transform.getPosition().clone().add(0.0, 1.32, 0.0).add(forward.clone().scale(0.42));
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
    private static Vector3d directionFromRotation(@Nonnull Vector3f euler) {
        double pitch = euler.getPitch();
        double yaw = euler.getYaw();
        double len = Math.cos(pitch);
        double x = len * -Math.sin(yaw);
        double y = Math.sin(pitch);
        double z = len * -Math.cos(yaw);
        return new Vector3d(x, y, z);
    }
}
