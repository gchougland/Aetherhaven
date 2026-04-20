package com.hexvane.aetherhaven.time;

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import javax.annotation.Nonnull;

public final class GameTimeEpochs {
    private GameTimeEpochs() {}

    /** Same minute index as villager schedule / inn epoch minute math. */
    public static long gameEpochMinute(@Nonnull LocalDateTime gameTime) {
        return gameTime.toLocalDate().toEpochDay() * 24L * 60L + gameTime.toLocalTime().toSecondOfDay() / 60L;
    }

    public static long gameEpochMinute(@Nonnull Instant instant) {
        return gameEpochMinute(LocalDateTime.ofInstant(instant, WorldTimeResource.ZONE_OFFSET));
    }

    /** Epoch days (inclusive) that have a configured morning hour start strictly inside {@code (from, to]}. */
    public static void collectEpochDaysWhereMorningStartOccurred(
        @Nonnull Instant from,
        @Nonnull Instant to,
        int morningStartHour,
        @Nonnull ZoneOffset zone,
        @Nonnull java.util.Collection<Long> out
    ) {
        if (!to.isAfter(from)) {
            return;
        }
        LocalDateTime a = LocalDateTime.ofInstant(from, zone);
        LocalDateTime b = LocalDateTime.ofInstant(to, zone);
        int h = Math.max(0, Math.min(23, morningStartHour));
        LocalDate endDate = b.toLocalDate();
        for (LocalDate d = a.toLocalDate(); !d.isAfter(endDate); d = d.plusDays(1)) {
            LocalDateTime morning = LocalDateTime.of(d, LocalTime.of(h, 0));
            Instant mi = morning.toInstant(zone);
            if (mi.compareTo(from) > 0 && !mi.isAfter(to)) {
                out.add(d.toEpochDay());
            }
        }
    }

    /** True if wall-clock game time jumped within the same calendar minute (e.g. /time adjust). */
    public static boolean largeSameMinuteInstantDelta(@Nonnull Instant prev, @Nonnull Instant now) {
        long sec = Math.abs(ChronoUnit.SECONDS.between(prev, now));
        return sec > 5L;
    }
}
