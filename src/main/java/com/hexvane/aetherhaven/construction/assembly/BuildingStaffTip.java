package com.hexvane.aetherhaven.construction.assembly;

import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Where the tip of a held Building Staff is in the world, as well as the server can tell.
 *
 * <p>The effects a player holds (the cone and its stars) are hung on the staff by the client, at the model's own tip,
 * and need none of this. Two things must still be given a world position by the server: the guide bolt, which is an
 * entity that flies to a block, and the line of beads from the staff to a placed block. Both start here.
 *
 * <p>The numbers are the base game's player model in the pose both staff actions play (animation set {@code Stick},
 * clip {@code Cast_Magic}, third person). That clip is one still pose: right arm straight ahead, staff level. The set
 * turns the right shoulder with the camera's pitch, so arm and staff swing about the shoulder as the player looks up
 * and down. This is the third-person position. First person draws the arms with a clip of its own, close to the
 * camera, so no single world position can match both views.
 */
public final class BuildingStaffTip {
    /** Right shoulder joint from the feet: right, up, forward, in blocks. */
    static final double SHOULDER_RIGHT = 0.2266;
    static final double SHOULDER_UP = 1.3530;
    static final double SHOULDER_FORWARD = -0.0147;

    /** The hand's grip (the staff's {@code Handle} origin) from the shoulder, looking level. */
    static final double GRIP_RIGHT = 0.1268;
    static final double GRIP_UP = -0.1056;
    static final double GRIP_FORWARD = 0.5248;

    /** The way the staff points from the grip, looking level. Unit length. */
    static final double AXIS_RIGHT = 0.0192;
    static final double AXIS_UP = 0.0225;
    static final double AXIS_FORWARD = 0.9996;

    /**
     * Blocks from the grip to the tip along the staff: the model's {@code Origin_Projectile} node, 90 model units at 64
     * to the block. The secondary interaction hangs the held effects at this same distance ({@code PositionOffset.X} on
     * {@code Handle}); {@code BuildingStaffAnchorTest} keeps the three in step.
     */
    public static final double TIP_REACH_BLOCKS = 1.406;

    private BuildingStaffTip() {}

    /**
     * @param feet the player's position
     * @param bodyYaw the body's yaw in radians (the transform's rotation, not the head's: the arm is on the body)
     * @param lookPitch the look pitch in radians, positive looking up
     */
    @Nonnull
    public static Vector3d estimate(@Nonnull Vector3dc feet, float bodyYaw, float lookPitch) {
        // Local axes as the base game uses them for a projectile's SpawnOffset: +X right, +Y up, -Z forward,
        // turned by pitch and then by yaw (ProjectileConfig.getCalculatedOffset).
        Vector3d tip = new Vector3d(
            GRIP_RIGHT + AXIS_RIGHT * TIP_REACH_BLOCKS,
            GRIP_UP + AXIS_UP * TIP_REACH_BLOCKS,
            -(GRIP_FORWARD + AXIS_FORWARD * TIP_REACH_BLOCKS)
        );
        tip.rotateX(lookPitch);
        tip.add(SHOULDER_RIGHT, SHOULDER_UP, -SHOULDER_FORWARD);
        tip.rotateY(bodyYaw);
        return tip.add(feet);
    }
}
