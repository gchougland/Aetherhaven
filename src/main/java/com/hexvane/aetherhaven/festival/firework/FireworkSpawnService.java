package com.hexvane.aetherhaven.festival.firework;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Spawns a rising firework rocket entity at a ground target. */
public final class FireworkSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** From {@code Aetherhaven_Festival_Firework} hitbox Min/Max. */
    private static final Box ROCKET_BOX = new Box(0.3125, 0.0, 0.3125, 0.6875, 0.9375, 0.6875);

    private FireworkSpawnService() {}

    @Nullable
    public static Ref<EntityStore> spawnAtBlock(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3i targetBlock
    ) {
        if (!FireworkRocketComponent.isRegistered()) {
            return null;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(FireworkIds.MODEL_ID);
        if (asset == null) {
            LOGGER.atWarning().log("Firework model missing: %s", FireworkIds.MODEL_ID);
            return null;
        }
        Model model = Model.createUnitScaleModel(asset);
        World world = commandBuffer.getExternalData().getWorld();
        Vector3d pos = FireworkBlockUtil.resolveSpawnPosition(world, targetBlock);
        float fuse =
            FireworkIds.FUSE_MIN_SECONDS
                + ThreadLocalRandom.current().nextFloat()
                    * (FireworkIds.FUSE_MAX_SECONDS - FireworkIds.FUSE_MIN_SECONDS);

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, new Rotation3f()));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(new Rotation3f()));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(
            PersistentModel.getComponentType(),
            new PersistentModel(
                new Model.ModelReference(FireworkIds.MODEL_ID, model.getScale(), model.getRandomAttachmentIds(), false)
            )
        );
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(ROCKET_BOX));
        holder.addComponent(
            NetworkId.getComponentType(),
            new NetworkId(commandBuffer.getExternalData().takeNextNetworkId())
        );
        holder.addComponent(Velocity.getComponentType(), new Velocity());
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(UUID.randomUUID()));
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        FireworkRocketComponent rocket = new FireworkRocketComponent();
        rocket.setFuseSeconds(fuse);
        holder.addComponent(FireworkRocketComponent.getComponentType(), rocket);

        Ref<EntityStore> ref = commandBuffer.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref;
    }
}
