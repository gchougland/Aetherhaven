package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import java.util.Set;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Festival dance: the two built in dances and the square bounds used to decide who may join in. */
@Tag("town")
final class FestivalDanceSystemTest {
    @Test
    void theTwoBuiltInDanceEmotesAreUsed() {
        assertEquals("DanceBoogie", FestivalDanceSystem.pickDanceEmote(0));
        assertEquals("DancePop", FestivalDanceSystem.pickDanceEmote(1));
        assertEquals(Set.of("DanceBoogie", "DancePop"), Set.of(FestivalDanceSystem.DANCE_EMOTES));
    }

    @Test
    void dancersMustBeStandingOnTheFestivalSquare() {
        PlotInstance square = new PlotInstance();
        square.setState(PlotInstanceState.COMPLETE);
        square.applySignAndFootprint(10, 64, 10, new PlotFootprintRecord(0, 60, 0, 29, 70, 29));

        assertTrue(FestivalDanceSystem.isInsideFestivalSquare(square, new Vector3d(14.5, 64.0, 14.5)));
        assertTrue(
            FestivalDanceSystem.isInsideFestivalSquare(square, new Vector3d(14.5, 59.2, 14.5)),
            "feet can sit just under the solid footprint"
        );
        assertFalse(FestivalDanceSystem.isInsideFestivalSquare(square, new Vector3d(-5.0, 64.0, 14.5)));
        assertFalse(FestivalDanceSystem.isInsideFestivalSquare(square, new Vector3d(14.5, 64.0, 40.0)));
    }
}
