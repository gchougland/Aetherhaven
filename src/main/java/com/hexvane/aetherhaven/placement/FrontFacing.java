package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.wall.WallCardinal;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Authored prefab-local cardinal that is the "front" of a building or prop. North matches Hytale
 * {@code Rotation.None} forward ({@code -Z}).
 */
public final class FrontFacing {
    public static final String NORTH = "North";
    public static final String EAST = "East";
    public static final String SOUTH = "South";
    public static final String WEST = "West";

    private FrontFacing() {}

    @Nonnull
    public static String normalize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return NORTH;
        }
        String t = raw.trim();
        if (EAST.equalsIgnoreCase(t)) {
            return EAST;
        }
        if (SOUTH.equalsIgnoreCase(t)) {
            return SOUTH;
        }
        if (WEST.equalsIgnoreCase(t)) {
            return WEST;
        }
        return NORTH;
    }

    @Nonnull
    public static WallCardinal toCardinal(@Nullable String raw) {
        return switch (normalize(raw)) {
            case EAST -> WallCardinal.EAST;
            case SOUTH -> WallCardinal.SOUTH;
            case WEST -> WallCardinal.WEST;
            default -> WallCardinal.NORTH;
        };
    }

    /** Dominant look cardinal from player body yaw (yaw 0 faces north / {@code -Z}). */
    @Nonnull
    public static WallCardinal lookCardinalFromYaw(float yawRadians) {
        double dx = -Math.sin(yawRadians);
        double dz = -Math.cos(yawRadians);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? WallCardinal.EAST : WallCardinal.WEST;
        }
        return dz >= 0 ? WallCardinal.SOUTH : WallCardinal.NORTH;
    }

    /**
     * Placement rotation steps (0..3 CW) so the authored front faces toward the player (opposite of look).
     */
    public static int rotationStepsFacingPlayer(@Nullable String frontFacing, float playerYawRadians) {
        WallCardinal desiredWorldFront = lookCardinalFromYaw(playerYawRadians).opposite();
        return WallCardinal.stepsAligning(toCardinal(frontFacing), desiredWorldFront);
    }

    /**
     * Yaw orbit (degrees, around Y) applied to the default marketplace camera offset so {@code frontFacing}
     * faces the camera. South keeps today's offset; North is 180° so the {@code -Z} front is shown.
     */
    public static int yawOrbitDegreesForPreview(@Nullable String frontFacing) {
        return switch (normalize(frontFacing)) {
            case EAST -> 90;
            case SOUTH -> 0;
            case WEST -> 270;
            default -> 180;
        };
    }

    /** CW steps that map authored front onto North for isometric icon projection. */
    public static int iconAlignStepsToNorth(@Nullable String frontFacing) {
        return WallCardinal.stepsAligning(toCardinal(frontFacing), WallCardinal.NORTH);
    }
}
