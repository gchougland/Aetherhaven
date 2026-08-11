package com.hexvane.aetherhaven.festival.carnival;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-town whack-a-goblin game. Kept outside the entity Store so dialogue and tick systems can share it safely.
 */
public final class CarnivalWhackSession {
    public enum Phase {
        IDLE,
        PLAYING,
        RESULTS
    }

    private Phase phase = Phase.IDLE;
    @Nullable
    private UUID playerUuid;
    private int hits;
    private int spawned;
    private int resolved;
    private float elapsedSeconds;
    private float spawnCooldown;
    private float hitLockSeconds;
    private int lastSpawnHoleIndex = -1;
    private final Set<Integer> occupiedHoles = new HashSet<>();
    private final List<UUID> activeGoblinUuids = new ArrayList<>();
    private int pendingTickets = -1;
    private boolean pendingPerfectClear;
    private boolean finishSfxPending;

    @Nonnull
    public Phase getPhase() {
        return phase;
    }

    public boolean consumeFinishSfxPending() {
        if (!finishSfxPending) {
            return false;
        }
        finishSfxPending = false;
        return true;
    }

    @Nullable
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getHits() {
        return hits;
    }

    public int getSpawned() {
        return spawned;
    }

    public int getResolved() {
        return resolved;
    }

    public float getElapsedSeconds() {
        return elapsedSeconds;
    }

    public int getLastSpawnHoleIndex() {
        return lastSpawnHoleIndex;
    }

    public void setLastSpawnHoleIndex(int lastSpawnHoleIndex) {
        this.lastSpawnHoleIndex = lastSpawnHoleIndex;
    }

    public boolean isHoleOccupied(int holeIndex) {
        return occupiedHoles.contains(holeIndex);
    }

    public void occupyHole(int holeIndex) {
        occupiedHoles.add(holeIndex);
    }

    public void freeHole(int holeIndex) {
        occupiedHoles.remove(holeIndex);
    }

    public int activeCount() {
        return activeGoblinUuids.size();
    }

    @Nonnull
    public List<UUID> activeGoblinUuidsView() {
        return List.copyOf(activeGoblinUuids);
    }

    public void addActiveGoblin(@Nonnull UUID uuid) {
        activeGoblinUuids.add(uuid);
    }

    public void removeActiveGoblin(@Nonnull UUID uuid) {
        activeGoblinUuids.remove(uuid);
    }

    public boolean isBusy() {
        return phase == Phase.PLAYING || phase == Phase.RESULTS;
    }

    public boolean canStart(@Nonnull UUID player) {
        return phase == Phase.IDLE;
    }

    public boolean isPlaying(@Nonnull UUID player) {
        return phase == Phase.PLAYING && player.equals(playerUuid);
    }

    public boolean hasResult(@Nonnull UUID player) {
        return phase == Phase.RESULTS && player.equals(playerUuid) && pendingTickets >= 0;
    }

    public boolean isBusyForOther(@Nonnull UUID player) {
        return isBusy() && (playerUuid == null || !playerUuid.equals(player));
    }

    public boolean tryBegin(@Nonnull UUID player) {
        if (!canStart(player)) {
            return false;
        }
        phase = Phase.PLAYING;
        playerUuid = player;
        hits = 0;
        spawned = 0;
        resolved = 0;
        elapsedSeconds = 0f;
        hitLockSeconds = 0f;
        // Brief beat so the player can leave dialogue and get ready before the first pop.
        spawnCooldown = CarnivalIds.WHACK_START_DELAY_SECONDS;
        lastSpawnHoleIndex = -1;
        occupiedHoles.clear();
        activeGoblinUuids.clear();
        pendingTickets = -1;
        pendingPerfectClear = false;
        finishSfxPending = false;
        return true;
    }

    /** Advances play timer; returns true when the round should end due to duration. */
    public synchronized boolean tickPlaying(float dt) {
        if (phase != Phase.PLAYING) {
            return false;
        }
        spawnCooldown = Math.max(0f, spawnCooldown - dt);
        hitLockSeconds = Math.max(0f, hitLockSeconds - dt);
        // Opening delay does not burn the round clock.
        if (spawned == 0 && spawnCooldown > 0f) {
            return false;
        }
        elapsedSeconds += dt;
        return elapsedSeconds >= CarnivalIds.WHACK_DURATION_SECONDS;
    }

    /**
     * Claims the single scored hit slot for this swing. Returns false when another goblin was already scored during
     * the current hit-lock window.
     */
    public synchronized boolean tryClaimHit() {
        if (phase != Phase.PLAYING || hitLockSeconds > 0f) {
            return false;
        }
        hitLockSeconds = CarnivalIds.WHACK_HIT_LOCK_SECONDS;
        return true;
    }

    public synchronized boolean canReserveSpawn() {
        return phase == Phase.PLAYING
            && spawned < CarnivalIds.WHACK_TOTAL_POPS
            && activeGoblinUuids.size() < CarnivalIds.WHACK_MAX_UP
            && spawnCooldown <= 0f
            && elapsedSeconds < CarnivalIds.WHACK_DURATION_SECONDS;
    }

    public synchronized boolean tryReserveSpawn() {
        if (!canReserveSpawn()) {
            return false;
        }
        spawned++;
        spawnCooldown = CarnivalIds.WHACK_SPAWN_INTERVAL;
        return true;
    }

    public synchronized void cancelReservedSpawn() {
        if (spawned > 0) {
            spawned--;
        }
    }

    public void markHit(@Nonnull UUID goblinUuid) {
        if (phase != Phase.PLAYING) {
            return;
        }
        if (activeGoblinUuids.remove(goblinUuid)) {
            hits++;
            resolved++;
            maybeFinishAllPops();
        }
    }

    public void markMissed(@Nonnull UUID goblinUuid) {
        if (phase != Phase.PLAYING) {
            return;
        }
        if (activeGoblinUuids.remove(goblinUuid)) {
            resolved++;
            maybeFinishAllPops();
        }
    }

    private void maybeFinishAllPops() {
        if (spawned >= CarnivalIds.WHACK_TOTAL_POPS && resolved >= spawned && activeGoblinUuids.isEmpty()) {
            enterResults();
        }
    }

    public synchronized void forceFinish() {
        if (phase != Phase.PLAYING) {
            return;
        }
        activeGoblinUuids.clear();
        occupiedHoles.clear();
        resolved = Math.max(resolved, spawned);
        enterResults();
    }

    private void enterResults() {
        phase = Phase.RESULTS;
        pendingTickets = CarnivalIds.whackTicketReward(hits, Math.max(hits, spawned));
        // Hit every goblin that appeared for a full round (all pops resolved as hits).
        pendingPerfectClear =
            spawned >= CarnivalIds.WHACK_TOTAL_POPS && hits >= spawned && hits > 0;
        finishSfxPending = true;
    }

    public boolean isPendingPerfectClear(@Nonnull UUID player) {
        return hasResult(player) && pendingPerfectClear;
    }

    public int collectResult(@Nonnull UUID player) {
        if (!hasResult(player)) {
            return -1;
        }
        int tickets = Math.max(0, pendingTickets);
        clearAll();
        return tickets;
    }

    public void clearAll() {
        phase = Phase.IDLE;
        playerUuid = null;
        hits = 0;
        spawned = 0;
        resolved = 0;
        elapsedSeconds = 0f;
        spawnCooldown = 0f;
        hitLockSeconds = 0f;
        lastSpawnHoleIndex = -1;
        occupiedHoles.clear();
        activeGoblinUuids.clear();
        pendingTickets = -1;
        pendingPerfectClear = false;
        finishSfxPending = false;
    }
}
