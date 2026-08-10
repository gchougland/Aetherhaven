package com.hexvane.aetherhaven.villagercosmetic;

import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Spawns a wardrobe preview model, aims a custom camera at it, and hides the interacting player. */
public final class VillagerCosmeticPreviewSession {
    private static final double PREVIEW_FORWARD = 2.2;
    private static final double CAMERA_FORWARD = 4.6;
    /** World Y above the wardrobe block for the camera (player root / feet based). */
    private static final double CAMERA_HEIGHT = 1.45;
    /** World Y above the wardrobe block to aim at on the preview villager. */
    private static final double LOOK_AT_HEIGHT = 1.2;
    /** Tiny scale so the local player body is not visible in the third-person wardrobe camera. */
    private static final float HIDDEN_PLAYER_SCALE = 0.001f;

    @Nullable
    private Ref<EntityStore> previewRef;
    private boolean cameraActive;
    private boolean playerHiddenFromOthers;
    private boolean playerScaleHidden;
    private boolean hadEntityScale;
    private float previousEntityScale = 1f;
    @Nullable
    private UUID playerUuid;

    public void begin(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        int wardrobeX,
        int wardrobeY,
        int wardrobeZ,
        @Nonnull Model model
    ) {
        cleanup(playerEntityRef, store, playerRef);
        World world = store.getExternalData().getWorld();
        Vector3i block = new Vector3i(wardrobeX, wardrobeY, wardrobeZ);
        int[] forward = horizontalForward(world, block);
        double previewX = wardrobeX + 0.5 + forward[0] * PREVIEW_FORWARD;
        double previewZ = wardrobeZ + 0.5 + forward[2] * PREVIEW_FORWARD;
        double previewY = wardrobeY;
        Vector3d previewPos = new Vector3d(previewX, previewY, previewZ);
        // Face the camera (further along the wardrobe forward axis).
        Rotation3f bodyRot = Rotation3f.lookAt(new Vector3d(forward[0], 0, forward[2]));

        var holder = store.getRegistry().newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(previewPos, bodyRot));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(bodyRot));
        previewRef = store.addEntity(holder, AddReason.SPAWN);

        hidePlayerModel(playerEntityRef, store, playerRef, world);

        double camX = wardrobeX + 0.5 + forward[0] * CAMERA_FORWARD;
        double camZ = wardrobeZ + 0.5 + forward[2] * CAMERA_FORWARD;
        double camY = wardrobeY + CAMERA_HEIGHT;
        TransformComponent playerTransform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        Vector3d playerPos = playerTransform != null ? playerTransform.getPosition() : new Vector3d(camX, camY, camZ);
        applyCamera(
            playerRef,
            playerPos.x,
            playerPos.y,
            playerPos.z,
            camX,
            camY,
            camZ,
            previewX,
            previewY + LOOK_AT_HEIGHT,
            previewZ
        );
        cameraActive = true;
    }

    public void updateModel(@Nonnull Store<EntityStore> store, @Nonnull Model model) {
        if (previewRef == null || !previewRef.isValid()) {
            return;
        }
        Ref<EntityStore> ref = previewRef;
        Runnable apply = () -> {
            if (ref.isValid()) {
                store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
            }
        };
        runStoreWrite(store, apply);
    }

    public void cleanup(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        final Ref<EntityStore> toRemove = previewRef;
        previewRef = null;
        if (toRemove != null && toRemove.isValid()) {
            runStoreWrite(
                store,
                () -> {
                    if (toRemove.isValid()) {
                        store.removeEntity(toRemove, RemoveReason.REMOVE);
                    }
                }
            );
        }
        if (cameraActive) {
            CameraManager cameraManager = store.getComponent(playerEntityRef, CameraManager.getComponentType());
            if (cameraManager != null) {
                cameraManager.resetCamera(playerRef);
            } else {
                playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
            }
            cameraActive = false;
        }
        restorePlayerModel(playerEntityRef, store, playerRef, store.getExternalData().getWorld());
    }

    private void hidePlayerModel(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nullable World world
    ) {
        // HiddenPlayersManager cannot hide a player from themselves; shrink the local model instead.
        EntityScaleComponent existing = store.getComponent(playerEntityRef, EntityScaleComponent.getComponentType());
        if (existing != null) {
            hadEntityScale = true;
            previousEntityScale = existing.getScale() > 0f ? existing.getScale() : 1f;
        } else {
            hadEntityScale = false;
            previousEntityScale = 1f;
        }
        runStoreWrite(
            store,
            () -> store.putComponent(playerEntityRef, EntityScaleComponent.getComponentType(), new EntityScaleComponent(HIDDEN_PLAYER_SCALE))
        );
        playerScaleHidden = true;

        UUIDComponent uc = store.getComponent(playerEntityRef, UUIDComponent.getComponentType());
        if (uc == null || world == null) {
            return;
        }
        playerUuid = uc.getUuid();
        for (PlayerRef viewer : world.getPlayerRefs()) {
            if (viewer == null || playerUuid.equals(viewer.getUuid())) {
                continue;
            }
            viewer.getHiddenPlayersManager().hidePlayer(playerUuid);
        }
        playerHiddenFromOthers = true;
    }

    private void restorePlayerModel(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nullable World world
    ) {
        if (playerScaleHidden) {
            // Scale changes send ModelUpdate; without a follow-up skin refresh the client shows a naked avatar.
            final float restore = previousEntityScale > 0f ? previousEntityScale : 1f;
            playerScaleHidden = false;
            hadEntityScale = false;
            previousEntityScale = 1f;
            runStoreWrite(
                store,
                () -> {
                    Ref<EntityStore> ref = playerEntityRef.isValid() ? playerEntityRef : playerRef.getReference();
                    if (ref == null || !ref.isValid()) {
                        return;
                    }
                    store.putComponent(
                        ref,
                        EntityScaleComponent.getComponentType(),
                        new EntityScaleComponent(restore)
                    );
                    refreshPlayerSkinNetwork(ref, store);
                }
            );
        }
        if (playerHiddenFromOthers && playerUuid != null) {
            UUID uuid = playerUuid;
            playerHiddenFromOthers = false;
            playerUuid = null;
            if (world != null) {
                for (PlayerRef viewer : world.getPlayerRefs()) {
                    if (viewer == null) {
                        continue;
                    }
                    viewer.getHiddenPlayersManager().showPlayer(uuid);
                }
            }
            playerRef.getHiddenPlayersManager().showPlayer(uuid);
        }
    }

    /** Re-send skin after a ModelUpdate so the client does not keep a naked default avatar. */
    @SuppressWarnings("deprecation") // sendPlayerSelf is the supported way to refresh the local player entity packet
    private static void refreshPlayerSkinNetwork(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerSkinComponent skin = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (skin != null) {
            skin.setNetworkOutdated();
        }
        PlayerSystems.PlayerSpawnedSystem.sendPlayerSelf(ref, store);
    }

    /** Store mutations are illegal while a tick system holds write processing (e.g. death UI dismiss). */
    @SuppressWarnings("deprecation") // Store.isProcessing() is the only way to detect mid-tick writes
    private static void runStoreWrite(@Nonnull Store<EntityStore> store, @Nonnull Runnable write) {
        if (store.isProcessing()) {
            store.getExternalData().getWorld().execute(write);
        } else {
            write.run();
        }
    }

    private static void applyCamera(
        @Nonnull PlayerRef playerRef,
        double playerX,
        double playerY,
        double playerZ,
        double camX,
        double camY,
        double camZ,
        double lookX,
        double lookY,
        double lookZ
    ) {
        double dx = lookX - camX;
        double dy = lookY - camY;
        double dz = lookZ - camZ;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.atan2(-dx, -dz);
        float pitch = (float) -Math.atan2(dy, Math.max(0.001, horiz));

        // Attach to the player with an offset so the body stays on solid ground with physics.
        // Offsets are from the entity root (feet), not eye height — eyeOffset would aim too high.
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.positionLerpSpeed = 0.35f;
        settings.rotationLerpSpeed = 0.35f;
        settings.displayCursor = true;
        settings.isFirstPerson = false;
        settings.eyeOffset = false;
        settings.positionType = PositionType.AttachedToPlusOffset;
        settings.positionOffset = new Position(camX - playerX, camY - playerY, camZ - playerZ);
        settings.rotationType = RotationType.Custom;
        settings.rotation = new Direction(yaw, pitch, 0f);
        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, settings));
    }

    @Nonnull
    private static int[] horizontalForward(@Nonnull World world, @Nonnull Vector3i blockWorldPos) {
        Rotation yaw = PlotBlockRotationUtil.readBlockYaw(world, blockWorldPos);
        return switch (yaw) {
            case None -> new int[] {0, 0, -1};
            case Ninety -> new int[] {1, 0, 0};
            case OneEighty -> new int[] {0, 0, 1};
            case TwoSeventy -> new int[] {-1, 0, 0};
            default -> new int[] {0, 0, -1};
        };
    }
}
