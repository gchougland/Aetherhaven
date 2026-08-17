package com.hexvane.aetherhaven.festival.snowball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-town snowball lobby and fight. Kept outside the entity Store so dialogue and tick systems can share it safely.
 * Only one fight runs at a time per town.
 */
public final class SnowballSession {
    public enum Phase {
        LOBBY,
        FIGHTING,
        RESULTS
    }

    public record StartPad(double x, double y, double z, float yawDegrees) {}

    public record PileSpot(int worldX, int worldY, int worldZ) {}

    public static final class Fighter {
        private final UUID uuid;
        private final SnowballIds.Team team;
        private final StartPad pad;
        private final boolean player;
        private int lives = SnowballIds.LIVES;
        private boolean out;

        Fighter(@Nonnull UUID uuid, @Nonnull SnowballIds.Team team, @Nonnull StartPad pad, boolean player) {
            this.uuid = uuid;
            this.team = team;
            this.pad = pad;
            this.player = player;
        }

        @Nonnull
        public UUID uuid() {
            return uuid;
        }

        @Nonnull
        public SnowballIds.Team team() {
            return team;
        }

        @Nonnull
        public StartPad pad() {
            return pad;
        }

        public boolean isPlayer() {
            return player;
        }

        public int lives() {
            return lives;
        }

        public boolean isOut() {
            return out;
        }
    }

    public enum VillagerAiPhase {
        CROUCH,
        STAND_BEFORE,
        THROW,
        STAND_AFTER
    }

    public static final class VillagerAi {
        private VillagerAiPhase phase = VillagerAiPhase.CROUCH;
        private long nextEpochMs;
        @Nullable
        private Boolean crouching;
        private boolean prepared;
        @Nullable
        private UUID throwTargetUuid;

        @Nonnull
        public VillagerAiPhase phase() {
            return phase;
        }

        public long nextEpochMs() {
            return nextEpochMs;
        }

        public void set(@Nonnull VillagerAiPhase phase, long nextEpochMs) {
            this.phase = phase;
            this.nextEpochMs = nextEpochMs;
        }

        public boolean consumePrepare() {
            if (prepared) {
                return false;
            }
            prepared = true;
            return true;
        }

        public boolean crouchChanged(boolean crouching) {
            if (this.crouching != null && this.crouching == crouching) {
                return false;
            }
            this.crouching = crouching;
            return true;
        }

        @Nullable
        public UUID throwTargetUuid() {
            return throwTargetUuid;
        }

        public void setThrowTargetUuid(@Nullable UUID throwTargetUuid) {
            this.throwTargetUuid = throwTargetUuid;
        }
    }

    private Phase phase = Phase.LOBBY;
    private final List<UUID> joinedPlayers = new ArrayList<>();
    private final Map<UUID, Fighter> fighters = new LinkedHashMap<>();
    private final Map<UUID, Integer> pendingTickets = new LinkedHashMap<>();
    private final Map<UUID, Integer> lastCollectedTickets = new LinkedHashMap<>();
    private final Set<UUID> pendingOutTeleport = new LinkedHashSet<>();
    private final Map<UUID, VillagerAi> villagerAi = new LinkedHashMap<>();
    private final Map<UUID, Integer> playerHits = new LinkedHashMap<>();
    private final Set<Integer> spentHitProjectiles = new HashSet<>();
    private final List<PileSpot> pileSpots = new ArrayList<>();
    private final Map<PileSpot, Long> pileRespawnAtMs = new LinkedHashMap<>();
    private final Set<PileSpot> pilesPresent = new LinkedHashSet<>();
    private final List<StartPad> teamAPads = new ArrayList<>();
    private final List<StartPad> teamBPads = new ArrayList<>();
    private StartPad outPad = new StartPad(0, 0, 0, 0);
    private long fightStartEpochMs;
    private long fightEndEpochMs;
    private long resultsUntilEpochMs;
    private boolean pendingStartTeleport;
    private boolean pendingFill;
    private boolean pendingWinAnnounce;
    @Nullable
    private SnowballIds.Team winningTeam;
    private final Set<UUID> fightMusicListeners = new HashSet<>();

    @Nonnull
    public Phase getPhase() {
        return phase;
    }

