package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseInputType;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Plot placement UI camera: matches {@code /camera topdown} settings (see {@code PlayerCameraTopdownCommand})
 * with an adjustable {@link ServerCameraSettings#distance}.
 */
public final class PlotPlacementCameraUtil {
    public static final float DEFAULT_DISTANCE = 20f;
    public static final float MIN_DISTANCE = 12f;
    public static final float MAX_DISTANCE = 48f;
    public static final float DISTANCE_STEP = 4f;
    /** Total world-space pan (blocks) applied over {@link #SMOOTH_PAN_STEPS} when using pan buttons. */
    public static final double PAN_STEP = 3.0;

    public static final int SMOOTH_PAN_STEPS = 10;
    public static final long SMOOTH_PAN_STEP_DELAY_MS = 28L;

    private PlotPlacementCameraUtil() {}

    @Nonnull
    public static ServerCameraSettings birdsEyeSettings(float distance, @Nonnull Position offsetFromPlayer) {
        float d = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
        ServerCameraSettings cameraSettings = new ServerCameraSettings();
        // Slower lerp so each small pan step blends smoothly on the client.
        cameraSettings.positionLerpSpeed = 0.1F;
        cameraSettings.rotationLerpSpeed = 0.12F;
        cameraSettings.distance = d;
        cameraSettings.displayCursor = true;
        cameraSettings.sendMouseMotion = true;
        cameraSettings.isFirstPerson = false;
        cameraSettings.movementForceRotationType = MovementForceRotationType.Custom;
        cameraSettings.eyeOffset = true;
        cameraSettings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
        cameraSettings.positionOffset = offsetFromPlayer;
        cameraSettings.rotationType = RotationType.Custom;
        cameraSettings.rotation = new Direction(0.0F, (float) (-Math.PI / 2), 0.0F);
        cameraSettings.mouseInputType = MouseInputType.LookAtPlane;
        cameraSettings.planeNormal = new Vector3f(0.0F, 1.0F, 0.0F);
        return cameraSettings;
    }

    /**
     * @param focusX/Y/Z world position the top-down rig should look at (typically preview footprint center + optional pan).
     */
    public static void applyBirdsEye(
        @Nonnull PlayerRef playerRef,
        float distance,
        double playerX,
        double playerY,
        double playerZ,
        double focusX,
        double focusY,
        double focusZ
    ) {
        Position offset = new Position(focusX - playerX, focusY - playerY, focusZ - playerZ);
        playerRef
            .getPacketHandler()
            .writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, birdsEyeSettings(distance, offset)));
    }

    public static void resetToPlayerCamera(@Nonnull PlayerRef playerRef) {
        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
    }
}
