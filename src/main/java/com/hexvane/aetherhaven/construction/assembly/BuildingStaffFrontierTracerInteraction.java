package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Primary (LMB): spawn a short-lived bolt from the staff tip to the nearest permitted assembly frontier cell within
 * range; passes through blocks and vanishes at the target.
 */
public final class BuildingStaffFrontierTracerInteraction extends SimpleInstantInteraction {
    /** Slow flight so players can follow the bolt; paired with {@link AetherhavenConstants#BUILDING_STAFF_GUIDE_TRAIL_PARTICLE_SYSTEM_ID}. */
    private static final float TRACER_SPEED_BLOCKS_PER_SEC = 8.5F;
    private static final float TRACER_MODEL_SCALE = 0.52F;
    /**
     * Bounding-box edge length for networking LOD ({@code LegacyLODCull} vs thin scaled Ice_Bolt mesh); larger intangible box keeps
     * transform/model/trail updates streamed for the whole flight.
     */
    private static final double TRACER_LOD_BOX_SIZE = 0.84;
    /**
     * Vanilla bolt model with mesh + particle trails ({@code Server/Models/Projectiles/Spells/Ice_Bolt.json}) — same class
     * of networked entity as core {@link com.hypixel.hytale.server.core.modules.projectile.ProjectileModule} spawn.
     */
    private static final String TRACER_MODEL_ASSET_ID = "Ice_Bolt";
    private static final long PRIMARY_COOLDOWN_NS = 280_000_000L;
    private static final ConcurrentHashMap<UUID, Long> LAST_PRIMARY_NS = new ConcurrentHashMap<>();

