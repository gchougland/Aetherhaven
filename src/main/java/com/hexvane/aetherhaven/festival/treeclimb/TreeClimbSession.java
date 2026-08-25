package com.hexvane.aetherhaven.festival.treeclimb;

import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-town tree climb lobby and race. Kept outside the entity Store so dialogue and tick systems can share it safely.
 * Only one race runs at a time per town.
 */
public final class TreeClimbSession {
    public enum Phase {
        LOBBY,
        RACING,
        RESULTS
    }

    private Phase phase = Phase.LOBBY;
    private final List<UUID> joined = new ArrayList<>();
    private final Map<UUID, Double> finishSeconds = new LinkedHashMap<>();
    private final Map<UUID, Integer> pendingTickets = new LinkedHashMap<>();
    private final Map<UUID, Integer> lastCollectedTickets = new LinkedHashMap<>();
    private final Map<UUID, SpendBreakdown> entryFees = new LinkedHashMap<>();
    private final Set<UUID> dnfPlayers = new LinkedHashSet<>();
    private final Set<UUID> finishSfxPending = new LinkedHashSet<>();
    private long raceStartEpochMs;
    private long resultsUntilEpochMs;
    private boolean startWhistlePending;
    private boolean pendingStartTeleport;
    private boolean pendingReturnTeleport;
    private final Map<UUID, StartPad> pendingReturnPads = new LinkedHashMap<>();
    private double finishWorldX;
    private double finishWorldY;
    private double finishWorldZ;
    private final List<StartPad> startPads = new ArrayList<>();
    private int maxRacers = TreeClimbIds.DEFAULT_MAX_RACERS;

    public record StartPad(double x, double y, double z, float yawDegrees) {}

    @Nonnull
    public Phase getPhase() {
        return phase;
    }

    public void clearAll() {
        phase = Phase.LOBBY;
        joined.clear();
        finishSeconds.clear();
        pendingTickets.clear();
        lastCollectedTickets.clear();
        entryFees.clear();
        dnfPlayers.clear();
        finishSfxPending.clear();
        raceStartEpochMs = 0L;
        resultsUntilEpochMs = 0L;
        startWhistlePending = false;
        pendingStartTeleport = false;
        pendingReturnTeleport = false;
        pendingReturnPads.clear();
        finishWorldX = 0;
        finishWorldY = 0;
        finishWorldZ = 0;
        startPads.clear();
        maxRacers = TreeClimbIds.DEFAULT_MAX_RACERS;
    }

    public void setCourse(
        @Nonnull List<StartPad> pads,
        double finishX,
        double finishY,
        double finishZ,
        int maxRacers
    ) {
        startPads.clear();
        startPads.addAll(pads);
        finishWorldX = finishX;
        finishWorldY = finishY;
        finishWorldZ = finishZ;
        this.maxRacers = Math.max(1, maxRacers);
    }

    public int getMaxRacers() {
        return maxRacers;
    }

    @Nonnull
    public List<StartPad> startPadsView() {
        return List.copyOf(startPads);
    }

    public double getFinishWorldX() {
        return finishWorldX;
    }

    public double getFinishWorldY() {
        return finishWorldY;
    }

    public double getFinishWorldZ() {
        return finishWorldZ;
    }

    public boolean canJoin(@Nonnull UUID playerUuid) {
        return phase == Phase.LOBBY
            && !joined.contains(playerUuid)
            && joined.size() < maxRacers
            && !startPads.isEmpty();
    }

    public boolean isJoined(@Nonnull UUID playerUuid) {
        return joined.contains(playerUuid);
    }

    public boolean join(@Nonnull UUID playerUuid, @Nonnull SpendBreakdown entryFee) {
        if (!canJoin(playerUuid)) {
            return false;
        }
        joined.add(playerUuid);
        entryFees.put(playerUuid, entryFee);
        return true;
    }

    /** Leaves the lobby. Returns the entry fee to refund, or null if leave was rejected. */
    @Nullable
    public SpendBreakdown leave(@Nonnull UUID playerUuid) {
        if (phase != Phase.LOBBY || !joined.remove(playerUuid)) {
            return null;
        }
        return entryFees.remove(playerUuid);
    }

    public boolean canStartRace() {
        return phase == Phase.LOBBY && !joined.isEmpty() && !startPads.isEmpty();
    }

    public boolean isRaceBusy() {
        return phase == Phase.RACING || phase == Phase.RESULTS;
    }

    @Nonnull
    public List<UUID> joinedView() {
        return List.copyOf(joined);
    }

    public int joinedCount() {
        return joined.size();
    }

    /** Starts the race for everyone currently joined. */
    public boolean beginRacing(long nowEpochMs) {
        if (!canStartRace()) {
            return false;
        }
        finishSeconds.clear();
        pendingTickets.clear();
        dnfPlayers.clear();
        finishSfxPending.clear();
        // Entry fees are spent once the race starts.
        entryFees.clear();
        raceStartEpochMs = nowEpochMs;
        resultsUntilEpochMs = 0L;
        startWhistlePending = true;
        pendingStartTeleport = true;
        pendingReturnTeleport = false;
        pendingReturnPads.clear();
        phase = Phase.RACING;
        return true;
    }

