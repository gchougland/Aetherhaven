package com.hexvane.aetherhaven.schedule;

import java.time.DayOfWeek;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Compares schedule transitions by day and clock time (for patch remove/replace). */
final class VillagerScheduleTransitionMatcher {
    private VillagerScheduleTransitionMatcher() {}

    static boolean matchesTime(@Nonnull VillagerScheduleTransition a, @Nonnull VillagerScheduleTransition b) {
        return parseDayOfWeek(a.getDayOfWeek()) == parseDayOfWeek(b.getDayOfWeek())
            && clampHour(a.getHour()) == clampHour(b.getHour())
            && clampMinute(a.getMinute()) == clampMinute(b.getMinute());
    }

    @Nonnull
    private static DayOfWeek parseDayOfWeek(@Nullable Object raw) {
        if (raw == null) {
            return DayOfWeek.MONDAY;
        }
        if (raw instanceof Number n) {
            int v = n.intValue();
            if (v >= 1 && v <= 7) {
                return DayOfWeek.of(v);
            }
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return DayOfWeek.MONDAY;
        }
        try {
            return DayOfWeek.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            try {
                int v = Integer.parseInt(s);
                if (v >= 1 && v <= 7) {
                    return DayOfWeek.of(v);
                }
            } catch (NumberFormatException ignored) {
            }
            return DayOfWeek.MONDAY;
        }
    }

    private static int clampHour(int hour) {
        return Math.max(0, Math.min(23, hour));
    }

    private static int clampMinute(int minute) {
        return Math.max(0, Math.min(59, minute));
    }
}
