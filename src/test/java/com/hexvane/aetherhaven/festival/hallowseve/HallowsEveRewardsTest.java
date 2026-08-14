package com.hexvane.aetherhaven.festival.hallowseve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
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
