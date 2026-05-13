package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FloatingGiftSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String MODEL_ID = "Floating_Gift";
    public static final String FLOAT_ANIMATION = "Float";
    /**
     * Idle bob uses {@link AnimationSlot#Action} with the Float set — same slot chain as Pop / PopHold. Emote is
     * resolved for player-like rigs on the client; prop blockymodels log missing Emote for {@code Float}.
     */
    public static final AnimationSlot FLOAT_ANIMATION_SLOT = AnimationSlot.Action;
    public static final String POP_ANIMATION = "Pop";
    /** Frozen popped pose — loops so clients do not strip Action after one-shot Pop finishes. */
    public static final String POP_HOLD_ANIMATION = "PopHold";

    private FloatingGiftSpawnService() {}

    public static boolean isModelAssetRegistered() {
        return ModelAsset.getAssetMap().getAsset(MODEL_ID) != null;
    }

    /**
     * Natural spawn from {@link FloatingGiftSchedulerSystem}: must not call {@link World#getNonTickingChunk(long)}
     * during an entity ticking system. {@link World#execute} runs after {@code EntityStore.tick} for this frame, so
     * heightmap sampling and {@link Store#addEntity} are safe there.
     */
    public static void scheduleNaturalSpawnAfterEntityTick(
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull Vector3d origin,
        @Nonnull Vector3d target
    ) {
        if (!FloatingGiftSpawnSchedule.tryBeginDeferredSpawn(playerUuid)) {
            return;
        }
        Vector3d o = origin.clone();
        Vector3d t = target.clone();
        world.execute(() -> {
            try {
                if (!world.isAlive()) {
                    return;
                }
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                if (plugin == null) {
                    return;
                }
                AetherhavenPluginConfig cfg = plugin.getConfig().get();
                if (!cfg.isFloatingGiftEnabled()) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
                if (wtr == null) {
                    return;
                }
                Instant gameNow = wtr.getGameTime();
                if (FloatingGiftSystem.countActiveGifts(store) >= cfg.getFloatingGiftMaxActivePerWorld()) {
                    return;
                }
                if (!isModelAssetRegistered()) {
                    FloatingGiftSpawnSchedule.onSpawnFailed(playerUuid, gameNow);
                    return;
                }
                Ref<EntityStore> ref = spawnAroundTarget(store, o, t);
                if (ref != null && ref.isValid()) {
                    FloatingGiftSpawnSchedule.onSpawnSucceeded(playerUuid, gameNow, cfg);
                } else {
                    FloatingGiftSpawnSchedule.onSpawnFailed(playerUuid, gameNow);
                }
            } finally {
                FloatingGiftSpawnSchedule.clearDeferredSpawnInFlight(playerUuid);
            }
        });
    }

    /**
     * Immediate spawn — safe from commands, world task queue, and other non-entity-tick contexts. Initial float clip is
     * applied synchronously.
     */
    @Nullable
    public static Ref<EntityStore> spawnAroundTarget(@Nonnull Store<EntityStore> store, @Nonnull Vector3d origin, @Nonnull Vector3d target) {
        Holder<EntityStore> holder = createSpawnHolder(store, origin, target);
        if (holder == null) {
            return null;
        }
        FloatingGiftComponent gift = holder.getComponent(FloatingGiftComponent.getComponentType());
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            LOGGER.atWarning().log("Floating gift spawn failed: addEntity returned invalid ref.");
            return null;
        }
        if (gift != null) {
            gift.markSpawnFloatPlayedImmediately();
        }
        FloatingGiftAnimationHelper.playAnimation(store, ref, FLOAT_ANIMATION_SLOT, FLOAT_ANIMATION);
        return ref;
    }

    @Nullable
    private static Holder<EntityStore> createSpawnHolder(@Nonnull Store<EntityStore> store, @Nonnull Vector3d origin, @Nonnull Vector3d target) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double radius = cfg.getFloatingGiftSpawnRadiusBlocks();
        double theta = rnd.nextDouble(Math.PI * 2.0);
        double sx = origin.x + Math.cos(theta) * radius;
        double sz = origin.z + Math.sin(theta) * radius;
        double heightOffset = cfg.getFloatingGiftSpawnHeightOffsetBlocks();
        int bx = MathUtil.floor(sx);
        int bz = MathUtil.floor(sz);
        double sy;
        World world = store.getExternalData().getWorld();
        WorldChunk surfaceChunk = world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
        if (surfaceChunk != null) {
            // Same surface convention as FitToHeightMapSpawnProvider: air block above heightmap (+ optional vertical offset).
            sy = surfaceChunk.getHeight(bx, bz) + 1.0 + heightOffset;
        } else {
            sy = origin.y + heightOffset;
        }

        // Horizontal travel only — a full 3D vector toward the player includes negative Y and makes the balloon sink.
        double hdx = target.x - sx;
        double hdz = target.z - sz;
        double lenH = Math.hypot(hdx, hdz);
        double dirHx;
        double dirHz;
        if (lenH < 1.0e-6) {
            dirHx = 1.0;
            dirHz = 0.0;
        } else {
            dirHx = hdx / lenH;
            dirHz = hdz / lenH;
        }
        Vector3d dirFlat = new Vector3d(dirHx, 0.0, dirHz);
        Vector3f rot = toYawPitch(dirFlat);

        ModelAsset asset = ModelAsset.getAssetMap().getAsset(MODEL_ID);
        if (asset == null) {
            LOGGER
                .atWarning()
                .log(
                    "Floating gift spawn skipped: model asset '%s' not loaded — add Server/Models/%s.json and register mesh under Items/... (MISC-only paths fail validation).",
                    MODEL_ID,
                    MODEL_ID
                );
            return null;
        }
        Model model = Model.createUnitScaleModel(asset);
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(sx, sy, sz), rot));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rot));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(
            PersistentModel.getComponentType(),
            new PersistentModel(new Model.ModelReference(MODEL_ID, model.getScale(), model.getRandomAttachmentIds(), false))
        );
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(Box.horizontallyCentered(1.0, 1.5, 1.0)));
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(ActiveAnimationComponent.getComponentType(), new ActiveAnimationComponent());
        holder.addComponent(Velocity.getComponentType(), new Velocity());
        FloatingGiftComponent gift = new FloatingGiftComponent();
        gift.setState(FloatingGiftState.FLOATING);
        gift.setDirX(dirHx);
        gift.setDirY(0.0);
        gift.setDirZ(dirHz);
        gift.setAnchorY(sy);
        gift.setSpeedBlocksPerSec(cfg.getFloatingGiftMoveSpeedBlocksPerSec());
        gift.setFallBlocksPerSec(cfg.getFloatingGiftFallSpeedBlocksPerSec());
        gift.setProjectileHitRadius(cfg.getFloatingGiftProjectileHitRadiusBlocks());
        holder.addComponent(FloatingGiftComponent.getComponentType(), gift);
        return holder;
    }

    @Nonnull
    public static Vector3f toYawPitch(@Nonnull Vector3d dir) {
        double ny = Math.max(-1.0, Math.min(1.0, dir.y));
        double pitch = Math.asin(ny);
        double yaw = Math.atan2(-dir.x, -dir.z);
        return new Vector3f((float) pitch, (float) yaw, 0.0f);
    }
}