    @Nonnull
    public static final BuilderCodec<BuildingStaffFrontierTracerInteraction> CODEC =
        BuilderCodec
            .builder(BuildingStaffFrontierTracerInteraction.class, BuildingStaffFrontierTracerInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Building staff primary: homing visual tracer to the nearest in-range frontier preview cell.")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    protected void simulateFirstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        // Server spawns the entity; avoid duplicating on the predicting client.
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        if (type != InteractionType.Primary) {
            context.getState().state = InteractionState.Failed;
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
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (hand == null
            || hand.isEmpty()
            || !AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(hand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUID playerUuid = uc.getUuid();
        long nowNs = System.nanoTime();
        Long prev = LAST_PRIMARY_NS.get(playerUuid);
        if (prev != null && nowNs - prev < PRIMARY_COOLDOWN_NS) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = store.getExternalData().getWorld();
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        HeadRotation headRot = store.getComponent(playerRef, HeadRotation.getComponentType());
        Vector3d tip = staffTip(transform, headRot);
        ArrayList<Vector3i> cells = new ArrayList<>();
        AssemblyFrontierWorldCells.collectWithinDefaultRange(world, plugin, tip, cells);
        if (cells.isEmpty()) {
            failWithHint(playerRef, store);
            context.getState().state = InteractionState.Failed;
            return;
        }
        Vector3i best = null;
        double bestSq = Double.MAX_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            Vector3i cell = cells.get(i);
            PlotAssemblyJob job = PlotAssemblyService.findJobContainingPreview(world, plugin, cell);
            if (job == null) {
                continue;
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownOwningPlot(job.plotId());
            if (town == null) {
                continue;
            }
            PlotInstance plot = town.findPlotById(job.plotId());
            if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            if (!town.playerCanManageConstructions(playerUuid)) {
                continue;
            }
            if (PlotAssemblyService.resolveFrontierPlacementIndex(job, plot, cell) < 0) {
                continue;
            }
            double cx = cell.x + 0.5;
            double cy = cell.y + 0.5;
            double cz = cell.z + 0.5;
            double dx = cx - tip.x;
            double dy = cy - tip.y;
            double dz = cz - tip.z;
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < bestSq) {
                bestSq = dSq;
                best = cell;
            }
        }
        if (best == null) {
            failWithHint(playerRef, store);
            context.getState().state = InteractionState.Failed;
            return;
        }
        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(TRACER_MODEL_ASSET_ID);
        if (modelAsset == null) {
            failWithHint(playerRef, store);
            context.getState().state = InteractionState.Failed;
            return;
        }
        Model model = Model.createScaledModel(modelAsset, TRACER_MODEL_SCALE);
        double tx = best.x + 0.5;
        double ty = best.y + 0.5;
        double tz = best.z + 0.5;
        Vector3d spawnPos = tip.clone();
        Vector3d toTarget = new Vector3d(tx - spawnPos.x, ty - spawnPos.y, tz - spawnPos.z);
        double leg = toTarget.length();
        if (leg < 1.0e-4) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        toTarget.scale(1.0 / leg);
        double pullback = Math.min(0.58, Math.max(0.22, leg * 0.09));
        spawnPos.addScaled(toTarget, -pullback);
        double pathLen =
            Math.sqrt(
                (tx - spawnPos.x) * (tx - spawnPos.x)
                    + (ty - spawnPos.y) * (ty - spawnPos.y)
                    + (tz - spawnPos.z) * (tz - spawnPos.z)
            );
        if (pathLen < 1.0e-4) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Vector3d dir = new Vector3d(tx - spawnPos.x, ty - spawnPos.y, tz - spawnPos.z);
        dir.scale(1.0 / pathLen);
        double pitch = Math.asin(Math.max(-1.0, Math.min(1.0, dir.y)));
        double yaw = Math.atan2(-dir.x, -dir.z);
        Vector3f spawnRot = new Vector3f((float) pitch, (float) yaw, 0.0F);
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(spawnPos.clone(), spawnRot));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(spawnRot));
        holder.addComponent(Interactions.getComponentType(), new Interactions());
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        holder.addComponent(
            BoundingBox.getComponentType(),
            new BoundingBox(Box.horizontallyCentered(TRACER_LOD_BOX_SIZE, TRACER_LOD_BOX_SIZE, TRACER_LOD_BOX_SIZE))
        );
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(commandBuffer.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(Projectile.getComponentType());
        Velocity launchVelocity = new Velocity();
        launchVelocity.set(
            dir.x * TRACER_SPEED_BLOCKS_PER_SEC,
            dir.y * TRACER_SPEED_BLOCKS_PER_SEC,
            dir.z * TRACER_SPEED_BLOCKS_PER_SEC
        );
        holder.addComponent(Velocity.getComponentType(), launchVelocity);
        holder.addComponent(
            BuildingStaffFrontierTracerComponent.getComponentType(),
            new BuildingStaffFrontierTracerComponent(tx, ty, tz, pathLen, TRACER_SPEED_BLOCKS_PER_SEC, playerUuid)
        );
        holder.addComponent(
            DespawnComponent.getComponentType(),
            new DespawnComponent(commandBuffer.getResource(TimeResource.getResourceType()).getNow().plus(Duration.ofSeconds(300L)))
        );
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        commandBuffer.addEntity(holder, AddReason.SPAWN);
        ParticleUtil.spawnParticleEffect(
            AetherhavenConstants.BUILDING_STAFF_GUIDE_TRAIL_PARTICLE_SYSTEM_ID,
            spawnPos.clone(),
            store
        );
        ParticleUtil.spawnParticleEffect(
            AetherhavenConstants.BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID,
            spawnPos.clone(),
            store
        );
        LAST_PRIMARY_NS.put(playerUuid, nowNs);
    }

    private static void failWithHint(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("server.aetherhaven.assembly.tracer.none_in_range"));
        }
    }

    @Nonnull
    private static Vector3d staffTip(@Nonnull TransformComponent transform, @Nullable HeadRotation head) {
        Vector3f euler = transform.getRotation();
        Vector3d forward =
            head != null ? head.getDirection() : directionFromRotation(euler);
        return transform.getPosition().clone().add(0.0, 1.32, 0.0).add(forward.clone().scale(0.42));
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