    public void clearAll() {
        phase = Phase.LOBBY;
        joinedPlayers.clear();
        fighters.clear();
        pendingTickets.clear();
        lastCollectedTickets.clear();
        pendingOutTeleport.clear();
        villagerAi.clear();
        playerHits.clear();
        spentHitProjectiles.clear();
        pileSpots.clear();
        pileRespawnAtMs.clear();
        pilesPresent.clear();
        teamAPads.clear();
        teamBPads.clear();
        outPad = new StartPad(0, 0, 0, 0);
        fightStartEpochMs = 0L;
        fightEndEpochMs = 0L;
        resultsUntilEpochMs = 0L;
        pendingStartTeleport = false;
        pendingFill = false;
        pendingWinAnnounce = false;
        winningTeam = null;
        fightMusicListeners.clear();
    }

    public void setCourse(
        @Nonnull List<StartPad> teamA,
        @Nonnull List<StartPad> teamB,
        @Nonnull StartPad out,
        @Nonnull List<PileSpot> piles
    ) {
        teamAPads.clear();
        teamAPads.addAll(teamA);
        teamBPads.clear();
        teamBPads.addAll(teamB);
        outPad = out;
        pileSpots.clear();
        pileSpots.addAll(piles);
        pileRespawnAtMs.clear();
        pilesPresent.clear();
        pilesPresent.addAll(piles);
    }

    @Nonnull
    public List<StartPad> teamAPadsView() {
        return List.copyOf(teamAPads);
    }

    @Nonnull
    public List<StartPad> teamBPadsView() {
        return List.copyOf(teamBPads);
    }

    @Nonnull
    public StartPad outPad() {
        return outPad;
    }

    @Nonnull
    public List<PileSpot> pileSpotsView() {
        return List.copyOf(pileSpots);
    }

    public boolean isPilePresent(@Nonnull PileSpot spot) {
        return pilesPresent.contains(spot);
    }

    public void markPileCleared(@Nonnull PileSpot spot, long respawnAtEpochMs) {
        pilesPresent.remove(spot);
        pileRespawnAtMs.put(spot, respawnAtEpochMs);
    }

    public void markPilePresent(@Nonnull PileSpot spot) {
        pilesPresent.add(spot);
        pileRespawnAtMs.remove(spot);
    }

    @Nullable
    public PileSpot pileAt(int worldX, int worldY, int worldZ) {
        for (PileSpot spot : pileSpots) {
            if (spot.worldX() == worldX && spot.worldY() == worldY && spot.worldZ() == worldZ) {
                return spot;
            }
        }
        return null;
    }

    @Nonnull
    public List<PileSpot> duePileRespawns(long nowEpochMs) {
        List<PileSpot> due = new ArrayList<>();
        for (Map.Entry<PileSpot, Long> entry : pileRespawnAtMs.entrySet()) {
            if (entry.getValue() != null && nowEpochMs >= entry.getValue()) {
                due.add(entry.getKey());
            }
        }
        return due;
    }

    public boolean canJoin(@Nonnull UUID playerUuid) {
        return phase == Phase.LOBBY
            && !joinedPlayers.contains(playerUuid)
            && joinedPlayers.size() < SnowballIds.MAX_PLAYERS
            && !teamAPads.isEmpty()
            && !teamBPads.isEmpty();
    }

    public boolean isJoined(@Nonnull UUID playerUuid) {
        return joinedPlayers.contains(playerUuid);
    }

    public boolean join(@Nonnull UUID playerUuid) {
        if (!canJoin(playerUuid)) {
            return false;
        }
        joinedPlayers.add(playerUuid);
        return true;
    }

    public boolean leave(@Nonnull UUID playerUuid) {
        return phase == Phase.LOBBY && joinedPlayers.remove(playerUuid);
    }

    public boolean canStartFight() {
        return phase == Phase.LOBBY && !joinedPlayers.isEmpty() && !teamAPads.isEmpty() && !teamBPads.isEmpty();
    }

    public boolean isFightBusy() {
        return phase == Phase.FIGHTING || phase == Phase.RESULTS;
    }

    @Nonnull
    public List<UUID> joinedPlayersView() {
        return List.copyOf(joinedPlayers);
    }

    public int joinedPlayerCount() {
        return joinedPlayers.size();
    }

    /**
     * Marks the fight as starting. Fighters are filled and teleported on the next fight-system tick.
     */
    public boolean beginFighting(long nowEpochMs) {
        if (!canStartFight()) {
            return false;
        }
        fighters.clear();
        villagerAi.clear();
        playerHits.clear();
        spentHitProjectiles.clear();
        pendingOutTeleport.clear();
        fightStartEpochMs = nowEpochMs;
        fightEndEpochMs = nowEpochMs + SnowballIds.FIGHT_DURATION_MS;
        resultsUntilEpochMs = 0L;
        pendingStartTeleport = true;
        pendingFill = true;
        pendingWinAnnounce = false;
        winningTeam = null;
        phase = Phase.FIGHTING;
        return true;
    }

