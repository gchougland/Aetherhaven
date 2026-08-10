package com.hexvane.aetherhaven.festival.pigrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class PigRaceSessionTest {
    @Test
    void payoutTableMatchesPlan() {
        assertEquals(1, PigRaceLanes.ticketPayout(10));
        assertEquals(3, PigRaceLanes.ticketPayout(25));
        assertEquals(7, PigRaceLanes.ticketPayout(50));
        assertEquals(15, PigRaceLanes.ticketPayout(100));
        assertEquals(0, PigRaceLanes.ticketPayout(15));
    }

    @Test
    void lanesAreFourDistinctRolesLeftToRight() {
        List<PigRaceLanes.Lane> lanes = PigRaceLanes.lanes();
        assertEquals(4, lanes.size());
        assertEquals("Aetherhaven_Festival_Pig_Race_Pink", lanes.get(0).npcRoleId());
        assertEquals("Aetherhaven_Festival_Pig_Race_Boar", lanes.get(1).npcRoleId());
        assertEquals("Aetherhaven_Festival_Pig_Race_Undead", lanes.get(2).npcRoleId());
        assertEquals("Aetherhaven_Festival_Pig_Race_Wild", lanes.get(3).npcRoleId());
        assertTrue(lanes.get(0).startLocalX() < lanes.get(1).startLocalX());
        assertTrue(lanes.get(1).startLocalX() < lanes.get(2).startLocalX());
        assertTrue(lanes.get(2).startLocalX() < lanes.get(3).startLocalX());
        assertEquals(-8, lanes.get(0).startLocalZ());
        assertEquals(8, lanes.get(0).finishLocalZ());
    }

    @Test
    void oneBetPerPlayerAndStartRequiresBetsAndPigs() {
        PigRaceSession session = new PigRaceSession();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertFalse(session.canStartRace());
        assertFalse(session.canPlaceBet(a));
        assertFalse(session.placeBet(a, 0, 10));
        session.setRacers(List.of(racer(0), racer(1), racer(2), racer(3)));
        assertTrue(session.canPlaceBet(a));
        assertTrue(session.placeBet(a, 0, 10));
        assertFalse(session.placeBet(a, 1, 25));
        assertTrue(session.placeBet(b, 2, 50));
        assertTrue(session.canStartRace());
    }

    @Test
    void startDelayHoldsPigsUntilGoCue() {
        PigRaceSession session = new PigRaceSession();
        UUID a = UUID.randomUUID();
        session.setRacers(List.of(racer(0), racer(1), racer(2), racer(3)));
        assertTrue(session.placeBet(a, 0, 10));
        assertTrue(session.beginRacing());
        long now = System.currentTimeMillis();
        assertTrue(session.consumeRaceStartMusic());
        assertFalse(session.consumeRaceStartMusic());
        assertTrue(session.isWaitingToGo(now));
        assertFalse(session.consumeRaceGoCue(now));
        assertFalse(session.isWaitingToGo(now + PigRaceLanes.RACE_START_DELAY_MS + 1));
        assertTrue(session.consumeRaceGoCue(now + PigRaceLanes.RACE_START_DELAY_MS + 1));
        assertFalse(session.consumeRaceGoCue(now + PigRaceLanes.RACE_START_DELAY_MS + 2));
    }

    @Test
    void finishCameraTriggersNearEndOfTrack() {
        PigRaceSession session = new PigRaceSession();
        UUID a = UUID.randomUUID();
        session.setRacers(List.of(racer(0), racer(1), racer(2), racer(3)));
        assertTrue(session.placeBet(a, 0, 10));
        assertTrue(session.beginRacing());
        assertFalse(session.consumeFinishCamera(8.0, 16.0));
        assertTrue(session.consumeFinishCamera(14.0, 16.0));
        assertFalse(session.consumeFinishCamera(15.0, 16.0));
    }

    @Test
    void cannotStartSecondRaceWhileRacing() {
        PigRaceSession session = new PigRaceSession();
        UUID a = UUID.randomUUID();
        List<PigRaceSession.Racer> racers = List.of(racer(0), racer(1), racer(2), racer(3));
        session.setRacers(racers);
        assertTrue(session.placeBet(a, 1, 25));
        assertTrue(session.beginRacing());
        assertFalse(session.canStartRace());
        assertFalse(session.canPlaceBet(UUID.randomUUID()));
        assertFalse(session.beginRacing(racers));
    }

    @Test
    void winnersGetTicketsLosersCanBetAgainAfterLobby() {
        PigRaceSession session = new PigRaceSession();
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        session.setRacers(List.of(racer(0), racer(1), racer(2), racer(3)));
        assertTrue(session.placeBet(winner, 2, 100));
        assertTrue(session.placeBet(loser, 0, 50));
        assertTrue(session.beginRacing());
        assertTrue(session.finishRace(2, 1_000L, 100L));
        assertTrue(session.hasWinnings(winner));
        assertFalse(session.hasWinnings(loser));
        assertTrue(session.hasPendingLoss(loser));
        assertFalse(session.hasPendingLoss(winner));
        assertEquals(15, session.collectWinnings(winner));
        assertEquals(0, session.collectWinnings(winner));
        assertEquals(0, session.collectWinnings(loser));
        assertTrue(session.acknowledgeLoss(loser));
        assertFalse(session.hasPendingLoss(loser));
        // Pigs remain registered so another race can start after results.
        assertEquals(4, session.racersView().size());
        // Hold waits until every lane finishes, then RESULTS_HOLD_MS.
        assertFalse(session.tryReturnToLobby(1_200L));
        session.markLaneFinished(0, 1_100L);
        session.markLaneFinished(1, 1_100L);
        session.markLaneFinished(3, 1_100L);
        assertFalse(session.tryReturnToLobby(1_150L));
        assertTrue(session.tryReturnToLobby(1_200L));
        assertEquals(PigRaceSession.Phase.LOBBY, session.getPhase());
        assertTrue(session.canPlaceBet(winner));
        assertTrue(session.canPlaceBet(loser));
        assertTrue(session.placeBet(loser, 1, 25));
        assertTrue(session.canStartRace());
    }

    @Test
    void resultsHoldStartsOnlyAfterEveryPigFinishes() {
        PigRaceSession session = new PigRaceSession();
        UUID a = UUID.randomUUID();
        session.setRacers(List.of(racer(0), racer(1), racer(2), racer(3)));
        assertTrue(session.placeBet(a, 0, 10));
        assertTrue(session.beginRacing());
        assertTrue(session.finishRace(0, 1_000L, 50L));
        assertEquals(PigRaceSession.Phase.RESULTS, session.getPhase());
        assertFalse(session.tryReturnToLobby(2_000L));
        session.markLaneFinished(1, 1_200L);
        session.markLaneFinished(2, 1_300L);
        assertFalse(session.tryReturnToLobby(2_000L));
        session.markLaneFinished(3, 1_400L);
        assertFalse(session.tryReturnToLobby(1_425L));
        assertTrue(session.tryReturnToLobby(1_450L));
    }

    @Nonnull
    private static PigRaceSession.Racer racer(int lane) {
        return new PigRaceSession.Racer(lane, UUID.randomUUID(), 1.0, 0, 0, 0, 0, 0, 16);
    }
}
