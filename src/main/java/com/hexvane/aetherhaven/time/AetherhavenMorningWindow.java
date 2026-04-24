package com.hexvane.aetherhaven.time;

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import javax.annotation.Nonnull;

/**
 * Shared "in-game morning" test for inn visitor refresh, farm sprinklers, and treasury UI preview. Uses
 * {@link com.hexvane.aetherhaven.config.AetherhavenPluginConfig#getGameMorningStartHour()} plus a scaled-day band so
 * {@code /time dawn} and early sunrises still qualify when the discrete hour has not reached the configured start yet.
 */
public final class AetherhavenMorningWindow {
    private AetherhavenMorningWindow() {}

    public static boolean isGameMorning(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        if (isInMorningHourWindow(wtr, morningStartHour, morningEndExclusive)) {
            return true;
        }
        // Broad: /time set dawn and similar often sit outside the older 0.08–0.5 band. Cap by clock hour to avoid
        // treating late evening as "morning".
        if (wtr.getCurrentHour() < 12 && wtr.isScaledDayTimeWithinRange(0.0f, 0.55f)) {
            return true;
        }
        return wtr.isScaledDayTimeWithinRange(0.08f, 0.52f);
    }

    private static boolean isInMorningHourWindow(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        int h = wtr.getCurrentHour();
        int start = Math.max(0, Math.min(23, morningStartHour));
        int end = morningEndExclusive;
        if (end <= start) {
            end = Math.min(start + 6, 24);
        }
        return h >= start && h < end;
    }
}
