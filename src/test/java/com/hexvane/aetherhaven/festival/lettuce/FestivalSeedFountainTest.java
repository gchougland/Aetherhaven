package com.hexvane.aetherhaven.festival.lettuce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3d;
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
        lettuce.setMaxScale(11.2f);

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
    void essenceIsPulledTowardTheMiddleOfTheLettuce() {
        Vector3d mouth = FestivalLettuceAbsorbSystem.mouthPosition(new Vector3d(10.0, 64.0, 10.0), 8.0f);
        assertEquals(10.0, mouth.x, 0.001);
        assertEquals(10.0, mouth.z, 0.001);
        assertTrue(mouth.y > 64.0, "the suck aim point sits up in the model, not at the feet");
    }

    @Test
    void slowestSeedsStillLeaveTheCentreAndFastestStayInsideTheSquare() {
        double[] range = FestivalLettuceBurstSystem.horizontalSpeedRange();
        assertTrue(range[0] > 0.5, "seeds should not drop straight back on the lettuce");
        assertTrue(range[1] < 8.0, "seeds should not clear the square");
        assertEquals(2, range.length);
    }

    @Test
    void overchargeRaisesTheMultiplierInHalfStepsUpToFive() {
        FestivalLettuceComponent lettuce = new FestivalLettuceComponent();
        lettuce.setRequiredEssence(FestivalLettuceComponent.DEFAULT_REQUIRED_ESSENCE);
        lettuce.setSeedsPerBurst(28);

        lettuce.addEssence(20);
        assertTrue(lettuce.isReadyToBurst());
        assertFalse(lettuce.isMaxOvercharge(), "exactly full should wait for the player");
        assertEquals(1.0, lettuce.seedMultiplier(), 0.0001);
        assertEquals(4, lettuce.scaledTicketCount());
        assertEquals(28, lettuce.scaledSeedCount());

        lettuce.addEssence(100);
        assertEquals(120, lettuce.getEssence());
        assertEquals(1.5, lettuce.seedMultiplier(), 0.0001);
        assertEquals(6, lettuce.scaledTicketCount());
        assertEquals(42, lettuce.scaledSeedCount());

        lettuce.addEssence(700);
        assertEquals(FestivalLettuceComponent.MAX_ESSENCE_AT_DEFAULT, lettuce.getEssence());
        assertEquals(5.0, lettuce.seedMultiplier(), 0.0001);
        assertTrue(lettuce.isMaxOvercharge());
        assertEquals(20, lettuce.scaledTicketCount());
        assertEquals(140, lettuce.scaledSeedCount());
    }

    @Test
    void concentratedLifeEssenceIsWorthOneHundred() {
        assertEquals(1, FestivalLettuceComponent.essenceValue(AetherhavenConstants.ITEM_LIFE_ESSENCE));
        assertEquals(
            100,
            FestivalLettuceComponent.essenceValue(AetherhavenConstants.ITEM_LIFE_ESSENCE_CONCENTRATED)
        );
        assertEquals(0, FestivalLettuceComponent.essenceValue("Plant_Seeds_Lettuce"));
    }

    @Test
    void overchargePastFullDoesNotShrinkFillRatio() {
        FestivalLettuceComponent lettuce = new FestivalLettuceComponent();
        lettuce.setRequiredEssence(20);
        lettuce.addEssence(20);
        assertEquals(1.0f, lettuce.fillRatio(), 0.0001f);
        lettuce.addEssence(50);
        assertEquals(1.0f, lettuce.fillRatio(), 0.0001f);
        assertTrue(lettuce.getEssence() > 20);
    }
}