    /**
     * Assigns players first with even team splits, then fills leftover pads with villagers.
     * Players are stronger than villagers, so 2 players are always split 1 and 1, 3 become 2 and 1, 4 become 2 and 2.
     *
     * @return true when at least two fighters were assigned
     */
    public boolean fillAndAssign(
        @Nonnull List<UUID> shuffledPlayers,
        @Nonnull List<UUID> fillerVillagers,
        long nowEpochMs
    ) {
        pendingFill = false;
        fighters.clear();
        villagerAi.clear();
        playerHits.clear();
        spentHitProjectiles.clear();

        List<UUID> players = new ArrayList<>();
        for (UUID player : shuffledPlayers) {
            if (player != null && !players.contains(player)) {
                players.add(player);
            }
        }
        List<UUID> villagers = new ArrayList<>();
        for (UUID villager : fillerVillagers) {
            if (villager != null && !players.contains(villager) && !villagers.contains(villager)) {
                villagers.add(villager);
            }
        }

        int padCap = teamAPads.size() + teamBPads.size();
        if (players.size() > padCap) {
            players = new ArrayList<>(players.subList(0, padCap));
        }
        int villagerSlots = Math.max(0, padCap - players.size());
        if (villagers.size() > villagerSlots) {
            villagers = new ArrayList<>(villagers.subList(0, villagerSlots));
        }
        if (players.size() + villagers.size() < 2) {
            abortToLobby();
            return false;
        }

        int aIdx = 0;
        int bIdx = 0;
        // Randomize which team gets the first player so an odd count does not always favor team A.
        boolean nextPlayerToA = (players.isEmpty() ? 0 : players.get(0).getLeastSignificantBits()) >= 0L;
        for (UUID player : players) {
            boolean preferA = nextPlayerToA;
            nextPlayerToA = !nextPlayerToA;
            AssignedPad seat = takePad(preferA, aIdx, bIdx);
            if (seat == null) {
                break;
            }
            if (seat.team == SnowballIds.Team.A) {
                aIdx = seat.nextIndex;
            } else {
                bIdx = seat.nextIndex;
            }
            fighters.put(player, new Fighter(player, seat.team, seat.pad, true));
            playerHits.put(player, 0);
        }

        for (UUID villager : villagers) {
            boolean preferA = aIdx <= bIdx;
            if (aIdx >= teamAPads.size()) {
                preferA = false;
            } else if (bIdx >= teamBPads.size()) {
                preferA = true;
            }
            AssignedPad seat = takePad(preferA, aIdx, bIdx);
            if (seat == null) {
                break;
            }
            if (seat.team == SnowballIds.Team.A) {
                aIdx = seat.nextIndex;
            } else {
                bIdx = seat.nextIndex;
            }
            fighters.put(villager, new Fighter(villager, seat.team, seat.pad, false));
            VillagerAi ai = new VillagerAi();
            ai.set(VillagerAiPhase.CROUCH, nowEpochMs + crouchHoldMs(villager));
            villagerAi.put(villager, ai);
        }
        return fighters.size() >= 2;
    }

    @Nullable
    private AssignedPad takePad(boolean preferA, int aIdx, int bIdx) {
        if (preferA && aIdx < teamAPads.size()) {
            return new AssignedPad(SnowballIds.Team.A, teamAPads.get(aIdx), aIdx + 1);
        }
        if (!preferA && bIdx < teamBPads.size()) {
            return new AssignedPad(SnowballIds.Team.B, teamBPads.get(bIdx), bIdx + 1);
        }
        if (aIdx < teamAPads.size()) {
            return new AssignedPad(SnowballIds.Team.A, teamAPads.get(aIdx), aIdx + 1);
        }
        if (bIdx < teamBPads.size()) {
            return new AssignedPad(SnowballIds.Team.B, teamBPads.get(bIdx), bIdx + 1);
        }
        return null;
    }

    private record AssignedPad(@Nonnull SnowballIds.Team team, @Nonnull StartPad pad, int nextIndex) {}

    private static long crouchHoldMs(@Nonnull UUID uuid) {
        long span = SnowballIds.VILLAGER_CROUCH_MAX_MS - SnowballIds.VILLAGER_CROUCH_MIN_MS;
        long mix = Math.floorMod(uuid.getLeastSignificantBits(), Math.max(1L, span + 1L));
        return SnowballIds.VILLAGER_CROUCH_MIN_MS + mix;
    }

