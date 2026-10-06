package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.math.vector.Rotation3f;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.hexvane.aetherhaven.placement.PlotPlacementNudgeUtil.Horizontal.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class PlotPlacementNudgeTest {
    @Test void turnedHeadControlsAllArrowsEvenWhenBodyStillFacesNorth() {
        var head = new Rotation3f();
        head.setYaw((float) (-Math.PI / 2));
        head.setPitch(1.2f);
        float yaw = PlotPlacementNudgeUtil.lookYaw(head, new Rotation3f());
        assertArrayEquals(new int[]{1,0}, PlotPlacementNudgeUtil.horizontalStep(false,yaw,NEG_Z));
        assertArrayEquals(new int[]{-1,0}, PlotPlacementNudgeUtil.horizontalStep(false,yaw,POS_Z));
        assertArrayEquals(new int[]{0,-1}, PlotPlacementNudgeUtil.horizontalStep(false,yaw,NEG_X));
        assertArrayEquals(new int[]{0,1}, PlotPlacementNudgeUtil.horizontalStep(false,yaw,POS_X));
    }

    @Test void forwardFollowsEveryCardinalLookDirection() {
        int[][] expected = {{0,-1},{-1,0},{0,1},{1,0}};
        for(int quarter=0;quarter<4;quarter++) {
            var head = new Rotation3f();
            head.setYaw((float) (quarter*Math.PI/2));
            float yaw = PlotPlacementNudgeUtil.lookYaw(head, new Rotation3f());
            assertArrayEquals(expected[quarter], PlotPlacementNudgeUtil.horizontalStep(false,yaw,NEG_Z));
        }
    }

    @Test void birdsEyeArrowsStayFixedDespiteHeadDirection() {
        float yaw = (float) (Math.PI/2);
        assertArrayEquals(new int[]{0,-1}, PlotPlacementNudgeUtil.horizontalStep(true,yaw,NEG_Z));
        assertArrayEquals(new int[]{0,1}, PlotPlacementNudgeUtil.horizontalStep(true,yaw,POS_Z));
        assertArrayEquals(new int[]{-1,0}, PlotPlacementNudgeUtil.horizontalStep(true,yaw,NEG_X));
        assertArrayEquals(new int[]{1,0}, PlotPlacementNudgeUtil.horizontalStep(true,yaw,POS_X));
    }

    @Test void absentOrInvalidHeadFallsBackToBodyAndThenNorth() {
        var body = new Rotation3f();
        body.setYaw(1.5f);
        assertEquals(1.5f, PlotPlacementNudgeUtil.lookYaw(null,body));
        var invalid = new Rotation3f();
        invalid.setYaw(Float.NaN);
        assertEquals(1.5f, PlotPlacementNudgeUtil.lookYaw(invalid,body));
        assertEquals(0f, PlotPlacementNudgeUtil.lookYaw(null,null));
        assertEquals(0f, PlotPlacementNudgeUtil.lookYaw(invalid,invalid));
    }
}
