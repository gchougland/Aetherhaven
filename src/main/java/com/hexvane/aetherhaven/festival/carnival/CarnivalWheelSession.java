package com.hexvane.aetherhaven.festival.carnival;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
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

    public enum Outcome {
        WIN,
        LOSE,
        CLOWN
    }

    /** Optional next-spin octant override for creative testing ({@code -1} = random). */
    private static final AtomicInteger FORCE_NEXT_OCTANT = new AtomicInteger(-1);

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
    private Outcome outcome = Outcome.LOSE;
    private boolean specialActive;
    /** Octant chosen when the spin began; used for the result so float drift cannot shift the wedge. */
    private int plannedOctant;
    private boolean resultPending;

    /** Forces the next {@link #tryBegin} in this process to land on {@code octant} (0..7), or clears with {@code -1}. */
    public static void setForceNextOctant(int octant) {
        if (octant < 0) {
            FORCE_NEXT_OCTANT.set(-1);
            return;
        }
        FORCE_NEXT_OCTANT.set(Math.floorMod(octant, 8));
    }

    public static int getForceNextOctant() {
        return FORCE_NEXT_OCTANT.get();
    }

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

    public int getPlannedOctant() {
        return plannedOctant;
    }

    public boolean isBusy() {
        return phase == Phase.SPINNING || phase == Phase.RESULTS;
    }

    public boolean canStart(@Nonnull UUID player) {
        // Face entity is only required when the spin actually begins; dialogue stays available even if the
        // decorative face failed to spawn so the attendant is not a dead end.
        return phase == Phase.IDLE;
    }

    public boolean isSpinning(@Nonnull UUID player) {
        return phase == Phase.SPINNING && player.equals(playerUuid);
    }

    public boolean hasWin(@Nonnull UUID player) {
        return phase == Phase.RESULTS && resultPending && outcome == Outcome.WIN && player.equals(playerUuid);
    }

    public boolean hasLoss(@Nonnull UUID player) {
        return phase == Phase.RESULTS && resultPending && outcome == Outcome.LOSE && player.equals(playerUuid);
    }

    public boolean hasClown(@Nonnull UUID player) {
        return phase == Phase.RESULTS && resultPending && outcome == Outcome.CLOWN && player.equals(playerUuid);
    }

    public boolean isBusyForOther(@Nonnull UUID player) {
        return isBusy() && (playerUuid == null || !playerUuid.equals(player));
    }

    /**
     * Begins a spin from the idle rest pose (not the previous landing). Returns the start roll so callers can snap the
     * face prop before the director ticks.
     */
    public boolean tryBegin(@Nonnull UUID player, boolean specialActive) {
        if (!canStart(player)) {
            return false;
        }
        phase = Phase.SPINNING;
        playerUuid = player;
        this.specialActive = specialActive;
        spinElapsed = 0f;
        spinDuration = CarnivalIds.WHEEL_SPIN_SECONDS;
        float twoPi = (float) (Math.PI * 2.0);
        // Always restart from the authored idle pose so wedge mapping stays stable across spins.
        startRoll = CarnivalIds.WHEEL_IDLE_OFFSET_RAD;
        int forced = FORCE_NEXT_OCTANT.getAndSet(-1);
        plannedOctant = forced >= 0 ? Math.floorMod(forced, 8) : ThreadLocalRandom.current().nextInt(8);
        // Land near the center of an octant; idle offset keeps rest pose between wedges.
        float landing = normalizeRoll(
            CarnivalIds.WHEEL_IDLE_OFFSET_RAD
                + plannedOctant * (float) (Math.PI / 4.0)
                + (float) (Math.PI / 8.0),
            twoPi
        );
        float travel = landing - startRoll;
        while (travel < twoPi * CarnivalIds.WHEEL_MIN_FULL_SPINS) {
            travel += twoPi;
        }
        // A little extra drama on top of the guaranteed minimum.
        travel += twoPi * ThreadLocalRandom.current().nextInt(3);
        targetRoll = startRoll + travel;
        tickSfxAccum = 0f;
        outcome = Outcome.LOSE;
        resultPending = false;
        return true;
    }

    public void addSpinElapsed(float dt) {
        spinElapsed += dt;
    }

    public boolean isSpinComplete() {
        return phase == Phase.SPINNING && spinElapsed >= spinDuration;
    }

    public boolean didWin() {
        return outcome == Outcome.WIN;
    }

    @Nonnull
    public Outcome getOutcome() {
        return outcome;
    }

    /** Current eased roll for the face prop while spinning (or the rest pose when idle). */
    public float currentRoll() {
        if (phase != Phase.SPINNING && phase != Phase.RESULTS) {
            return startRoll;
        }
        float t = Math.min(1f, spinElapsed / Math.max(0.01f, spinDuration));
        // Ease-out quintic: fast at first, then a long slow finish.
        float u = 1f - t;
        float eased = 1f - u * u * u * u * u;
        return startRoll + (targetRoll - startRoll) * eased;
    }

    public void finishSpin(float finalRoll) {
        if (phase != Phase.SPINNING) {
            return;
        }
        // Prefer the octant chosen at start so a float edge case cannot flip the prize wedge.
        int octant = plannedOctant;
        if (specialActive && octant == CarnivalIds.WHEEL_CLOWN_OCTANT) {
            outcome = Outcome.CLOWN;
        } else {
            outcome = (octant & 1) == 0 ? Outcome.WIN : Outcome.LOSE;
        }
        phase = Phase.RESULTS;
        resultPending = true;
        startRoll = normalizeRoll(finalRoll, (float) (Math.PI * 2.0));
    }

    private static float normalizeRoll(float roll, float twoPi) {
        float n = roll % twoPi;
        if (n < 0f) {
            n += twoPi;
        }
        return n;
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
        outcome = Outcome.LOSE;
        specialActive = false;
        plannedOctant = 0;
        resultPending = false;
        startRoll = CarnivalIds.WHEEL_IDLE_OFFSET_RAD;
    }

    public void clearAll() {
        clearGameplay();
        faceEntityUuid = null;
    }
}