    public boolean consumeStartWhistle() {
        if (!startWhistlePending) {
            return false;
        }
        startWhistlePending = false;
        return true;
    }

    public boolean consumePendingStartTeleport() {
        if (!pendingStartTeleport) {
            return false;
        }
        pendingStartTeleport = false;
        return true;
    }

    public long getRaceStartEpochMs() {
        return raceStartEpochMs;
    }

    public double elapsedSeconds(long nowEpochMs) {
        if (phase != Phase.RACING && phase != Phase.RESULTS) {
            return 0.0;
        }
        return Math.max(0.0, (nowEpochMs - raceStartEpochMs) / 1000.0);
    }

    public boolean hasFinished(@Nonnull UUID playerUuid) {
        return finishSeconds.containsKey(playerUuid) || dnfPlayers.contains(playerUuid);
    }

    public boolean isDnf(@Nonnull UUID playerUuid) {
        return dnfPlayers.contains(playerUuid);
    }

    @Nullable
    public Double finishTimeSeconds(@Nonnull UUID playerUuid) {
        return finishSeconds.get(playerUuid);
    }

    /** Records a finish for a racer still racing. Returns the finish time seconds, or null if rejected. */
    @Nullable
    public Double markFinished(@Nonnull UUID playerUuid, long nowEpochMs) {
        if (phase != Phase.RACING || !joined.contains(playerUuid) || hasFinished(playerUuid)) {
            return null;
        }
        double seconds = Math.max(0.0, (nowEpochMs - raceStartEpochMs) / 1000.0);
        finishSeconds.put(playerUuid, seconds);
        int tickets = TreeClimbIds.ticketReward(seconds);
        if (tickets > 0) {
            pendingTickets.put(playerUuid, tickets);
        }
        finishSfxPending.add(playerUuid);
        tryEnterResults(nowEpochMs);
        return seconds;
    }

    public void markDnf(@Nonnull UUID playerUuid, long nowEpochMs) {
        if (phase != Phase.RACING || !joined.contains(playerUuid) || hasFinished(playerUuid)) {
            return;
        }
        dnfPlayers.add(playerUuid);
        tryEnterResults(nowEpochMs);
    }

    public boolean consumeFinishSfx(@Nonnull UUID playerUuid) {
        return finishSfxPending.remove(playerUuid);
    }

    private void tryEnterResults(long nowEpochMs) {
        if (phase != Phase.RACING) {
            return;
        }
        if (allRacersSettled()) {
            phase = Phase.RESULTS;
            resultsUntilEpochMs = nowEpochMs + TreeClimbIds.RESULTS_HOLD_MS;
        }
    }

    public boolean allRacersSettled() {
        for (UUID id : joined) {
            if (!hasFinished(id)) {
                return false;
            }
        }
        return !joined.isEmpty();
    }

    /** Returns true once when RESULTS hold ends and lobby is restored. */
    public boolean tryReturnToLobby(long nowEpochMs) {
        if (phase != Phase.RESULTS || nowEpochMs < resultsUntilEpochMs) {
            return false;
        }
        pendingReturnPads.clear();
        List<StartPad> pads = startPads;
        for (int i = 0; i < joined.size(); i++) {
            if (pads.isEmpty()) {
                break;
            }
            pendingReturnPads.put(joined.get(i), pads.get(Math.min(i, pads.size() - 1)));
        }
        pendingReturnTeleport = !pendingReturnPads.isEmpty();
        joined.clear();
        finishSeconds.clear();
        dnfPlayers.clear();
        finishSfxPending.clear();
        entryFees.clear();
        raceStartEpochMs = 0L;
        resultsUntilEpochMs = 0L;
        startWhistlePending = false;
        pendingStartTeleport = false;
        phase = Phase.LOBBY;
        return true;
    }

    public boolean consumePendingReturnTeleport() {
        if (!pendingReturnTeleport) {
            return false;
        }
        pendingReturnTeleport = false;
        return true;
    }

    @Nonnull
    public Map<UUID, StartPad> takePendingReturnPads() {
        Map<UUID, StartPad> out = Map.copyOf(pendingReturnPads);
        pendingReturnPads.clear();
        return out;
    }

    public boolean hasPendingTickets(@Nonnull UUID playerUuid) {
        return pendingTickets.getOrDefault(playerUuid, 0) > 0;
    }

    @Nonnull
    public Set<UUID> pendingTicketPlayerUuids() {
        return Set.copyOf(pendingTickets.keySet());
    }

    public int collectTickets(@Nonnull UUID playerUuid) {
        Integer tickets = pendingTickets.remove(playerUuid);
        int count = tickets != null ? tickets : 0;
        if (count > 0) {
            lastCollectedTickets.put(playerUuid, count);
        }
        return count;
    }

    public int peekPendingTickets(@Nonnull UUID playerUuid) {
        return pendingTickets.getOrDefault(playerUuid, 0);
    }

    public int peekLastCollectedTickets(@Nonnull UUID playerUuid) {
        return lastCollectedTickets.getOrDefault(playerUuid, 0);
    }
}
