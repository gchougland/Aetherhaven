package com.hexvane.aetherhaven.tourist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("tourist")
class TouristPortalStandDetectionTest {

    @Test
    void isNearPortalStand_acceptsFeetWithinRadiusOfBaseCenter() {
        Vector3i base = new Vector3i(10, 64, 20);
        assertTrue(TouristPortalBlockUtil.isNearPortalStand(base, new Vector3d(10.5, 65.0, 20.5)));
        assertTrue(TouristPortalBlockUtil.isNearPortalStand(base, new Vector3d(12.0, 65.0, 20.0)));
    }

    @Test
    void isNearPortalStand_rejectsFeetOutsideRadius() {
        Vector3i base = new Vector3i(10, 64, 20);
        assertFalse(TouristPortalBlockUtil.isNearPortalStand(base, new Vector3d(13.0, 65.0, 20.0)));
        assertFalse(TouristPortalBlockUtil.isNearPortalStand(base, new Vector3d(10.5, 65.0, 24.0)));
    }

    @Test
    void travelPlayerState_sameProbeColumn_skipsWhenBlockColumnUnchanged() {
        TouristPortalTravelPlayerState state = new TouristPortalTravelPlayerState();
        state.setLastProbeBlock(5, -3);

        assertTrue(state.sameProbeColumn(5, -3));
        assertFalse(state.sameProbeColumn(5, -2));
        assertFalse(state.sameProbeColumn(6, -3));
    }

    @Test
    void travelPlayerState_unprobedColumn_neverMatches() {
        TouristPortalTravelPlayerState state = new TouristPortalTravelPlayerState();
        assertFalse(state.sameProbeColumn(0, 0));
    }

    @Test
    void resolvePortalAtPlayerFeet_doesNotRefreshTravelNetwork() throws Exception {
        String body = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/com/hexvane/aetherhaven/tourist/TouristPortalTravelService.java")
        );
        int start = body.indexOf("resolvePortalAtPlayerFeet");
        int end = body.indexOf("public static boolean isActivePortal", start);
        String methodSource = body.substring(start, end);
        assertFalse(
            methodSource.contains("refreshTravelNetwork"),
            "resolvePortalAtPlayerFeet must not call refreshTravelNetwork on the hot path"
        );
    }
}
