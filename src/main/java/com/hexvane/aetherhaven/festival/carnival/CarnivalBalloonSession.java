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
    private int lastSpawnSpotIndex = -1;
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

    public synchronized void addSpawnCooldown(float dt) {
        spawnCooldown = Math.max(0f, spawnCooldown - dt);
    }

    /** @return true when the spawn interval has elapsed and another balloon may be reserved. */
    public synchronized boolean tickSpawnCooldown(float dt) {
        spawnCooldown = Math.max(0f, spawnCooldown - dt);
        return phase == Phase.PLAYING && spawned < CarnivalIds.BALLOON_TOTAL && spawnCooldown <= 0f;
    }

    public int getLastSpawnSpotIndex() {
        return lastSpawnSpotIndex;
    }

    public void setLastSpawnSpotIndex(int lastSpawnSpotIndex) {
        this.lastSpawnSpotIndex = lastSpawnSpotIndex;
    }

    @Nonnull
    public List<UUID> activeBalloonUuidsView() {
        return List.copyOf(activeBalloonUuids);
    }

    public void addActiveBalloon(@Nonnull UUID uuid) {
        activeBalloonUuids.add(uuid);
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
        // Full interval before the first balloon so the director cannot double-fire on the opening tick.
        spawnCooldown = CarnivalIds.BALLOON_SPAWN_INTERVAL;
        lastSpawnSpotIndex = -1;
        activeBalloonUuids.clear();
        pendingTickets = -1;
        return true;
    }

    /**
     * Reserves the next balloon slot and arm the spawn interval. Call before queueing a spawn so deferred
     * {@code world.execute} work cannot schedule a second balloon for the same slot.
     */
    public synchronized boolean tryReserveSpawn() {
        if (phase != Phase.PLAYING || spawned >= CarnivalIds.BALLOON_TOTAL || spawnCooldown > 0f) {
            return false;
        }
        spawned++;
        spawnCooldown = CarnivalIds.BALLOON_SPAWN_INTERVAL;
        return true;
    }

    public synchronized void cancelReservedSpawn() {
        if (spawned > 0) {
            spawned--;
        }
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
        lastSpawnSpotIndex = -1;
        activeBalloonUuids.clear();
        pendingTickets = -1;
        finishSfxPending = false;
    }
}
