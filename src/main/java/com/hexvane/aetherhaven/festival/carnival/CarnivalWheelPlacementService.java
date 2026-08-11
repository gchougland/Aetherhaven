package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
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
    private static final double FACE_HEIGHT = 1.0 + (2.0 / 32.0);

    private CarnivalWheelPlacementService() {}

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
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(bx, bz));
        if (chunk == null) {
            LOGGER.atWarning().log("Carnival wheel chunk not loaded at %s %s %s", bx, by, bz);
            return;
        }
        if (!chunk.placeBlock(bx, by, bz, CarnivalIds.WHEEL_BLOCK_ID, rt, PLACE_SETTINGS, false)) {
            LOGGER.atWarning().log("Carnival wheel placeBlock failed at %s %s %s", bx, by, bz);
        }

        float facingYaw = rotationToYawRadians(yaw);
        UUID faceUuid = spawnFace(entityStore.getStore(), townId, center, facingYaw);
        CarnivalWheelSession session = CarnivalWheelSessionIndex.getOrCreate(townId);
        session.clearGameplay();
        session.setFaceEntityUuid(faceUuid);
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

    @Nullable
    private static UUID spawnFace(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull Vector3d center,
        float facingYaw
    ) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(CarnivalIds.WHEEL_FACE_MODEL);
        if (asset == null) {
            LOGGER.atWarning().log("Carnival wheel face model missing: %s", CarnivalIds.WHEEL_FACE_MODEL);
            return null;
        }
        Model model = Model.createScaledModel(asset, CarnivalIds.WHEEL_FACE_SCALE);
        UUID entityUuid = UUID.randomUUID();
        // Face the same way as the wall block (model +Z). Idle roll parks the pointer on a color edge.
        Rotation3f rot = new Rotation3f(0f, facingYaw, CarnivalIds.WHEEL_IDLE_OFFSET_RAD);
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        Vector3d pos = new Vector3d(center.x, center.y + FACE_HEIGHT, center.z);
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rot));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rot));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(
            PersistentModel.getComponentType(),
            new PersistentModel(
                new Model.ModelReference(
                    CarnivalIds.WHEEL_FACE_MODEL,
                    model.getScale(),
                    model.getRandomAttachmentIds(),
                    false
                )
            )
        );
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(FACE_BOX));
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
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
                    BlockType type = world.getBlockType(x, y, z);
                    if (type != null && CarnivalIds.WHEEL_BLOCK_ID.equals(type.getId())) {
                        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
                        if (chunk != null) {
                            chunk.setBlock(x, y, z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, PLACE_SETTINGS);
                        }
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
