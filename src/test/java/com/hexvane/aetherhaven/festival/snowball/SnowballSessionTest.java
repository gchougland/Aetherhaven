package com.hexvane.aetherhaven.festival.snowball;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class SnowballSessionTest {
    @Test
    void joinCapsAtEightAndLeaveWorksOnlyInLobby() {
        SnowballSession session = readySession();
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < SnowballIds.MAX_PLAYERS; i++) {
            UUID player = UUID.randomUUID();
            players.add(player);
            assertTrue(session.join(player));
        }
        UUID extra = UUID.randomUUID();
        assertFalse(session.canJoin(extra));
        assertFalse(session.join(extra));
        assertEquals(SnowballIds.MAX_PLAYERS, session.joinedPlayerCount());

        UUID first = players.get(0);
        assertTrue(session.leave(first));
        assertFalse(session.isJoined(first));
        assertTrue(session.join(extra));

        assertTrue(session.beginFighting(1_000L));
        assertFalse(session.leave(extra));
        assertTrue(session.isJoined(extra));
        assertTrue(session.isFightBusy());
    }

    @Test
    void fillAndAssignKeepsFivePlayersAsEvenAsPossible() {
        SnowballSession session = readySession();
        List<UUID> players = List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        for (UUID player : players) {
            assertTrue(session.join(player));
        }
        assertTrue(session.beginFighting(2_000L));
        assertTrue(session.fillAndAssign(players, List.of(), 2_000L));
        assertEquals(5, session.fightersView().size());
        int a = session.livingCount(SnowballIds.Team.A);
        int b = session.livingCount(SnowballIds.Team.B);
        assertEquals(5, a + b);
        assertEquals(1, Math.abs(a - b));
    }

    @Test
    void fillAndAssignSplitsPlayersEvenlyBeforeVillagers() {
        SnowballSession session = readySession();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        List<UUID> villagers = List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        assertTrue(session.join(p1));
        assertTrue(session.join(p2));
        assertTrue(session.beginFighting(1_000L));
        assertTrue(session.fillAndAssign(List.of(p1, p2), villagers, 1_000L));
        assertEquals(1, playerCountOnTeam(session, SnowballIds.Team.A));
        assertEquals(1, playerCountOnTeam(session, SnowballIds.Team.B));

        SnowballSession three = readySession();
        assertTrue(three.join(p1));
        assertTrue(three.join(p2));
        assertTrue(three.join(p3));
        assertTrue(three.beginFighting(2_000L));
        assertTrue(three.fillAndAssign(List.of(p1, p2, p3), villagers, 2_000L));
        int aPlayers = playerCountOnTeam(three, SnowballIds.Team.A);
        int bPlayers = playerCountOnTeam(three, SnowballIds.Team.B);
        assertEquals(3, aPlayers + bPlayers);
        assertEquals(1, Math.abs(aPlayers - bPlayers));
        assertTrue(aPlayers == 2 || bPlayers == 2);
    }

    @Test
    void hitsOnlyCountFromTheOtherTeamAndOutFightersCannotBeHitAgain() {
        SnowballSession session = twoPlayerFight();
        SnowballSession.Fighter a = teamPlayer(session, SnowballIds.Team.A);
        SnowballSession.Fighter b = teamPlayer(session, SnowballIds.Team.B);

        assertFalse(session.tryHit(a.uuid(), a.uuid()));
        assertTrue(session.tryHit(b.uuid(), a.uuid()));
        assertEquals(2, b.lives());
        assertTrue(session.tryHit(b.uuid(), a.uuid()));
        assertTrue(session.tryHit(b.uuid(), a.uuid()));
        assertEquals(0, b.lives());
        assertTrue(b.isOut());
        assertFalse(session.tryHit(b.uuid(), a.uuid()));
        assertTrue(session.isOutFighter(b.uuid()));
        assertFalse(session.isLivingFighter(b.uuid()));
        assertFalse(session.wouldHit(a.uuid(), b.uuid()));
        assertFalse(session.tryHit(a.uuid(), b.uuid()));
        assertEquals(3, session.hitsFor(a.uuid()));
        assertEquals(0, session.hitsFor(b.uuid()));
        assertEquals(3, a.lives());
    }

    @Test
    void playerHitsCountVillagerVictimsAndIgnoreSameTeam() {
        SnowballSession session = readySession();
        UUID player = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        UUID villager = UUID.randomUUID();
        assertTrue(session.join(player));
        assertTrue(session.beginFighting(1_000L));
        assertTrue(session.fillAndAssign(List.of(player), List.of(teammate, villager, UUID.randomUUID()), 1_000L));
        SnowballSession.Fighter playerFighter = session.fighter(player);
        UUID opponent = null;
        UUID sameTeam = null;
        for (SnowballSession.Fighter fighter : session.fightersView()) {
            if (fighter.uuid().equals(player)) {
                continue;
            }
            if (fighter.team() == playerFighter.team()) {
                sameTeam = fighter.uuid();
            } else {
                opponent = fighter.uuid();
            }
        }
        assertTrue(opponent != null);
        assertTrue(sameTeam != null);
        assertFalse(session.tryHit(sameTeam, player));
        assertEquals(0, session.hitsFor(player));
        assertTrue(session.tryHit(opponent, player));
        assertEquals(1, session.hitsFor(player));
        assertEquals(0, session.hitsFor(opponent));
    }

    @Test
    void scoreboardFileKeepsTheHigherHitCount() {
        SnowballLeaderboardWorldFile file = new SnowballLeaderboardWorldFile();
        assertTrue(file.recordBest("p", "Pat", 4));
        assertFalse(file.recordBest("p", "Pat", 2));
        assertTrue(file.recordBest("p", "Pat", 7));
        assertFalse(file.recordBest("p", "Pat", 7));
        assertTrue(file.recordBest("p", "Patrica", 7));
        SnowballLeaderboard.Entry best = file.find("p");
        assertEquals(7, best.hits());
        assertEquals("Patrica", best.playerName());
        assertFalse(file.recordBest("p", "Patrica", 0));
    }

    @Test
    void wipeEndsTheFightForTheTeamThatStillHasSnowflakes() {
        SnowballSession session = twoPlayerFight();
        SnowballSession.Fighter a = teamPlayer(session, SnowballIds.Team.A);
        SnowballSession.Fighter b = teamPlayer(session, SnowballIds.Team.B);
        assertTrue(session.tryHit(b.uuid(), a.uuid()));
        assertTrue(session.tryHit(b.uuid(), a.uuid()));
        assertTrue(session.tryHit(b.uuid(), a.uuid()));
        assertTrue(session.tryFinish(3_000L));
        assertEquals(SnowballIds.Team.A, session.winningTeam());
        assertTrue(session.hasPendingTickets(a.uuid()));
        assertFalse(session.hasPendingTickets(b.uuid()));
        assertEquals(SnowballIds.WIN_TICKETS, session.collectTickets(a.uuid()));
        assertEquals(SnowballIds.WIN_TICKETS, session.peekLastCollectedTickets(a.uuid()));
        assertEquals(0, session.collectTickets(b.uuid()));
    }

    @Test
    void timeoutAwardsTheTeamWithMoreSnowflakesLeft() {
        SnowballSession session = twoPlayerFight();
        SnowballSession.Fighter a = teamPlayer(session, SnowballIds.Team.A);
        SnowballSession.Fighter b = teamPlayer(session, SnowballIds.Team.B);
        assertTrue(session.tryHit(a.uuid(), b.uuid()));
        assertTrue(session.tryHit(a.uuid(), b.uuid()));
        long end = session.getFightEndEpochMs();
        assertFalse(session.tryFinish(end - 1L));
        assertTrue(session.tryFinish(end));
        assertEquals(SnowballIds.Team.B, session.winningTeam());
        assertEquals(SnowballIds.WIN_TICKETS, session.collectTickets(b.uuid()));
        assertEquals(0, session.collectTickets(a.uuid()));
    }

    @Test
    void tieAwardsBothPlayerTeamsFiveTickets() {
        SnowballSession session = twoPlayerFight();
        SnowballSession.Fighter a = teamPlayer(session, SnowballIds.Team.A);
        SnowballSession.Fighter b = teamPlayer(session, SnowballIds.Team.B);
        assertTrue(session.tryFinish(session.getFightEndEpochMs()));
        assertNull(session.winningTeam());
        assertEquals(SnowballIds.WIN_TICKETS, session.collectTickets(a.uuid()));
        assertEquals(SnowballIds.WIN_TICKETS, session.collectTickets(b.uuid()));
    }

    @Test
    void hudPlayerListIsTakenBeforeLobbyReset() {
        SnowballSession session = twoPlayerFight();
        assertEquals(2, session.hudPlayerUuids().size());
        assertTrue(session.tryFinish(session.getFightEndEpochMs()));
        assertEquals(2, session.hudPlayerUuids().size());
        assertTrue(session.tryReturnToLobby(session.getFightEndEpochMs() + SnowballIds.RESULTS_HOLD_MS));
        assertTrue(session.hudPlayerUuids().isEmpty());
    }

    @Test
    void consumeHitProjectileOnlyAcceptsATokenOnce() {
        SnowballSession session = twoPlayerFight();
        assertTrue(session.consumeHitProjectile(42));
        assertFalse(session.consumeHitProjectile(42));
        assertTrue(session.consumeHitProjectile(43));
    }

    @Test
    void villagersDoNotCollectTickets() {
        SnowballSession session = readySession();
        UUID player = UUID.randomUUID();
        UUID villager = UUID.randomUUID();
        assertTrue(session.join(player));
        assertTrue(session.beginFighting(1_000L));
        assertTrue(session.fillAndAssign(List.of(player), List.of(villager), 1_000L));
        SnowballSession.Fighter playerFighter = session.fighter(player);
        SnowballSession.Fighter villagerFighter = session.fighter(villager);
        assertTrue(playerFighter.isPlayer());
        assertFalse(villagerFighter.isPlayer());
        assertTrue(session.tryHit(player, villager));
        assertTrue(session.tryHit(player, villager));
        assertTrue(session.tryHit(player, villager));
        assertTrue(session.tryFinish(2_000L));
        assertEquals(villagerFighter.team(), session.winningTeam());
        assertFalse(session.hasPendingTickets(villager));
        assertFalse(session.hasPendingTickets(player));
    }

    @Nonnull
    private static SnowballSession readySession() {
        SnowballSession session = new SnowballSession();
        session.setCourse(pads(4, -11, 90f), pads(4, 11, 270f), new SnowballSession.StartPad(0, 6, -13, 0), List.of());
        return session;
    }

    @Nonnull
    private static SnowballSession twoPlayerFight() {
        SnowballSession session = readySession();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(session.join(a));
        assertTrue(session.join(b));
        assertTrue(session.beginFighting(1_000L));
        assertTrue(session.fillAndAssign(List.of(a, b), List.of(), 1_000L));
        return session;
    }

    @Nonnull
    private static SnowballSession.Fighter teamPlayer(
        @Nonnull SnowballSession session,
        @Nonnull SnowballIds.Team team
    ) {
        return session.fightersView().stream().filter(f -> f.team() == team).findFirst().orElseThrow();
    }

    private static int playerCountOnTeam(@Nonnull SnowballSession session, @Nonnull SnowballIds.Team team) {
        int count = 0;
        for (SnowballSession.Fighter fighter : session.fightersView()) {
            if (fighter.team() == team && fighter.isPlayer()) {
                count++;
            }
        }
        return count;
    }

    @Nonnull
    private static List<SnowballSession.StartPad> pads(int count, double x, float yaw) {
        List<SnowballSession.StartPad> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new SnowballSession.StartPad(x, 6, -10 + (i * 3), yaw));
        }
        return out;
    }
}
