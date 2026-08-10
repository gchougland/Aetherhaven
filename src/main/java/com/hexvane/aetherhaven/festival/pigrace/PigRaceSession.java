package com.hexvane.aetherhaven.festival.pigrace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-town pig race lobby and results. Kept outside the entity Store so dialogue and tick systems can share it safely.
 */
public final class PigRaceSession {
    public enum Phase {
        LOBBY,
        RACING,
        RESULTS
    }

    public record Bet(int laneIndex, int amount) {}

    public record Racer(
        int laneIndex,
        @Nonnull UUID entityUuid,
        double speedMultiplier,
        double startX,
        double startY,
        double startZ,
        double finishX,
        double finishY,
        double finishZ
    ) {
        public double trackLength() {
            double dx = finishX - startX;
            double dy = finishY - startY;
            double dz = finishZ - startZ;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        @Nonnull
        public Racer withSpeed(double speedMultiplier) {
            return new Racer(
                laneIndex,
                entityUuid,
                speedMultiplier,
                startX,
                startY,
                startZ,
                finishX,
                finishY,
                finishZ
            );
        }
    }

    private Phase phase = Phase.LOBBY;
    private final Map<UUID, Bet> bets = new LinkedHashMap<>();
    private final Map<UUID, Integer> pendingTickets = new LinkedHashMap<>();
    private final Set<UUID> pendingLosses = new HashSet<>();
    private final Map<UUID, Integer> pendingStakes = new LinkedHashMap<>();
    private final List<Racer> racers = new ArrayList<>();
    private int winningLane = -1;
    /** Wall clock when RESULTS may return to LOBBY; 0 while waiting for stragglers to finish. */
    private long resultsUntilEpochMs;
    private long resultsHoldMs;
    private final Set<Integer> finishedLanes = new HashSet<>();
    private boolean needsReturnToStart;
    /** True once after {@link #beginRacing()} until suspense music is applied. */
    private boolean raceStartMusicPending;
    /** True once after the start delay until whistle / hooves fire. */
    private boolean raceGoCuePending;
    /** Wall clock when pigs may leave the start line. */
    private long raceGoAtEpochMs;
    /** True once the finish-line top-down camera has been applied this race. */
    private boolean finishCameraApplied;
    @Nullable
    private UUID hoovesEmitterUuid;
    private final Set<UUID> raceMusicListeners = new HashSet<>();
    private final Set<UUID> raceCameraViewers = new HashSet<>();

    @Nonnull
    public Phase getPhase() {
        return phase;
    }

    public boolean canPlaceBet(@Nonnull UUID playerUuid) {
        return phase == Phase.LOBBY && !bets.containsKey(playerUuid) && !racers.isEmpty();
    }

    public boolean canStartRace() {
        return phase == Phase.LOBBY && !bets.isEmpty() && !racers.isEmpty();
    }

    public boolean hasWinnings(@Nonnull UUID playerUuid) {
        return pendingTickets.getOrDefault(playerUuid, 0) > 0;
    }

    public boolean hasPendingLoss(@Nonnull UUID playerUuid) {
        return pendingLosses.contains(playerUuid);
    }

    public boolean acknowledgeLoss(@Nonnull UUID playerUuid) {
        return pendingLosses.remove(playerUuid);
    }

    public void setPendingStake(@Nonnull UUID playerUuid, int amount) {
        if (!PigRaceLanes.isAllowedBet(amount)) {
            return;
        }
        pendingStakes.put(playerUuid, amount);
    }

    public int takePendingStake(@Nonnull UUID playerUuid) {
        Integer amount = pendingStakes.remove(playerUuid);
        return amount != null ? amount : 0;
    }

    /** Records a bet after gold has already been taken. */
    public boolean placeBet(@Nonnull UUID playerUuid, int laneIndex, int amount) {
        if (!canPlaceBet(playerUuid) || !PigRaceLanes.isAllowedBet(amount)) {
            return false;
        }
        int laneCount = racersView().isEmpty() ? PigRaceLanes.defaultLanes().size() : racersView().size();
        if (laneIndex < 0 || laneIndex >= laneCount) {
            return false;
        }
        bets.put(playerUuid, new Bet(laneIndex, amount));
        pendingStakes.remove(playerUuid);
        needsReturnToStart = true;
        return true;
    }

    public boolean consumeNeedsReturnToStart() {
        if (!needsReturnToStart) {
            return false;
        }
        needsReturnToStart = false;
        return true;
    }

    @Nonnull
    public Map<UUID, Bet> betsView() {
        return Map.copyOf(bets);
    }

    /** Stores the pigs that stay for the whole festival. */
    public void setRacers(@Nonnull List<Racer> spawnedRacers) {
        racers.clear();
        racers.addAll(spawnedRacers);
    }

    /** Starts a race with the pigs already present; rolls new speed multipliers. */
    public boolean beginRacing() {
        if (!canStartRace()) {
            return false;
        }
        List<Racer> rolled = new ArrayList<>(racers.size());
        for (Racer racer : racers) {
            rolled.add(racer.withSpeed(rollSpeedMultiplier()));
        }
        racers.clear();
        racers.addAll(rolled);
        winningLane = -1;
        needsReturnToStart = false;
        raceStartMusicPending = true;
        raceGoCuePending = true;
        raceGoAtEpochMs = System.currentTimeMillis() + PigRaceLanes.RACE_START_DELAY_MS;
        finishCameraApplied = false;
        finishedLanes.clear();
        resultsUntilEpochMs = 0L;
        resultsHoldMs = 0L;
        phase = Phase.RACING;
        return true;
    }

    /** True once when suspense music should start (pigs still hold at the line). */
    public boolean consumeRaceStartMusic() {
        if (!raceStartMusicPending) {
            return false;
        }
        raceStartMusicPending = false;
        return true;
    }

    /** True while the start delay is still counting down. */
    public boolean isWaitingToGo(long nowEpochMs) {
        return phase == Phase.RACING && nowEpochMs < raceGoAtEpochMs;
    }

    /** True once when the delay ends and whistle / hooves / movement should begin. */
    public boolean consumeRaceGoCue(long nowEpochMs) {
        if (!raceGoCuePending || nowEpochMs < raceGoAtEpochMs) {
            return false;
        }
        raceGoCuePending = false;
        return true;
    }

    /**
     * True once when a pig is near the finish and bettors should switch to the top-down finish camera.
     *
     * @param progressAlongTrack distance traveled from the start
     * @param trackLength full lane length
     */
    public boolean consumeFinishCamera(double progressAlongTrack, double trackLength) {
        if (finishCameraApplied || phase != Phase.RACING || trackLength < 1.0e-3) {
            return false;
        }
        double remainingFraction = 1.0 - (progressAlongTrack / trackLength);
        if (remainingFraction > PigRaceLanes.FINISH_CAMERA_REMAINING_FRACTION) {
            return false;
        }
        finishCameraApplied = true;
        return true;
    }

    @Nonnull
    public Set<UUID> raceCameraViewersView() {
        return Set.copyOf(raceCameraViewers);
    }

    public void setHoovesEmitterUuid(@Nullable UUID emitterUuid) {
        hoovesEmitterUuid = emitterUuid;
    }

    @Nullable
    public UUID getHoovesEmitterUuid() {
        return hoovesEmitterUuid;
    }

    @Nullable
    public UUID takeHoovesEmitterUuid() {
        UUID id = hoovesEmitterUuid;
        hoovesEmitterUuid = null;
        return id;
    }

    public void markRaceMusicListener(@Nonnull UUID playerUuid) {
        raceMusicListeners.add(playerUuid);
    }

    public void clearRaceMusicListener(@Nonnull UUID playerUuid) {
        raceMusicListeners.remove(playerUuid);
    }

    @Nonnull
    public Set<UUID> raceMusicListenersView() {
        return Set.copyOf(raceMusicListeners);
    }

    public void clearRaceMusicListeners() {
        raceMusicListeners.clear();
    }

    public void markRaceCameraViewer(@Nonnull UUID playerUuid) {
        raceCameraViewers.add(playerUuid);
    }

    @Nonnull
    public Set<UUID> takeRaceCameraViewers() {
        Set<UUID> viewers = Set.copyOf(raceCameraViewers);
        raceCameraViewers.clear();
        return viewers;
    }

    public void clearRaceCameraViewers() {
        raceCameraViewers.clear();
    }

    /** Test helper that injects racers then starts. */
    public boolean beginRacing(@Nonnull List<Racer> spawnedRacers) {
        if (phase != Phase.LOBBY || bets.isEmpty() || spawnedRacers.isEmpty()) {
            return false;
        }
        setRacers(spawnedRacers);
        return beginRacing();
    }

    @Nonnull
    public List<Racer> racersView() {
        return List.copyOf(racers);
    }

    @Nullable
    public Racer racerForEntity(@Nonnull UUID entityUuid) {
        for (Racer r : racers) {
            if (r.entityUuid().equals(entityUuid)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Marks the winning lane, queues ticket payouts and losses, and enters RESULTS. Other pigs keep running until
     * they reach the finish; the lobby hold starts only once every racer has finished.
     *
     * @return true if this call settled a live race
     */
    public boolean finishRace(int laneIndex, long nowEpochMs, long resultsHoldMs) {
        if (phase != Phase.RACING) {
            return false;
        }
        winningLane = laneIndex;
        for (Map.Entry<UUID, Bet> entry : bets.entrySet()) {
            Bet bet = entry.getValue();
            if (bet.laneIndex() == laneIndex) {
                int tickets = PigRaceLanes.ticketPayout(bet.amount());
                if (tickets > 0) {
                    pendingTickets.merge(entry.getKey(), tickets, Integer::sum);
                }
            } else {
                pendingLosses.add(entry.getKey());
            }
        }
        bets.clear();
        // Keep racers for the rest of the festival; they return to the start after the hold.
        phase = Phase.RESULTS;
        this.resultsHoldMs = Math.max(0L, resultsHoldMs);
        resultsUntilEpochMs = 0L;
        finishedLanes.clear();
        finishedLanes.add(laneIndex);
        maybeStartResultsHold(nowEpochMs);
        raceStartMusicPending = false;
        raceGoCuePending = false;
        raceGoAtEpochMs = 0L;
        return true;
    }

    /** Records that a lane has reached the finish during RESULTS; starts the hold when every pig is done. */
    public void markLaneFinished(int laneIndex, long nowEpochMs) {
        if (phase != Phase.RESULTS || laneIndex < 0) {
            return;
        }
        finishedLanes.add(laneIndex);
        maybeStartResultsHold(nowEpochMs);
    }

    private void maybeStartResultsHold(long nowEpochMs) {
        if (resultsUntilEpochMs > 0L) {
            return;
        }
        if (!racers.isEmpty()) {
            for (Racer racer : racers) {
                if (!finishedLanes.contains(racer.laneIndex())) {
                    return;
                }
            }
        }
        resultsUntilEpochMs = nowEpochMs + resultsHoldMs;
    }

    public int getWinningLane() {
        return winningLane;
    }

    /** Moves RESULTS back to LOBBY once every pig has finished and the hold expires. */
    public boolean tryReturnToLobby(long nowEpochMs) {
        if (phase != Phase.RESULTS) {
            return false;
        }
        if (resultsUntilEpochMs <= 0L || nowEpochMs < resultsUntilEpochMs) {
            return false;
        }
        phase = Phase.LOBBY;
        needsReturnToStart = true;
        finishedLanes.clear();
        resultsUntilEpochMs = 0L;
        resultsHoldMs = 0L;
        return true;
    }

    public int collectWinnings(@Nonnull UUID playerUuid) {
        Integer tickets = pendingTickets.remove(playerUuid);
        return tickets != null ? tickets : 0;
    }

    public void clearRaceEntities() {
        racers.clear();
        if (phase == Phase.RACING) {
            phase = Phase.LOBBY;
            bets.clear();
            winningLane = -1;
        } else if (phase == Phase.RESULTS) {
            phase = Phase.LOBBY;
        }
    }

    public void clearAll() {
        phase = Phase.LOBBY;
        bets.clear();
        pendingTickets.clear();
        pendingLosses.clear();
        pendingStakes.clear();
        racers.clear();
        winningLane = -1;
        resultsUntilEpochMs = 0L;
        resultsHoldMs = 0L;
        finishedLanes.clear();
        needsReturnToStart = false;
        raceStartMusicPending = false;
        raceGoCuePending = false;
        raceGoAtEpochMs = 0L;
        finishCameraApplied = false;
        hoovesEmitterUuid = null;
        raceMusicListeners.clear();
        raceCameraViewers.clear();
    }

    public static double rollSpeedMultiplier() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return PigRaceLanes.SPEED_MULT_MIN
            + rng.nextDouble() * (PigRaceLanes.SPEED_MULT_MAX - PigRaceLanes.SPEED_MULT_MIN);
    }
}