    private void abortToLobby() {
        phase = Phase.LOBBY;
        fighters.clear();
        villagerAi.clear();
        playerHits.clear();
        spentHitProjectiles.clear();
        pendingStartTeleport = false;
        pendingFill = false;
        fightStartEpochMs = 0L;
        fightEndEpochMs = 0L;
    }

    public boolean consumePendingFill() {
        if (!pendingFill) {
            return false;
        }
        pendingFill = false;
        return true;
    }

    public boolean consumePendingStartTeleport() {
        if (!pendingStartTeleport) {
            return false;
        }
        pendingStartTeleport = false;
        return true;
    }

    public boolean consumePendingWinAnnounce() {
        if (!pendingWinAnnounce) {
            return false;
        }
        pendingWinAnnounce = false;
        return true;
    }

    @Nonnull
    public Set<UUID> takePendingOutTeleport() {
        if (pendingOutTeleport.isEmpty()) {
            return Set.of();
        }
        Set<UUID> out = Set.copyOf(pendingOutTeleport);
        pendingOutTeleport.clear();
        return out;
    }

    @Nullable
    public Fighter fighter(@Nonnull UUID uuid) {
        return fighters.get(uuid);
    }

    @Nonnull
    public Set<UUID> hudPlayerUuids() {
        Set<UUID> out = new LinkedHashSet<>(joinedPlayers);
        for (Fighter fighter : fighters.values()) {
            if (fighter.player) {
                out.add(fighter.uuid);
            }
        }
        return out;
    }

    public boolean isFighter(@Nonnull UUID uuid) {
        return fighters.containsKey(uuid);
    }

    public boolean isLivingFighter(@Nonnull UUID uuid) {
        Fighter fighter = fighters.get(uuid);
        return fighter != null && !fighter.out && phase == Phase.FIGHTING;
    }

    public boolean isOutFighter(@Nonnull UUID uuid) {
        Fighter fighter = fighters.get(uuid);
        return fighter != null && fighter.out && phase == Phase.FIGHTING;
    }

    public boolean isVillagerFighter(@Nonnull UUID uuid) {
        Fighter fighter = fighters.get(uuid);
        return fighter != null && !fighter.player;
    }

    @Nullable
    public VillagerAi villagerAi(@Nonnull UUID uuid) {
        return villagerAi.get(uuid);
    }

    @Nonnull
    public List<Fighter> fightersView() {
        return List.copyOf(fighters.values());
    }

    @Nonnull
    public List<UUID> livingOpponentUuids(@Nonnull UUID uuid) {
        Fighter self = fighters.get(uuid);
        if (self == null) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (Fighter fighter : fighters.values()) {
            if (!fighter.out && fighter.team != self.team) {
                out.add(fighter.uuid);
            }
        }
        return out;
    }

    /**
     * True when this throw would count: both people are still in the fight and on opposite teams.
     */
    public boolean wouldHit(@Nonnull UUID victimUuid, @Nonnull UUID attackerUuid) {
        if (phase != Phase.FIGHTING) {
            return false;
        }
        Fighter victim = fighters.get(victimUuid);
        Fighter attacker = fighters.get(attackerUuid);
        return victim != null
            && attacker != null
            && !victim.out
            && !attacker.out
            && victim.team != attacker.team
            && victim.lives > 0;
    }

    /**
     * Applies an opposing-team hit. Returns true when a life was removed.
     */
    public boolean tryHit(@Nonnull UUID victimUuid, @Nonnull UUID attackerUuid) {
        if (!wouldHit(victimUuid, attackerUuid)) {
            return false;
        }
        Fighter victim = fighters.get(victimUuid);
        Fighter attacker = fighters.get(attackerUuid);
        if (victim == null || attacker == null) {
            return false;
        }
        victim.lives -= 1;
        if (attacker.player) {
            playerHits.merge(attackerUuid, 1, Integer::sum);
        }
        if (victim.lives <= 0) {
            victim.out = true;
            pendingOutTeleport.add(victimUuid);
        }
        return true;
    }

    public int hitsFor(@Nonnull UUID playerUuid) {
        return playerHits.getOrDefault(playerUuid, 0);
    }

    /** Returns true the first time this projectile is counted as a hit this fight. */
    public boolean consumeHitProjectile(int token) {
        return spentHitProjectiles.add(token);
    }

    @Nonnull
    public Map<UUID, Integer> playerHitsView() {
        return Map.copyOf(playerHits);
    }

    public int remainingLives(@Nonnull SnowballIds.Team team) {
        int total = 0;
        for (Fighter fighter : fighters.values()) {
            if (fighter.team == team && !fighter.out) {
                total += fighter.lives;
            }
        }
        return total;
    }

