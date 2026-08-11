package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Spawns carnival balloon floaters from festival-local spawn cells. */
public final class CarnivalBalloonSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** From {@code Aetherhaven_Balloon} hitbox Min/Max. */
    private static final Box BALLOON_BOX = new Box(0.15625, 1.03125, 0.125, 0.84375, 1.90625, 0.875);

    private CarnivalBalloonSpawnService() {}

    public static void scheduleSpawn(
        @Nonnull World world,
        @Nonnull UUID townId,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        world.execute(() -> spawnOne(world, townId, festivalPlot, festival));
    }

    @Nullable
    public static Ref<EntityStore> spawnOne(
        @Nonnull World world,
        @Nonnull UUID townId,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        if (!CarnivalBalloonComponent.isRegistered()) {
            return null;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        var entityStore = world.getEntityStore();
        if (plugin == null || entityStore == null) {
            return null;
        }
        CarnivalBalloonSession session = CarnivalBalloonSessionIndex.get(townId);
        if (session == null || session.getPhase() != CarnivalBalloonSession.Phase.PLAYING) {
            return null;
        }
        if (session.getSpawned() >= CarnivalIds.BALLOON_TOTAL) {
            return null;
        }
        List<FestivalDefinition.BalloonSpawnRow> spots = festival.getBalloonSpawns();
        if (spots.isEmpty()) {
            LOGGER.atWarning().log("Carnival balloon spawn skipped: no balloonSpawns for town %s", townId);
            return null;
        }
        FestivalDefinition.BalloonSpawnRow spot = spots.get(ThreadLocalRandom.current().nextInt(spots.size()));
        Vector3d pos =
            FestivalPrefabSwapService.spotWorldPosition(
                plugin,
                festivalPlot,
                spot.getLocalX(),
                spot.getLocalY(),
                spot.getLocalZ()
            );
        // Float a little above the marked cell so ground-level markers still clear the floor.
        pos.y += 0.75;
        String modelId =
            CarnivalIds.BALLOON_MODELS[ThreadLocalRandom.current().nextInt(CarnivalIds.BALLOON_MODELS.length)];
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
        if (asset == null) {
            LOGGER.atWarning().log("Carnival balloon model missing: %s", modelId);
            return null;
        }
        Model model = Model.createUnitScaleModel(asset);
        UUID entityUuid = UUID.randomUUID();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, new Rotation3f()));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(new Rotation3f()));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(
            PersistentModel.getComponentType(),
            new PersistentModel(new Model.ModelReference(modelId, model.getScale(), model.getRandomAttachmentIds(), false))
        );
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(BALLOON_BOX));
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(entityStore.getStore().getExternalData().takeNextNetworkId()));
        holder.addComponent(Velocity.getComponentType(), new Velocity());
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUuid));
        CarnivalBalloonComponent balloon = new CarnivalBalloonComponent();
        balloon.setTownId(townId);
        balloon.setMaxLifeSeconds(CarnivalIds.BALLOON_FLOAT_SECONDS);
        holder.addComponent(CarnivalBalloonComponent.getComponentType(), balloon);

        Store<EntityStore> store = entityStore.getStore();
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        session.addActiveBalloon(entityUuid);
        return ref;
    }

    public static void despawnAllForTown(@Nonnull World world, @Nonnull UUID townId) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !CarnivalBalloonComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        store.forEachChunk(
            Query.and(CarnivalBalloonComponent.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    CarnivalBalloonComponent balloon =
                        chunk.getComponent(i, CarnivalBalloonComponent.getComponentType());
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    if (balloon == null || ref == null || !ref.isValid()) {
                        continue;
                    }
                    if (townId.equals(balloon.getTownId())) {
                        commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
                    }
                }
            }
        );
    }

    public static void despawnAllForTown(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UUID townId
    ) {
        if (!CarnivalBalloonComponent.isRegistered()) {
            return;
        }
        store.forEachChunk(
            Query.and(CarnivalBalloonComponent.getComponentType()),
            (chunk, buf) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    CarnivalBalloonComponent balloon =
                        chunk.getComponent(i, CarnivalBalloonComponent.getComponentType());
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    if (balloon == null || ref == null || !ref.isValid()) {
                        continue;
                    }
                    if (townId.equals(balloon.getTownId())) {
                        commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
                    }
                }
            }
        );
    }
}
