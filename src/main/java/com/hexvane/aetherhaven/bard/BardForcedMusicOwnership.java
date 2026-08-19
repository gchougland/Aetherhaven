package com.hexvane.aetherhaven.bard;

import javax.annotation.Nonnull;

/**
 * Decides whether bard proximity should write {@link com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker}.
 * Only clears forced music that bard started; leaves trigger-volume / command forced music alone.
 */
public final class BardForcedMusicOwnership {
    private BardForcedMusicOwnership() {}

    /**
     * @param desiredContainer nearest bard music index, or {@code 0} when none
     * @param haveContainer current forced music index on the player
     * @param wasListening whether this player is tracked as listening to bard music
     */
    @Nonnull
    public static Decision decide(int desiredContainer, int haveContainer, boolean wasListening) {
        if (desiredContainer != 0 && !wasListening) {
            return Decision.apply(desiredContainer);
        }
        if (haveContainer == desiredContainer) {
            if (desiredContainer == 0 && wasListening) {
                return Decision.clearListeningOnly();
            }
            return Decision.noop();
        }
        if (desiredContainer != 0) {
            return Decision.apply(desiredContainer);
        }
        if (wasListening) {
            return Decision.clearBardMusic();
        }
        return Decision.noop();
    }

    public record Decision(boolean updateTracker, int containerIndex, boolean markListening, boolean clearListening) {
        @Nonnull
        static Decision noop() {
            return new Decision(false, 0, false, false);
        }

        @Nonnull
        static Decision clearListeningOnly() {
            return new Decision(false, 0, false, true);
        }

        @Nonnull
        static Decision apply(int containerIndex) {
            return new Decision(true, containerIndex, true, false);
        }

        @Nonnull
        static Decision clearBardMusic() {
            return new Decision(true, 0, false, true);
        }
    }
}
