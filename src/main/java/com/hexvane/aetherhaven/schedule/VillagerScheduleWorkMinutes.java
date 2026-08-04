package com.hexvane.aetherhaven.schedule;

import com.hexvane.aetherhaven.time.GameTimeEpochs;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Counts in-game minutes where a weekly schedule is in the {@code work} segment. */
public final class VillagerScheduleWorkMinutes {
    private static final int MINUTES_PER_DAY = 24 * 60;

    private VillagerScheduleWorkMinutes() {}

    @Nonnull
    public static LocalDateTime localDateTimeFromEpochMinute(long epochMinute) {
        long day = Math.floorDiv(epochMinute, MINUTES_PER_DAY);
        int minuteOfDay = (int) Math.floorMod(epochMinute, MINUTES_PER_DAY);
        LocalDate date = LocalDate.ofEpochDay(day);
        LocalTime time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
        return LocalDateTime.of(date, time);
    }

    public static boolean isWorkMinute(long epochMinute, @Nonnull VillagerScheduleDefinition schedule) {
        if (schedule.getTransitions().isEmpty()) {
            return false;
        }
        LocalDateTime dt = localDateTimeFromEpochMinute(epochMinute);
        return VillagerScheduleResolver.LOC_WORK.equals(
            Objects.requireNonNullElse(
                VillagerScheduleResolver.activeLocationSymbol(schedule, dt),
                ""
            )
        );
    }

    /**
     * Counts minutes in {@code (fromExclusive, toInclusive]} where the schedule location is {@code work}.
     *
     * @param maxMinutesToScan caps iteration (forward jumps); excess minutes advance the cursor without credit
     * @return work-minute count within the capped window
     */
    public static int countWorkMinutes(
        long fromExclusive,
        long toInclusive,
        @Nonnull VillagerScheduleDefinition schedule,
        int maxMinutesToScan
    ) {
        if (schedule.getTransitions().isEmpty() || toInclusive <= fromExclusive || maxMinutesToScan <= 0) {
            return 0;
        }
        int work = 0;
        int scanned = 0;
        for (long m = fromExclusive + 1; m <= toInclusive && scanned < maxMinutesToScan; m++, scanned++) {
            if (isWorkMinute(m, schedule)) {
                work++;
            }
        }
        return work;
    }

    /** Minutes actually scanned in {@code (fromExclusive, toInclusive]} after applying {@code maxMinutesToScan}. */
    public static long scannedMinuteSpan(long fromExclusive, long toInclusive, int maxMinutesToScan) {
        if (toInclusive <= fromExclusive || maxMinutesToScan <= 0) {
            return 0L;
        }
        long span = toInclusive - fromExclusive;
        return Math.min(span, maxMinutesToScan);
    }

    /** Cursor after processing up to {@code maxMinutesToScan} minutes forward from {@code fromExclusive}. */
    public static long cursorAfterScan(long fromExclusive, long toInclusive, int maxMinutesToScan) {
        return fromExclusive + scannedMinuteSpan(fromExclusive, toInclusive, maxMinutesToScan);
    }

    public static long currentEpochMinute(@Nonnull LocalDateTime gameDateTime) {
        return GameTimeEpochs.gameEpochMinute(gameDateTime);
    }

    /**
     * Counts scheduled {@code work} minutes in the in-game span {@code (from, to]} using calendar datetime (handles
     * {@code /time set} jumps where instant epoch indices can disagree with the visible clock).
     */
    public static int countWorkMinutesBetweenInstants(
        @Nonnull Instant from,
        @Nonnull Instant to,
        @Nonnull VillagerScheduleDefinition schedule,
        int maxMinutesToScan
    ) {
        if (!to.isAfter(from) || schedule.getTransitions().isEmpty() || maxMinutesToScan <= 0) {
            return 0;
        }
        LocalDateTime dtFrom = LocalDateTime.ofInstant(from, WorldTimeResource.ZONE_OFFSET);
        LocalDateTime dtTo = LocalDateTime.ofInstant(to, WorldTimeResource.ZONE_OFFSET);
        long minuteSpan = ChronoUnit.MINUTES.between(dtFrom, dtTo);
        if (minuteSpan <= 0L) {
            return 0;
        }
        long fromExclusive = GameTimeEpochs.gameEpochMinute(dtFrom);
        long toInclusive = fromExclusive + minuteSpan;
        return countWorkMinutes(fromExclusive, toInclusive, schedule, maxMinutesToScan);
    }

    /** Game seconds represented by {@link #countWorkMinutesBetweenInstants} (each counted minute = 60 in-game seconds). */
    public static double workGameSecondsFromMinuteCount(int workMinutes) {
        return (double) workMinutes * 60.0;
    }

    /** Cursor after scanning the instant span (same minute span as {@link #countWorkMinutesBetweenInstants}). */
    public static long cursorAfterInstantScan(
        @Nonnull Instant from,
        @Nonnull Instant to,
        long fromExclusiveEpochMinute,
        int maxMinutesToScan
    ) {
        if (!to.isAfter(from) || maxMinutesToScan <= 0) {
            return fromExclusiveEpochMinute;
        }
        LocalDateTime dtFrom = LocalDateTime.ofInstant(from, WorldTimeResource.ZONE_OFFSET);
        LocalDateTime dtTo = LocalDateTime.ofInstant(to, WorldTimeResource.ZONE_OFFSET);
        long minuteSpan = ChronoUnit.MINUTES.between(dtFrom, dtTo);
        if (minuteSpan <= 0L) {
            return fromExclusiveEpochMinute;
        }
        long scanned = Math.min(minuteSpan, maxMinutesToScan);
        return fromExclusiveEpochMinute + scanned;
    }
}
