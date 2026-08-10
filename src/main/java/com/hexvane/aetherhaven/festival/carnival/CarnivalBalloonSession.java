package com.hexvane.aetherhaven.festival.carnival;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-town balloon popping game. Kept outside the entity Store so dialogue and tick systems can share it safely.
 */
public final class CarnivalBalloonSession {
    public enum Phase {
        IDLE,
        PLAYING,
        RESULTS
    }

    private Phase phase = Phase.IDLE;
    @Nullable
    private UUID playerUuid;
    private int popped;
    private int spawned;
    private int resolved;
    private float spawnCooldown;
    private final List<UUID> activeBalloonUuids = new ArrayList<>();
    private int pendingTickets = -1;
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

    public int getPopped() {
        return popped;
    }

    public int getSpawned() {
        return spawned;
    }

    public int getResolved() {
        return resolved;
    }

    public float getSpawnCooldown() {
        return spawnCooldown;
    }

    public void setSpawnCooldown(float spawnCooldown) {
        this.spawnCooldown = Math.max(0f, spawnCooldown);
    }

    public void addSpawnCooldown(float dt) {
        spawnCooldown = Math.max(0f, spawnCooldown - dt);
    }

    @Nonnull
    public List<UUID> activeBalloonUuidsView() {
        return List.copyOf(activeBalloonUuids);
    }

    public void addActiveBalloon(@Nonnull UUID uuid) {
        activeBalloonUuids.add(uuid);
        spawned++;
    }

    public void removeActiveBalloon(@Nonnull UUID uuid) {
        activeBalloonUuids.remove(uuid);
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
        popped = 0;
        spawned = 0;
        resolved = 0;
        spawnCooldown = 0.15f;
        activeBalloonUuids.clear();
        pendingTickets = -1;
        return true;
    }

    public void markPopped(@Nonnull UUID balloonUuid) {
        if (phase != Phase.PLAYING) {
            return;
        }
        if (activeBalloonUuids.remove(balloonUuid)) {
            popped++;
            resolved++;
            maybeFinish();
        }
    }

    public void markMissed(@Nonnull UUID balloonUuid) {
        if (phase != Phase.PLAYING) {
            return;
        }
        if (activeBalloonUuids.remove(balloonUuid)) {
            resolved++;
            maybeFinish();
        }
    }

    private void maybeFinish() {
        if (spawned >= CarnivalIds.BALLOON_TOTAL && resolved >= CarnivalIds.BALLOON_TOTAL) {
            phase = Phase.RESULTS;
            pendingTickets = CarnivalIds.balloonTicketReward(popped);
            finishSfxPending = true;
        }
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
        popped = 0;
        spawned = 0;
        resolved = 0;
        spawnCooldown = 0f;
        activeBalloonUuids.clear();
        pendingTickets = -1;
        finishSfxPending = false;
    }
}
