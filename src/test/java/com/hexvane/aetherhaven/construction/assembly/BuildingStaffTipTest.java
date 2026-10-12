package com.hexvane.aetherhaven.construction.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The server's estimate of the staff tip, for the guide bolt and the bead line. Yaw 0 faces -Z and turns left as it
 * grows; a positive pitch looks up; right of a player facing -Z is +X.
 */
@Tag("construction")
class BuildingStaffTipTest {
    private static final float QUARTER_TURN = (float) (Math.PI / 2.0);
    private static final Vector3d FEET = new Vector3d(100.0, 64.0, -20.0);

    @Test
    void lookingLevelTheTipIsAheadAtChestHeightOnTheRight() {
        Vector3d tip = BuildingStaffTip.estimate(FEET, 0.0F, 0.0F).sub(FEET);
        // Measured from the base game's player model in the third-person Cast_Magic pose.
        assertEquals(0.3805, tip.x, 0.002, "right");
        assertEquals(1.2791, tip.y, 0.002, "up");
        assertEquals(-1.9158, tip.z, 0.002, "ahead");
        // Nowhere near the chest-height point 0.42 ahead of the body that the mint stars used to come from.
        assertTrue(tip.distance(new Vector3d(0.0, 1.32, -0.42)) > 1.5);
    }

    @Test
    void theTipTurnsWithTheBody() {
        // A quarter turn to the left: facing -X, with the right hand towards -Z.
        Vector3d left = BuildingStaffTip.estimate(FEET, QUARTER_TURN, 0.0F).sub(FEET);
        assertEquals(-1.9158, left.x, 0.002);
        assertEquals(1.2791, left.y, 0.002);
        assertEquals(-0.3805, left.z, 0.002);
        // Half a turn: facing +Z, right hand towards -X.
        Vector3d back = BuildingStaffTip.estimate(FEET, 2.0F * QUARTER_TURN, 0.0F).sub(FEET);
        assertEquals(-0.3805, back.x, 0.002);
        assertEquals(1.9158, back.z, 0.002);
        // Moving the player moves the tip with them and changes nothing else.
        Vector3d elsewhere = new Vector3d(-7.5, 120.0, 3.25);
        assertEquals(0.0, BuildingStaffTip.estimate(elsewhere, QUARTER_TURN, 0.3F).sub(elsewhere)
            .distance(BuildingStaffTip.estimate(FEET, QUARTER_TURN, 0.3F).sub(FEET)), 1.0e-9);
    }

    @Test
    void lookingUpAndDownSwingsTheStaffAboutTheShoulder() {
        Vector3d shoulder = new Vector3d(BuildingStaffTip.SHOULDER_RIGHT, BuildingStaffTip.SHOULDER_UP, -BuildingStaffTip.SHOULDER_FORWARD);
        double level = BuildingStaffTip.estimate(new Vector3d(), 0.0F, 0.0F).distance(shoulder);
        assertEquals(1.938, level, 0.002);
        for (float pitch : new float[] {-1.2F, -0.5F, 0.4F, 1.0F, QUARTER_TURN}) {
            Vector3d tip = BuildingStaffTip.estimate(new Vector3d(), 0.0F, pitch);
            assertEquals(level, tip.distance(shoulder), 1.0e-6, "the arm does not stretch");
            assertEquals(0.3805, tip.x, 0.002, "pitch does not move the tip sideways");
        }
        Vector3d up = BuildingStaffTip.estimate(new Vector3d(), 0.0F, 0.6F);
        Vector3d down = BuildingStaffTip.estimate(new Vector3d(), 0.0F, -0.6F);
        assertTrue(up.y > 2.2 && down.y < 0.4, "up " + up.y + ", down " + down.y);
        assertTrue(up.z < -1.4 && down.z < -1.4, "still ahead of the player");
        // Straight up: the tip is above the shoulder.
        Vector3d overhead = BuildingStaffTip.estimate(new Vector3d(), 0.0F, QUARTER_TURN);
        assertEquals(BuildingStaffTip.SHOULDER_UP + 1.9305, overhead.y, 0.002);
        assertEquals(-0.0593, overhead.z, 0.002);
    }

    @Test
    void theTipIsTheModelsReachFromTheGrip() {
        double axis = Math.sqrt(BuildingStaffTip.AXIS_RIGHT * BuildingStaffTip.AXIS_RIGHT
            + BuildingStaffTip.AXIS_UP * BuildingStaffTip.AXIS_UP
            + BuildingStaffTip.AXIS_FORWARD * BuildingStaffTip.AXIS_FORWARD);
        assertEquals(1.0, axis, 0.001, "the staff's direction is a unit vector");
        Vector3d grip = new Vector3d(
            BuildingStaffTip.SHOULDER_RIGHT + BuildingStaffTip.GRIP_RIGHT,
            BuildingStaffTip.SHOULDER_UP + BuildingStaffTip.GRIP_UP,
            -(BuildingStaffTip.SHOULDER_FORWARD + BuildingStaffTip.GRIP_FORWARD));
        assertEquals(BuildingStaffTip.TIP_REACH_BLOCKS, BuildingStaffTip.estimate(new Vector3d(), 0.0F, 0.0F).distance(grip), 0.002);
        // The staff is all but level in that pose and points where the body faces.
        assertTrue(BuildingStaffTip.AXIS_FORWARD > 0.999);
    }
}
