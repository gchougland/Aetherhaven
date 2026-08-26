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
import com.hypixel.hytale.server.core.modules.entity.component.RespondToHit;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Spawns carnival whack goblins at festival-local hole cells. */
public final class CarnivalWhackSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Half-scale goblin hitbox for melee targeting (feet at origin). */
    private static final Box GOBLIN_BOX = new Box(0.35, 0.0, 0.35, 0.65, 0.7, 0.65);

    private CarnivalWhackSpawnService() {}

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
        if (!CarnivalWhackComponent.isRegistered()) {
            return null;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        var entityStore = world.getEntityStore();
        if (plugin == null || entityStore == null) {
            return null;
        }
        CarnivalWhackSession session = CarnivalWhackSessionIndex.get(townId);
        if (session == null || session.getPhase() != CarnivalWhackSession.Phase.PLAYING) {
            if (session != null) {
                session.cancelReservedSpawn();
            }
            return null;
        }
        List<FestivalDefinition.WhackSpawnRow> holes = festival.getWhackSpawns();
        if (holes.isEmpty()) {
            LOGGER.atWarning().log("Carnival whack spawn skipped: no whackSpawns for town %s", townId);
            session.cancelReservedSpawn();
            return null;
        }
        int holeIndex = pickHoleIndex(holes.size(), session);
        if (holeIndex < 0) {
            session.cancelReservedSpawn();
            return null;
        }
        session.setLastSpawnHoleIndex(holeIndex);
        session.occupyHole(holeIndex);
        FestivalDefinition.WhackSpawnRow hole = holes.get(holeIndex);
        Vector3d stand =
            FestivalPrefabSwapService.spotWorldPosition(
                plugin,
                festivalPlot,
                hole.getLocalX(),
                hole.getLocalY(),
                hole.getLocalZ()
            );
        double peakY = stand.y;
        double buryY = peakY - CarnivalIds.WHACK_POP_HEIGHT;
        Vector3d pos = new Vector3d(stand.x, buryY, stand.z);
        String modelId = CarnivalIds.WHACK_GOBLIN_MODEL;
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
        if (asset == null) {
            LOGGER.atWarning().log("Carnival whack goblin model missing: %s", modelId);
            session.freeHole(holeIndex);
            session.cancelReservedSpawn();
            return null;
        }
        Model model = Model.createScaledModel(asset, CarnivalIds.WHACK_MODEL_SCALE);
        Store<EntityStore> store = entityStore.getStore();
        float yaw = faceYawTowardSessionPlayer(store, session, pos);
        Rotation3f rot = new Rotation3f(0f, yaw, 0f);
        UUID entityUuid = UUID.randomUUID();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rot));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(new Rotation3f(0f, yaw, 0f)));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(
            PersistentModel.getComponentType(),
            new PersistentModel(new Model.ModelReference(modelId, model.getScale(), model.getRandomAttachmentIds(), false))
        );
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(GOBLIN_BOX));
        // U6 melee selectors skip entities without Health or RespondToHit.
        holder.ensureComponent(RespondToHit.getComponentType());
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(Velocity.getComponentType(), new Velocity());
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUuid));
        CarnivalWhackComponent whack = new CarnivalWhackComponent();
        whack.setTownId(townId);
        whack.setHoleIndex(holeIndex);
        whack.setStandY(peakY);
        whack.setState(CarnivalWhackComponent.State.RISING);
        holder.addComponent(CarnivalWhackComponent.getComponentType(), whack);

        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            session.freeHole(holeIndex);
            session.cancelReservedSpawn();
            return null;
        }
        session.addActiveGoblin(entityUuid);
        CarnivalAudio.playGoblinAlert(store, stand);
        return ref;
    }

    private static float faceYawTowardSessionPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull CarnivalWhackSession session,
        @Nonnull Vector3d from
    ) {
        UUID playerUuid = session.getPlayerUuid();
        if (playerUuid == null) {
            return 0f;
        }
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return 0f;
        }
        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            return 0f;
        }
        Vector3d playerPos = playerTransform.getPosition();
        return com.hexvane.aetherhaven.festival.pigrace.PigRaceLanes.facingYawRadians(
            playerPos.x - from.x,
            playerPos.z - from.z
        );
    }

    private static int pickHoleIndex(int holeCount, @Nonnull CarnivalWhackSession session) {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < holeCount; i++) {
            if (!session.isHoleOccupied(i)) {
                free.add(i);
            }
        }
        if (free.isEmpty()) {
            return -1;
        }
        int last = session.getLastSpawnHoleIndex();
        if (free.size() > 1 && last >= 0) {
            free.removeIf(i -> i == last);
            if (free.isEmpty()) {
                for (int i = 0; i < holeCount; i++) {
                    if (!session.isHoleOccupied(i)) {
                        free.add(i);
                    }
                }
            }
        }
        return free.get(ThreadLocalRandom.current().nextInt(free.size()));
    }

    public static void despawnAllForTown(@Nonnull World world, @Nonnull UUID townId) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !CarnivalWhackComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        store.forEachChunk(
            Query.and(CarnivalWhackComponent.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    CarnivalWhackComponent whack =
                        chunk.getComponent(i, CarnivalWhackComponent.getComponentType());
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    if (whack == null || ref == null || !ref.isValid()) {
                        continue;
                    }
                    if (townId.equals(whack.getTownId())) {
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
        if (!CarnivalWhackComponent.isRegistered()) {
            return;
        }
        store.forEachChunk(
            Query.and(CarnivalWhackComponent.getComponentType()),
            (chunk, buf) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    CarnivalWhackComponent whack =
                        chunk.getComponent(i, CarnivalWhackComponent.getComponentType());
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    if (whack == null || ref == null || !ref.isValid()) {
                        continue;
                    }
                    if (townId.equals(whack.getTownId())) {
                        commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
                    }
                }
            }
        );
    }
}
