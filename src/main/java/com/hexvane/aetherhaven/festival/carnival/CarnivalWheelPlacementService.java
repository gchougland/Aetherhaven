package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Places / removes the wall-mounted carnival wheel block and spinning face entity. */
public final class CarnivalWheelPlacementService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int PLACE_SETTINGS = 10;
    /** Matches the scaled face prop; needed so clients render it. */
    private static final Box FACE_BOX = new Box(-4.0, -4.0, -0.2, 4.0, 4.0, 0.2);
    /**
     * Block models sit on the cell floor while the face center sits about one block up. A couple of model pixels
     * (1/32 block each) nudge the spinning face up to match the frame.
     */
    private static final double FACE_HEIGHT = 1.0 + (3.0 / 32.0);
    /**
     * Out from the wall toward the player uses the opposite of Hytale look forward; negative pulls the face toward
     * the backboard.
     */
    private static final double FACE_FORWARD = -0.38 - (1.0 / 32.0);

    private CarnivalWheelPlacementService() {}
    private static boolean placeWheelBlock(@Nonnull World world, int bx, int by, int bz, @Nonnull RotationTuple rt) {
        BlockType wheelType = BlockType.getAssetMap().getAsset(CarnivalIds.WHEEL_BLOCK_ID);
        if (wheelType == null) {
            return false;
        }
        BlockSection section = ChunkSectionBlockUtil.blockSectionAt(world, bx, by, bz);
        if (section == null) {
            return false;
        }
        int rot = rt.index();
        var chunkStore = world.getChunkStore().getStore();
        if (!BlockOperations.testPlaceBlock(chunkStore, section, bx, by, bz, wheelType, rot)) {
            return false;
        }
        int index = BlockType.getAssetMap().getIndex(CarnivalIds.WHEEL_BLOCK_ID);
        return ChunkSectionBlockUtil.setBlock(world, bx, by, bz, index, wheelType, rot, FillerBlockUtil.NO_FILLER, PLACE_SETTINGS);
    }

    public static void place(
        @Nonnull World world,
        @Nonnull UUID townId,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        var entityStore = world.getEntityStore();
        if (plugin == null || entityStore == null || !CarnivalWheelFaceComponent.isRegistered()) {
            return;
        }
        FestivalDefinition.WheelLocalRow wheel = festival.getWheelLocal();
        if (wheel == null) {
            LOGGER.atWarning().log("Carnival wheel missing wheelLocal for town %s", townId);
            return;
        }
        remove(world, townId);

        Vector3d center =
            FestivalPrefabSwapService.spotWorldPosition(
                plugin,
                festivalPlot,
                wheel.getLocalX(),
                wheel.getLocalY(),
                wheel.getLocalZ()
            );
        int bx = (int) Math.floor(center.x);
        int by = (int) Math.floor(center.y);
        int bz = (int) Math.floor(center.z);
        // Prefab spots rotate with the plot; facing must use the same world yaw as the pasted wall.
        Rotation yaw = worldWheelRotation(festivalPlot, wheel.getYawDegrees());
        RotationTuple rt = RotationTuple.of(yaw, Rotation.None, Rotation.None);
        if (ChunkSectionBlockUtil.resolveTickingChunk(world, bx, bz) == null) {
            LOGGER.atWarning().log("Carnival wheel chunk not loaded at %s %s %s", bx, by, bz);
            return;
        }
        if (!placeWheelBlock(world, bx, by, bz, rt)) {
            LOGGER.atWarning().log("Carnival wheel placeBlock failed at %s %s %s", bx, by, bz);
        }

        float facingYaw = rotationToYawRadians(yaw);
        boolean specialFace = isSpecialWheelFace(world, plugin, townId);
        UUID faceUuid = spawnFace(entityStore.getStore(), townId, center, facingYaw, specialFace);
        CarnivalWheelSession session = CarnivalWheelSessionIndex.getOrCreate(townId);
        session.clearGameplay();
        session.setFaceEntityUuid(faceUuid);
    }

    /** True when the town has not unlocked the clown yet (special blue wedge texture). */
    public static boolean isSpecialWheelFace(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        return town == null || !town.hasQuestCompleted(AetherhavenConstants.QUEST_CLOWN_RESCUE);
    }

    /**
     * World position suitable for spawning the clown rescue beside the wheel face (or block center fallback).
     */
    @Nullable
    public static Vector3d resolveClownStandNearWheel(@Nonnull World world, @Nonnull UUID townId) {
        CarnivalWheelSession session = CarnivalWheelSessionIndex.get(townId);
        UUID faceUuid = session != null ? session.getFaceEntityUuid() : null;
        var entityStore = world.getEntityStore();
        if (entityStore != null && faceUuid != null) {
            Ref<EntityStore> faceRef = entityStore.getStore().getExternalData().getRefFromUUID(faceUuid);
            if (faceRef != null && faceRef.isValid()) {
                TransformComponent tc =
                    entityStore.getStore().getComponent(faceRef, TransformComponent.getComponentType());
                CarnivalWheelFaceComponent face =
                    entityStore.getStore().getComponent(faceRef, CarnivalWheelFaceComponent.getComponentType());
                if (tc != null) {
                    Vector3d pos = tc.getPosition();
                    float yaw = face != null ? face.getBaseYaw() : 0f;
                    // Left of the wheel as you face it (perpendicular to facing), not behind the wall.
                    double dist = 2.0;
                    double ox = -Math.cos(yaw) * dist;
                    double oz = Math.sin(yaw) * dist;
                    int bx = (int) Math.floor(pos.x + ox);
                    int bz = (int) Math.floor(pos.z + oz);
                    int searchTop = (int) Math.floor(pos.y) + 2;
                    int feetY = com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.findStandY(world, bx, bz, searchTop);
                    if (feetY == Integer.MIN_VALUE) {
                        feetY = Math.max(0, (int) Math.floor(pos.y) - 1);
                    }
                    return new Vector3d(bx + 0.5, feetY, bz + 0.5);
                }
            }
        }
        return null;
    }

    /**
     * Rebinds the in-memory session to a live wheel-face entity after restart (session UUIDs are not saved). Does not
     * spawn, delete, or mutate Store components Ã¢â‚¬â€ PropComponent / NetworkId repair happens in
     * {@link CarnivalWheelSystem} via CommandBuffer.
     */
    public static void bindSessionToLiveFace(@Nonnull World world, @Nonnull UUID townId) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !CarnivalWheelFaceComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        CarnivalWheelSession session = CarnivalWheelSessionIndex.getOrCreate(townId);
        UUID tracked = session.getFaceEntityUuid();
        if (tracked != null) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(tracked);
            if (ref != null && ref.isValid()) {
                return;
            }
        }
        UUID orphan = findFaceUuidForTown(world, townId);
        if (orphan != null) {
            session.setFaceEntityUuid(orphan);
        }
    }

    /**
     * After the clown is unlocked, swap any live special face for the normal red/white face without respawning the
     * wall block. Safe to call from {@code world.execute}.
     */
    public static void refreshFaceAfterClownUnlock(@Nonnull World world, @Nonnull UUID townId) {
        refreshFaceModel(world, townId, false);
    }

    /** Swap the live wheel face to special (blue wedge) or normal texture. */
    public static void refreshFaceModel(@Nonnull World world, @Nonnull UUID townId, boolean specialFace) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !CarnivalWheelFaceComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        bindSessionToLiveFace(world, townId);
        CarnivalWheelSession session = CarnivalWheelSessionIndex.getOrCreate(townId);
        UUID faceUuid = session.getFaceEntityUuid();
        if (faceUuid == null) {
            faceUuid = findFaceUuidForTown(world, townId);
            if (faceUuid != null) {
                session.setFaceEntityUuid(faceUuid);
            }
        }
        if (faceUuid == null) {
            return;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(faceUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        String modelId = specialFace ? CarnivalIds.WHEEL_FACE_MODEL_SPECIAL : CarnivalIds.WHEEL_FACE_MODEL;
        PersistentModel persistent = store.getComponent(ref, PersistentModel.getComponentType());
        if (persistent != null) {
            Model.ModelReference pref = persistent.getModelReference();
            if (pref != null && modelId.equals(pref.getModelAssetId())) {
                return;
            }
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
        if (asset == null) {
            LOGGER.atWarning().log("Carnival wheel face model missing: %s", modelId);
            return;
        }
        Model model = Model.createScaledModel(asset, CarnivalIds.WHEEL_FACE_SCALE);
        store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
        store.putComponent(
            ref,
            PersistentModel.getComponentType(),
            new PersistentModel(
                new Model.ModelReference(
                    modelId,
                    model.getScale(),
                    model.getRandomAttachmentIds(),
                    false
                )
            )
        );
        snapFaceRoll(store, session, CarnivalIds.WHEEL_IDLE_OFFSET_RAD);
    }

    /** Instantly sets the face prop roll (e.g. snap to idle before a new spin). */
    public static void snapFaceRoll(
        @Nonnull Store<EntityStore> store,
        @Nonnull CarnivalWheelSession session,
        float roll
    ) {
        UUID faceUuid = session.getFaceEntityUuid();
        if (faceUuid == null) {
            return;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(faceUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        CarnivalWheelFaceComponent face = store.getComponent(ref, CarnivalWheelFaceComponent.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (face == null || transform == null) {
            return;
        }
        face.setRoll(roll);
        Rotation3f rot = new Rotation3f(0f, face.getBaseYaw(), roll);
        TransformComponent updated = new TransformComponent(transform.getPosition(), rot);
        updated.setSectionLocation(transform.getSectionRef());
        store.putComponent(ref, TransformComponent.getComponentType(), updated);
        if (head != null) {
            head.teleportRotation(rot);
            store.putComponent(ref, HeadRotation.getComponentType(), head);
        }
        store.putComponent(ref, CarnivalWheelFaceComponent.getComponentType(), face);
    }

    public static void remove(@Nonnull World world, @Nonnull UUID townId) {
        CarnivalWheelSession session = CarnivalWheelSessionIndex.get(townId);
        UUID faceUuid = session != null ? session.getFaceEntityUuid() : null;
        var entityStore = world.getEntityStore();
        if (entityStore != null && CarnivalWheelFaceComponent.isRegistered()) {
            Store<EntityStore> store = entityStore.getStore();
            if (faceUuid != null) {
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(faceUuid);
                if (ref != null && ref.isValid()) {
                    TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                    if (tc != null) {
                        clearWheelBlockNear(world, tc.getPosition());
                    }
                    store.removeEntity(ref, RemoveReason.REMOVE);
                }
            }
            store.forEachChunk(
                Query.and(CarnivalWheelFaceComponent.getComponentType()),
                (chunk, commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        CarnivalWheelFaceComponent face =
                            chunk.getComponent(i, CarnivalWheelFaceComponent.getComponentType());
                        Ref<EntityStore> ref = chunk.getReferenceTo(i);
                        if (face == null || ref == null || !ref.isValid()) {
                            continue;
                        }
                        if (townId.equals(face.getTownId())) {
                            TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                            if (tc != null) {
                                clearWheelBlockNear(world, tc.getPosition());
                            }
                            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
                        }
                    }
                }
            );
        }
        if (session != null) {
            session.clearAll();
        }
    }

    @Nonnull
    private static Vector3d facePosFromCenter(@Nonnull Vector3d center, float facingYaw) {
        return new Vector3d(
            center.x + Math.sin(facingYaw) * FACE_FORWARD,
            center.y + FACE_HEIGHT,
            center.z + Math.cos(facingYaw) * FACE_FORWARD
        );
    }

    @Nullable
    private static UUID findFaceUuidForTown(@Nonnull World world, @Nonnull UUID townId) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !CarnivalWheelFaceComponent.isRegistered()) {
            return null;
        }
        UUID[] found = new UUID[1];
        entityStore
            .getStore()
            .forEachChunk(
                Query.and(CarnivalWheelFaceComponent.getComponentType(), UUIDComponent.getComponentType()),
                (chunk, commandBuffer) -> {
                    if (found[0] != null) {
                        return;
                    }
                    for (int i = 0; i < chunk.size(); i++) {
                        CarnivalWheelFaceComponent face =
                            chunk.getComponent(i, CarnivalWheelFaceComponent.getComponentType());
                        UUIDComponent uuid = chunk.getComponent(i, UUIDComponent.getComponentType());
                        if (face == null || uuid == null || !townId.equals(face.getTownId())) {
                            continue;
                        }
                        found[0] = uuid.getUuid();
                        return;
                    }
                }
            );
        return found[0];
    }

    @Nullable
    private static UUID spawnFace(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull Vector3d center,
        float facingYaw,
        boolean specialFace
    ) {
        String modelId = specialFace ? CarnivalIds.WHEEL_FACE_MODEL_SPECIAL : CarnivalIds.WHEEL_FACE_MODEL;
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
        if (asset == null) {
            LOGGER.atWarning().log("Carnival wheel face model missing: %s", modelId);
            return null;
        }
        Model model = Model.createScaledModel(asset, CarnivalIds.WHEEL_FACE_SCALE);
        UUID entityUuid = UUID.randomUUID();
        // Face the same way as the wall block (model +Z). Idle roll parks the pointer on a color edge.
        Rotation3f rot = new Rotation3f(0f, facingYaw, CarnivalIds.WHEEL_IDLE_OFFSET_RAD);
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        Vector3d pos = facePosFromCenter(center, facingYaw);
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rot));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rot));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(
            PersistentModel.getComponentType(),
            new PersistentModel(
                new Model.ModelReference(
                    modelId,
                    model.getScale(),
                    model.getRandomAttachmentIds(),
                    false
                )
            )
        );
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(FACE_BOX));
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        // Required so NetworkId is reassigned after chunk load Ã¢â‚¬â€ without this the face saves but looks gone.
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
        holder.addComponent(Velocity.getComponentType(), new Velocity());
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUuid));
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        CarnivalWheelFaceComponent face = new CarnivalWheelFaceComponent();
        face.setTownId(townId);
        face.setBaseYaw(facingYaw);
        face.setRoll(CarnivalIds.WHEEL_IDLE_OFFSET_RAD);
        holder.addComponent(CarnivalWheelFaceComponent.getComponentType(), face);
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            LOGGER.atWarning().log("Carnival wheel face spawn failed for town %s", townId);
            return null;
        }
        return entityUuid;
    }

    private static void clearWheelBlockNear(@Nonnull World world, @Nonnull Vector3d pos) {
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);
        for (int dy = -1; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = bx + dx;
                    int y = by + dy;
                    int z = bz + dz;
                    BlockType type = ChunkSectionBlockUtil.blockType(world, x, y, z);
                    if (type != null && CarnivalIds.WHEEL_BLOCK_ID.equals(type.getId())) {
                        ChunkSectionBlockUtil.setBlockEmpty(world, x, y, z, PLACE_SETTINGS);
                    }
                }
            }
        }
    }

    @Nonnull
    static Rotation worldWheelRotation(@Nonnull PlotInstance festivalPlot, float localYawDegrees) {
        int worldQuarter =
            Math.floorMod(rotationToQuarter(festivalPlot.resolvePrefabYaw()) + yawDegreesToQuarter(localYawDegrees), 4);
        return quarterToRotation(worldQuarter);
    }

    @Nonnull
    static Rotation yawDegreesToRotation(float yawDegrees) {
        return quarterToRotation(yawDegreesToQuarter(yawDegrees));
    }

    static float rotationToYawRadians(@Nonnull Rotation rotation) {
        return rotationToQuarter(rotation) * (float) (Math.PI / 2.0);
    }

    static float yawDegreesToRadians(float yawDegrees) {
        return yawDegreesToQuarter(yawDegrees) * (float) (Math.PI / 2.0);
    }

    private static int yawDegreesToQuarter(float yawDegrees) {
        float normalized = yawDegrees % 360f;
        if (normalized < 0f) {
            normalized += 360f;
        }
        return Math.floorMod(Math.round(normalized / 90f), 4);
    }

    private static int rotationToQuarter(@Nonnull Rotation rotation) {
        return switch (rotation) {
            case Ninety -> 1;
            case OneEighty -> 2;
            case TwoSeventy -> 3;
            default -> 0;
        };
    }

    @Nonnull
    private static Rotation quarterToRotation(int quarter) {
        return switch (Math.floorMod(quarter, 4)) {
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> Rotation.None;
        };
    }

}
