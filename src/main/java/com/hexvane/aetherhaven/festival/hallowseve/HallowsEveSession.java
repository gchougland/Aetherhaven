package com.hexvane.aetherhaven.festival.hallowseve;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Per-town maze run. Kept outside the entity Store so dialogue and tick systems can share it safely.
 */
public final class HallowsEveSession {
    public enum Phase {
        IDLE,
        COUNTDOWN,
        RACING,
        READY_TO_BURST,
        BURSTING
    }

    private Phase phase = Phase.IDLE;
    @Nullable
    private UUID playerUuid;
    private long phaseStartEpochMs;
    private int collected;
    private int totalOrbs;
    private boolean pendingTeleport;
    private boolean pendingOrbSpawn;
    private boolean pendingThaw;
    private double startX;
    private double startY;
    private double startZ;
    private float startYawDegrees;
    @Nonnull
    private final List<Vector3d> orbWorldPositions = new ArrayList<>();
    @Nonnull
    private final List<UUID> activeOrbUuids = new ArrayList<>();

    @Nonnull
    public Phase getPhase() {
        return phase;
    }

    @Nullable
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public boolean canStart(@Nonnull UUID player) {
        return phase == Phase.IDLE;
    }

    public boolean isBusy() {
        return phase != Phase.IDLE;
    }

    public boolean isBusyForOther(@Nonnull UUID player) {
        return isBusy() && (playerUuid == null || !playerUuid.equals(player));
    }

    public boolean isRacer(@Nonnull UUID player) {
        return playerUuid != null && playerUuid.equals(player);
    }

    public boolean isRacing(@Nonnull UUID player) {
        return phase == Phase.RACING && isRacer(player);
    }

    /**
     * Anyone standing at the festival may pop the pumpkin once the run has filled it, not just the racer. The
     * racer is often still walking back when the crowd wants to see it burst.
     */
    public boolean isReadyToBurst() {
        return phase == Phase.READY_TO_BURST;
    }

    public boolean tryBegin(@Nonnull UUID player, long nowMs) {
        if (!canStart(player)) {
            return false;
        }
        phase = Phase.COUNTDOWN;
        playerUuid = player;
        phaseStartEpochMs = nowMs;
        collected = 0;
        pendingTeleport = true;
        pendingOrbSpawn = false;
        pendingThaw = false;
        activeOrbUuids.clear();
        return true;
    }

    public boolean consumePendingTeleport() {
        if (!pendingTeleport) {
            return false;
        }
        pendingTeleport = false;
        return true;
    }

    public boolean consumePendingOrbSpawn() {
        if (!pendingOrbSpawn) {
            return false;
        }
        pendingOrbSpawn = false;
        return true;
    }

    public boolean consumePendingThaw() {
        if (!pendingThaw) {
            return false;
        }
        pendingThaw = false;
        return true;
    }

    public void setStartPad(double x, double y, double z, float yawDegrees) {
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.startYawDegrees = yawDegrees;
    }

    public double getStartX() {
        return startX;
    }

    public double getStartY() {
        return startY;
    }

    public double getStartZ() {
        return startZ;
    }

    public float getStartYawDegrees() {
        return startYawDegrees;
    }

    public long getPhaseStartEpochMs() {
        return phaseStartEpochMs;
    }

    public int countdownSecondsLeft(long nowMs) {
        if (phase != Phase.COUNTDOWN) {
            return 0;
        }
        long remaining = HallowsEveIds.COUNTDOWN_MS - (nowMs - phaseStartEpochMs);
        if (remaining <= 0L) {
            return 0;
        }
        return (int) Math.ceil(remaining / 1000.0);
    }

    public long raceRemainingMs(long nowMs) {
        if (phase != Phase.RACING) {
            return 0L;
        }
        return Math.max(0L, HallowsEveIds.RACE_MS - (nowMs - phaseStartEpochMs));
    }

    public boolean tickCountdown(long nowMs) {
        if (phase != Phase.COUNTDOWN) {
            return false;
        }
        if (nowMs - phaseStartEpochMs < HallowsEveIds.COUNTDOWN_MS) {
            return false;
        }
        phase = Phase.RACING;
        phaseStartEpochMs = nowMs;
        pendingThaw = true;
        pendingOrbSpawn = true;
        return true;
    }

    public boolean tickRace(long nowMs) {
        if (phase != Phase.RACING) {
            return false;
        }
        if (collected >= totalOrbs && totalOrbs > 0) {
            finishRace(nowMs);
            return true;
        }
        if (nowMs - phaseStartEpochMs >= HallowsEveIds.RACE_MS) {
            finishRace(nowMs);
            return true;
        }
        return false;
    }

    private void finishRace(long nowMs) {
        phase = collected > 0 ? Phase.READY_TO_BURST : Phase.IDLE;
        phaseStartEpochMs = nowMs;
        if (collected <= 0) {
            playerUuid = null;
        }
        activeOrbUuids.clear();
    }

    public void beginBurst(long nowMs) {
        if (phase != Phase.READY_TO_BURST) {
            return;
        }
        phase = Phase.BURSTING;
        phaseStartEpochMs = nowMs;
    }

    public void finishBurst() {
        phase = Phase.IDLE;
        playerUuid = null;
        collected = 0;
        activeOrbUuids.clear();
        pendingTeleport = false;
        pendingOrbSpawn = false;
        pendingThaw = true;
    }

    public void clearAll() {
        phase = Phase.IDLE;
        playerUuid = null;
        collected = 0;
        totalOrbs = 0;
        pendingTeleport = false;
        pendingOrbSpawn = false;
        pendingThaw = true;
        activeOrbUuids.clear();
        orbWorldPositions.clear();
    }

    public int getCollected() {
        return collected;
    }

    public int getTotalOrbs() {
        return totalOrbs;
    }

    public void setTotalOrbs(int totalOrbs) {
        this.totalOrbs = Math.max(0, totalOrbs);
    }

    public void addCollected() {
        if (phase == Phase.RACING) {
            collected = Math.min(totalOrbs, collected + 1);
        }
    }

    @Nonnull
    public List<Vector3d> orbWorldPositionsView() {
        return List.copyOf(orbWorldPositions);
    }

    public void setOrbWorldPositions(@Nonnull List<Vector3d> positions) {
        orbWorldPositions.clear();
        for (Vector3d p : positions) {
            if (p != null) {
                orbWorldPositions.add(new Vector3d(p));
            }
        }
        totalOrbs = orbWorldPositions.size();
    }

    public void addActiveOrb(@Nonnull UUID uuid) {
        activeOrbUuids.add(uuid);
    }

    public void removeActiveOrb(@Nonnull UUID uuid) {
        activeOrbUuids.remove(uuid);
    }

    @Nonnull
    public List<UUID> activeOrbUuidsView() {
        return List.copyOf(activeOrbUuids);
    }

    public void clearActiveOrbs() {
        activeOrbUuids.clear();
    }
}
