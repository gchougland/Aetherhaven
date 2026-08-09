package com.hexvane.aetherhaven.festival.lettuce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The seed burst should arc up steeply and land back inside the festival square rather than shoot across town. */
@Tag("town")
final class FestivalSeedFountainTest {
    @Test
    void seedsAlwaysLaunchUpwardAndSpreadInEveryDirection() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        boolean sawPositiveX = false;
        boolean sawNegativeX = false;
        boolean sawPositiveZ = false;
        boolean sawNegativeZ = false;
        double[] range = FestivalLettuceBurstSystem.horizontalSpeedRange();

        for (int i = 0; i < 500; i++) {
            float[] v = FestivalLettuceBurstSystem.launchVelocity(rnd);
            assertTrue(v[1] > 0.0f, "seed should be thrown upward");
            double horizontal = Math.hypot(v[0], v[2]);
            assertTrue(horizontal >= range[0] - 1.0e-4, "horizontal speed below the fountain range");
            assertTrue(horizontal <= range[1] + 1.0e-4, "horizontal speed above the fountain range");
            assertTrue(v[1] > horizontal, "the arc should be steeper than it is wide");
            sawPositiveX |= v[0] > 0.1f;
            sawNegativeX |= v[0] < -0.1f;
            sawPositiveZ |= v[2] > 0.1f;
            sawNegativeZ |= v[2] < -0.1f;
        }

        assertTrue(sawPositiveX && sawNegativeX && sawPositiveZ && sawNegativeZ, "seeds should spray all around");
    }

    @Test
    void aLettuceThatHasPoppedStopsGrowingAndDrinking() {
        FestivalLettuceComponent lettuce = new FestivalLettuceComponent();
        lettuce.setRequiredEssence(4);
        lettuce.addEssence(4);
        assertTrue(lettuce.isFull());

        lettuce.setState(FestivalLettuceComponent.STATE_SPENT);
        lettuce.resetEssence();

        assertTrue(lettuce.isSpent());
        assertFalse(lettuce.isGrowing(), "a spent lettuce must not drink more essence");
        assertFalse(lettuce.isBursting());
        assertFalse(lettuce.isFull());
        assertEquals(0.0f, lettuce.fillRatio(), 0.0001f);
    }

    @Test
    void theLettuceReachesFurtherForEssenceAsItGrows() {
        FestivalLettuceComponent lettuce = new FestivalLettuceComponent();
        lettuce.setRequiredEssence(10);
        lettuce.setMinScale(4.0f);
        lettuce.setMaxScale(14.0f);

        double empty = FestivalLettuceAbsorbSystem.absorbRadius(lettuce);
        lettuce.addEssence(9);
        double nearlyFull = FestivalLettuceAbsorbSystem.absorbRadius(lettuce);

        assertTrue(empty >= 5.0, "a small lettuce still needs to catch essence thrown from outside it");
        assertTrue(
            nearlyFull > lettuce.getMaxScale() * 0.4,
            "a grown lettuce should reach past its own model"
        );
        assertTrue(nearlyFull > empty);
    }

    @Test
    void slowestSeedsStillLeaveTheCentreAndFastestStayInsideTheSquare() {
        double[] range = FestivalLettuceBurstSystem.horizontalSpeedRange();
        assertTrue(range[0] > 0.5, "seeds should not drop straight back on the lettuce");
        assertTrue(range[1] < 8.0, "seeds should not clear the square");
        assertEquals(2, range.length);
    }
}