    public int livingCount(@Nonnull SnowballIds.Team team) {
        int n = 0;
        for (Fighter fighter : fighters.values()) {
            if (fighter.team == team && !fighter.out) {
                n++;
            }
        }
        return n;
    }

    public long getFightStartEpochMs() {
        return fightStartEpochMs;
    }

    public long getFightEndEpochMs() {
        return fightEndEpochMs;
    }

    public float remainingBarFraction(long nowEpochMs) {
        if (phase != Phase.FIGHTING && phase != Phase.RESULTS) {
            return 0f;
        }
        long remaining = Math.max(0L, fightEndEpochMs - nowEpochMs);
        return Math.max(0f, Math.min(1f, remaining / (float) SnowballIds.FIGHT_DURATION_MS));
    }

    /**
     * Ends the fight when a team is wiped or time runs out. Tie awards both teams.
     */
    public boolean tryFinish(long nowEpochMs) {
        if (phase != Phase.FIGHTING) {
            return false;
        }
        boolean aAlive = livingCount(SnowballIds.Team.A) > 0;
        boolean bAlive = livingCount(SnowballIds.Team.B) > 0;
        boolean timedOut = nowEpochMs >= fightEndEpochMs;
        if (aAlive && bAlive && !timedOut) {
            return false;
        }
        int aLives = remainingLives(SnowballIds.Team.A);
        int bLives = remainingLives(SnowballIds.Team.B);
        if (!aAlive && bAlive) {
            winningTeam = SnowballIds.Team.B;
        } else if (!bAlive && aAlive) {
            winningTeam = SnowballIds.Team.A;
        } else if (aLives > bLives) {
            winningTeam = SnowballIds.Team.A;
        } else if (bLives > aLives) {
            winningTeam = SnowballIds.Team.B;
        } else {
            winningTeam = null;
        }
        awardTickets();
        pendingWinAnnounce = true;
        phase = Phase.RESULTS;
        resultsUntilEpochMs = nowEpochMs + SnowballIds.RESULTS_HOLD_MS;
        return true;
    }

    private void awardTickets() {
        for (Fighter fighter : fighters.values()) {
            if (!fighter.player) {
                continue;
            }
            boolean win = winningTeam == null || fighter.team == winningTeam;
            if (win) {
                pendingTickets.merge(fighter.uuid, SnowballIds.WIN_TICKETS, Integer::sum);
            }
        }
    }

    @Nullable
    public SnowballIds.Team winningTeam() {
        return winningTeam;
    }

    public boolean tryReturnToLobby(long nowEpochMs) {
        if (phase != Phase.RESULTS || nowEpochMs < resultsUntilEpochMs) {
            return false;
        }
        joinedPlayers.clear();
        fighters.clear();
        villagerAi.clear();
        playerHits.clear();
        spentHitProjectiles.clear();
        pendingOutTeleport.clear();
        fightStartEpochMs = 0L;
        fightEndEpochMs = 0L;
        resultsUntilEpochMs = 0L;
        pendingStartTeleport = false;
        pendingFill = false;
        pendingWinAnnounce = false;
        winningTeam = null;
        phase = Phase.LOBBY;
        return true;
    }

    public void markFightMusicListener(@Nonnull UUID playerUuid) {
        fightMusicListeners.add(playerUuid);
    }

    public void clearFightMusicListener(@Nonnull UUID playerUuid) {
        fightMusicListeners.remove(playerUuid);
    }

    @Nonnull
    public Set<UUID> fightMusicListenersView() {
        return Set.copyOf(fightMusicListeners);
    }

    public void clearFightMusicListeners() {
        fightMusicListeners.clear();
    }

    public boolean hasPendingTickets(@Nonnull UUID playerUuid) {
        return pendingTickets.getOrDefault(playerUuid, 0) > 0;
    }

    public int collectTickets(@Nonnull UUID playerUuid) {
        Integer tickets = pendingTickets.remove(playerUuid);
        int count = tickets != null ? tickets : 0;
        if (count > 0) {
            lastCollectedTickets.put(playerUuid, count);
        }
        return count;
    }

    public int peekLastCollectedTickets(@Nonnull UUID playerUuid) {
        return lastCollectedTickets.getOrDefault(playerUuid, 0);
    }

    @Nonnull
    public List<UUID> shuffledJoinedPlayers(@Nonnull java.util.Random random) {
        List<UUID> copy = new ArrayList<>(joinedPlayers);
        Collections.shuffle(copy, random);
        return copy;
    }
}
