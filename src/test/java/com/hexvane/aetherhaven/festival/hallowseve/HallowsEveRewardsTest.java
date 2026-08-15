package com.hexvane.aetherhaven.festival.hallowseve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class HallowsEveRewardsTest {
    @Test
    void ticketsAreHalfTheOrbsWithCollectAllBonus() {
        assertEquals(0, HallowsEveRewards.ticketCount(0, 24));
        assertEquals(0, HallowsEveRewards.ticketCount(0, 0));
        assertEquals(4, HallowsEveRewards.ticketCount(8, 24));
        assertEquals(6, HallowsEveRewards.ticketCount(12, 24));
        assertEquals(13, HallowsEveRewards.ticketCount(24, 24));
        assertEquals(5, HallowsEveRewards.ticketCount(8, 8));
        assertEquals(14, HallowsEveRewards.ticketCount(25, 25));
    }

    @Test
    void candyIsHalfTheOrbsRounded() {
        assertEquals(0, HallowsEveRewards.candyCount(0));
        assertEquals(1, HallowsEveRewards.candyCount(1));
        assertEquals(4, HallowsEveRewards.candyCount(8));
        assertEquals(6, HallowsEveRewards.candyCount(12));
        assertEquals(12, HallowsEveRewards.candyCount(24));
    }

    @Test
    void mazeStartYawLooksTowardTheCenterEvenAfterSquareRotation() {
        Vector3d start = new Vector3d(14.5, 6.0, 2.5);
        Vector3d center = new Vector3d(1.5, 7.0, 1.5);
        float yaw = HallowsEveTeleport.yawDegreesToward(start, center);
        assertEquals(Math.toDegrees(Math.atan2(13.0, 1.0)), yaw, 0.01);

        // Prefab 90 degree rotation: (x, z) -> (z, -x), then block-center offset.
        Vector3d startRotated = new Vector3d(2.5, 6.0, -13.5);
        Vector3d centerRotated = new Vector3d(1.5, 7.0, -0.5);
        float yawRotated = HallowsEveTeleport.yawDegreesToward(startRotated, centerRotated);
        assertEquals(yaw + 90.0, yawRotated, 0.01);
    }

    @Test
    void jackLanternFacesAwayFromTheEntranceEvenAfterSquareRotation() {
        Vector3d pumpkin = new Vector3d(1.0, 7.0, 0.0);
        Vector3d entrance = new Vector3d(2.5, 6.0, -12.5);
        float toward = HallowsEveTeleport.yawDegreesToward(pumpkin, entrance);
        float away = HallowsEveTeleport.yawDegreesAwayFrom(pumpkin, entrance);
        assertEquals(toward + 180.0, away, 0.01);

        // Prefab 90 degree rotation: (x, z) -> (z, -x), then block-center offset.
        Vector3d pumpkinRotated = new Vector3d(0.0, 7.0, -1.0);
        Vector3d entranceRotated = new Vector3d(-12.5, 6.0, -2.5);
        float awayRotated = HallowsEveTeleport.yawDegreesAwayFrom(pumpkinRotated, entranceRotated);
        assertEquals(away + 90.0, awayRotated, 0.01);
    }

    @Test
    void mazeRunCostsTenGoldLikeOtherFestivalGames() {
        assertEquals(10, HallowsEveIds.MAZE_COST_GOLD);
    }

    @Test
    void festivalBatsUseTheVanillaBatLook() {
        assertEquals("Aetherhaven_Festival_Hallows_Eve_Bat", HallowsEveIds.BAT_NPC_ROLE);
        assertEquals(6, HallowsEveIds.BAT_COUNT);
    }

    @Test
    void mazeScoreboardRanksFullClearsByTimeLeftThenByOrbCount() {
        HallowsEveScore twenty = HallowsEveScore.of(20, 25, 0L);
        HallowsEveScore twentyFour = HallowsEveScore.of(24, 25, 0L);
        HallowsEveScore allSlow = HallowsEveScore.of(25, 25, 4_000L);
        HallowsEveScore allFast = HallowsEveScore.of(25, 25, 12_000L);

        assertTrue(twentyFour.isBetterThan(twenty));
        assertTrue(allSlow.isBetterThan(twentyFour));
        assertTrue(allFast.isBetterThan(allSlow));
        assertFalse(twenty.isBetterThan(twentyFour));
        assertFalse(allSlow.isBetterThan(allFast));
        assertEquals("20 orbs", twenty.scoreLabel());
        assertEquals("25 orbs, 12 seconds left", allFast.scoreLabel());
        assertEquals("1 orb", HallowsEveScore.of(1, 25, 0L).scoreLabel());
    }

    @Test
    void scoreboardFileKeepsTheBetterRun() {
        HallowsEveLeaderboardWorldFile file = new HallowsEveLeaderboardWorldFile();
        assertTrue(file.recordBest("p", "Pat", 18, 25, 0L));
        assertFalse(file.recordBest("p", "Pat", 12, 25, 0L));
        assertTrue(file.recordBest("p", "Pat", 25, 25, 5_000L));
        assertTrue(file.recordBest("p", "Pat", 25, 25, 9_000L));
        assertFalse(file.recordBest("p", "Pat", 25, 25, 8_000L));
        HallowsEveLeaderboard.Entry best = file.find("p");
        assertEquals(25, best.collected());
        assertEquals(9_000L, best.remainingMs());
        assertFalse(file.recordBest("p", "Pat", 0, 25, 0L));
    }

    @Test
    void collectSoundUsesARealEventId() {
        assertEquals("Aetherhaven_Festival_Hallows_Eve_Orb", HallowsEveIds.COLLECT_SOUND);
    }

    @Test
    void iceEssenceMarkerMatchingIsLoose() {
        assertTrue(HallowsEveIds.isIceEssenceMarker("Ingredient_Ice_Essence"));
        assertTrue(HallowsEveIds.isIceEssenceMarker("ingredient_ice_essence"));
        assertTrue(HallowsEveIds.isIceEssenceMarker("  Ingredient_Ice_Essence  "));
        assertFalse(HallowsEveIds.isIceEssenceMarker(null));
        assertFalse(HallowsEveIds.isIceEssenceMarker("Ingredient_Life_Essence"));
        assertFalse(HallowsEveIds.isIceEssenceMarker("Deco_Halloween_Pumpkin_Cute"));
    }

    @Test
    void sessionIsBusyUntilBurstFinishesThenIdleAgain() {
        HallowsEveSession session = new HallowsEveSession();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        long now = 1_000L;

        assertTrue(session.canStart(a));
        assertFalse(session.isBusy());
        assertTrue(session.tryBegin(a, now));
        assertFalse(session.canStart(b));
        assertTrue(session.isBusyForOther(b));
        assertFalse(session.isBusyForOther(a));
        assertTrue(session.isRacer(a));

        session.setTotalOrbs(8);
        session.tickCountdown(now + HallowsEveIds.COUNTDOWN_MS);
        assertEquals(HallowsEveSession.Phase.RACING, session.getPhase());
        for (int i = 0; i < 8; i++) {
            session.addCollected();
        }
        assertTrue(session.tickRace(now + HallowsEveIds.COUNTDOWN_MS + 1));
        assertEquals(HallowsEveSession.Phase.READY_TO_BURST, session.getPhase());
        assertTrue(session.isBusyForOther(b));

        session.beginBurst(now + 10_000L);
        session.finishBurst();
        assertEquals(HallowsEveSession.Phase.IDLE, session.getPhase());
        assertTrue(session.canStart(b));
        assertFalse(session.isBusy());
    }

    @Test
    void zeroOrbRunReturnsToIdleWithoutBurst() {
        HallowsEveSession session = new HallowsEveSession();
        UUID a = UUID.randomUUID();
        long now = 5_000L;
        assertTrue(session.tryBegin(a, now));
        session.setTotalOrbs(8);
        session.tickCountdown(now + HallowsEveIds.COUNTDOWN_MS);
        assertTrue(session.tickRace(now + HallowsEveIds.COUNTDOWN_MS + HallowsEveIds.RACE_MS));
        assertEquals(HallowsEveSession.Phase.IDLE, session.getPhase());
        assertTrue(session.canStart(UUID.randomUUID()));
    }
}
