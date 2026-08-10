package com.hexvane.aetherhaven.festival.pigrace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.TrigMathUtil;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Race cameras for bettors: a sideline angle for most of the race, then a top-down finish cam near the line.
 */
public final class PigRaceCamera {
    /** Prefab-local sideline camera: merchant side, ~12 blocks above the track. */
    private static final int SIDE_CAMERA_LOCAL_X = -5;
    private static final int SIDE_CAMERA_LOCAL_Y = PigRaceLanes.STAND_Y + 12;
    private static final int SIDE_CAMERA_LOCAL_Z = -13;

    private static final int SIDE_FOCUS_LOCAL_X = 0;
    private static final int SIDE_FOCUS_LOCAL_Y = PigRaceLanes.STAND_Y + 1;
    private static final int SIDE_FOCUS_LOCAL_Z = 0;

    /** Prefab-local finish camera: same height, centered over the finish line, looking straight down. */
    private static final int FINISH_CAMERA_LOCAL_X = 0;
    private static final int FINISH_CAMERA_LOCAL_Y = PigRaceLanes.STAND_Y + 12;
    private static final int FINISH_CAMERA_LOCAL_Z = 8;

    private PigRaceCamera() {}

    /** Puts every current bettor on the sideline race camera. Safe to call once when the race begins. */
    public static void activateForBettors(
        @Nonnull Store<EntityStore> store,
        @Nonnull PigRaceSession session,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        PlotInstance square = resolveSquare(plugin, town);
        if (square == null) {
            return;
        }
        Vector3d camera =
            FestivalPrefabSwapService.spotWorldPosition(
                plugin,
                square,
                SIDE_CAMERA_LOCAL_X,
                SIDE_CAMERA_LOCAL_Y,
                SIDE_CAMERA_LOCAL_Z
            );
        Vector3d focus =
            FestivalPrefabSwapService.spotWorldPosition(
                plugin,
                square,
                SIDE_FOCUS_LOCAL_X,
                SIDE_FOCUS_LOCAL_Y,
                SIDE_FOCUS_LOCAL_Z
            );
        Set<UUID> bettors = new HashSet<>(session.betsView().keySet());
        if (bettors.isEmpty()) {
            return;
        }
        forEachPlayer(store, bettors, (pr, playerPos, uuid) -> {
            applyLookAt(
                pr,
                playerPos.x,
                playerPos.y,
                playerPos.z,
                camera.x,
                camera.y,
                camera.z,
                focus.x,
                focus.y,
                focus.z
            );
            session.markRaceCameraViewer(uuid);
        });
    }

    /** Switches existing race-camera viewers to a straight top-down view over the finish line. */
    public static void switchToFinishCamera(
        @Nonnull Store<EntityStore> store,
        @Nonnull PigRaceSession session,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        PlotInstance square = resolveSquare(plugin, town);
        if (square == null) {
            return;
        }
        Set<UUID> viewers = session.raceCameraViewersView();
        if (viewers.isEmpty()) {
            return;
        }
        Vector3d camera =
            FestivalPrefabSwapService.spotWorldPosition(
                plugin,
                square,
                FINISH_CAMERA_LOCAL_X,
                FINISH_CAMERA_LOCAL_Y,
                FINISH_CAMERA_LOCAL_Z
            );
        forEachPlayer(store, viewers, (pr, playerPos, uuid) ->
            applyTopDown(pr, playerPos.x, playerPos.y, playerPos.z, camera.x, camera.y, camera.z)
        );
    }

    /** Restores normal cameras for everyone who was put on the race cam. */
    public static void deactivateAll(@Nonnull Store<EntityStore> store, @Nonnull PigRaceSession session) {
        Set<UUID> viewers = session.takeRaceCameraViewers();
        if (viewers.isEmpty()) {
            return;
        }
        Query<EntityStore> query = Query.and(PlayerRef.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                if (uc == null || pr == null || !viewers.contains(uc.getUuid())) {
                    continue;
                }
                resetToPlayerCamera(pr);
            }
        });
    }

    private static void applyLookAt(
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
        // Negative pitch looks down (top-down uses -π/2). dy is lookY - camY, so looking down is negative.
        float pitch = (float) Math.atan2(dy, Math.max(0.001, horiz));
        writeCamera(playerRef, playerX, playerY, playerZ, camX, camY, camZ, yaw, pitch);
    }

    private static void applyTopDown(
        @Nonnull PlayerRef playerRef,
        double playerX,
        double playerY,
        double playerZ,
        double camX,
        double camY,
        double camZ
    ) {
        writeCamera(playerRef, playerX, playerY, playerZ, camX, camY, camZ, 0f, -TrigMathUtil.PI_HALF);
    }

    private static void writeCamera(
        @Nonnull PlayerRef playerRef,
        double playerX,
        double playerY,
        double playerZ,
        double camX,
        double camY,
        double camZ,
        float yaw,
        float pitch
    ) {
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.positionLerpSpeed = 0.25f;
        settings.rotationLerpSpeed = 0.25f;
        settings.displayCursor = false;
        settings.isFirstPerson = false;
        settings.eyeOffset = false;
        settings.positionType = PositionType.AttachedToPlusOffset;
        settings.positionOffset = new Position(camX - playerX, camY - playerY, camZ - playerZ);
        settings.rotationType = RotationType.Custom;
        settings.rotation = new Direction(yaw, pitch, 0f);
        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, settings));
    }

    private static void resetToPlayerCamera(@Nonnull PlayerRef playerRef) {
        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
    }

    private static void forEachPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull Set<UUID> playerIds,
        @Nonnull PlayerConsumer consumer
    ) {
        Query<EntityStore> query = Query.and(
            PlayerRef.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                if (uc == null || pr == null || tc == null || !playerIds.contains(uc.getUuid())) {
                    continue;
                }
                consumer.accept(pr, tc.getPosition(), uc.getUuid());
            }
        });
    }

    @Nullable
    private static PlotInstance resolveSquare(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        UUID plotId = town.getActiveFestivalPlotId();
        if (plotId == null) {
            return null;
        }
        return town.findPlotById(plotId);
    }

    @FunctionalInterface
    private interface PlayerConsumer {
        void accept(@Nonnull PlayerRef playerRef, @Nonnull Vector3d playerPos, @Nonnull UUID playerUuid);
    }
}
