package com.hexvane.aetherhaven.festival.carnival;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-town spin-the-wheel game. Kept outside the entity Store so dialogue and tick systems can share it safely.
 */
public final class CarnivalWheelSession {
    public enum Phase {
        IDLE,
        SPINNING,
        RESULTS
    }

    private Phase phase = Phase.IDLE;
    @Nullable
    private UUID playerUuid;
    @Nullable
    private UUID faceEntityUuid;
    private float spinElapsed;
    private float spinDuration = CarnivalIds.WHEEL_SPIN_SECONDS;
    private float startRoll;
    private float targetRoll;
    private float tickSfxAccum;
    private boolean won;
    private boolean resultPending;

    @Nonnull
    public Phase getPhase() {
        return phase;
    }

    @Nullable
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Nullable
    public UUID getFaceEntityUuid() {
        return faceEntityUuid;
    }

    public void setFaceEntityUuid(@Nullable UUID faceEntityUuid) {
        this.faceEntityUuid = faceEntityUuid;
    }

    public float getSpinElapsed() {
        return spinElapsed;
    }

    public float getSpinDuration() {
        return spinDuration;
    }

    public float getStartRoll() {
        return startRoll;
    }

    public float getTargetRoll() {
        return targetRoll;
    }

    public float getTickSfxAccum() {
        return tickSfxAccum;
    }

    public void setTickSfxAccum(float tickSfxAccum) {
        this.tickSfxAccum = tickSfxAccum;
    }

    public boolean isBusy() {
        return phase == Phase.SPINNING || phase == Phase.RESULTS;
    }

    public boolean canStart(@Nonnull UUID player) {
        return phase == Phase.IDLE && faceEntityUuid != null;
    }

    public boolean isSpinning(@Nonnull UUID player) {
        return phase == Phase.SPINNING && player.equals(playerUuid);
    }

    public boolean hasWin(@Nonnull UUID player) {
        return phase == Phase.RESULTS && resultPending && won && player.equals(playerUuid);
    }

    public boolean hasLoss(@Nonnull UUID player) {
        return phase == Phase.RESULTS && resultPending && !won && player.equals(playerUuid);
    }

    public boolean isBusyForOther(@Nonnull UUID player) {
        return isBusy() && (playerUuid == null || !playerUuid.equals(player));
    }

    public boolean tryBegin(@Nonnull UUID player, float currentRoll) {
        if (!canStart(player)) {
            return false;
        }
        phase = Phase.SPINNING;
        playerUuid = player;
        spinElapsed = 0f;
        spinDuration = CarnivalIds.WHEEL_SPIN_SECONDS;
        startRoll = currentRoll;
        int octant = ThreadLocalRandom.current().nextInt(8);
        // Land near the center of an octant; idle offset keeps rest pose between wedges.
        targetRoll = CarnivalIds.WHEEL_IDLE_OFFSET_RAD
            + octant * (float) (Math.PI / 4.0)
            + (float) (Math.PI / 8.0)
            + (float) (Math.PI * 2.0 * (4 + ThreadLocalRandom.current().nextInt(3)));
        tickSfxAccum = 0f;
        won = false;
        resultPending = false;
        return true;
    }

    public void addSpinElapsed(float dt) {
        spinElapsed += dt;
    }

    public boolean isSpinComplete() {
        return phase == Phase.SPINNING && spinElapsed >= spinDuration;
    }

    public void finishSpin(float finalRoll) {
        if (phase != Phase.SPINNING) {
            return;
        }
        int octant = octantAtTop(finalRoll);
        won = (octant & 1) == 0;
        phase = Phase.RESULTS;
        resultPending = true;
    }

    public int collectWin(@Nonnull UUID player) {
        if (!hasWin(player)) {
            return 0;
        }
        resultPending = false;
        phase = Phase.IDLE;
        playerUuid = null;
        return CarnivalIds.WHEEL_WIN_TICKETS;
    }

    public boolean acknowledgeLoss(@Nonnull UUID player) {
        if (!hasLoss(player)) {
            return false;
        }
        resultPending = false;
        phase = Phase.IDLE;
        playerUuid = null;
        return true;
    }

    /** Octant under the top pointer; rest pose sits between wedges via {@link CarnivalIds#WHEEL_IDLE_OFFSET_RAD}. */
    public static int octantAtTop(float rollRadians) {
        double relative = rollRadians - CarnivalIds.WHEEL_IDLE_OFFSET_RAD;
        double step = Math.PI / 4.0;
        double norm = relative % (Math.PI * 2.0);
        if (norm < 0) {
            norm += Math.PI * 2.0;
        }
        return (int) Math.floor(norm / step) % 8;
    }

    public void clearGameplay() {
        phase = Phase.IDLE;
        playerUuid = null;
        spinElapsed = 0f;
        tickSfxAccum = 0f;
        won = false;
        resultPending = false;
    }

    public void clearAll() {
        clearGameplay();
        faceEntityUuid = null;
    }
}
