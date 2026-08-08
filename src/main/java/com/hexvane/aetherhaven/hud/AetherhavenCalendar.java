package com.hexvane.aetherhaven.hud;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A compact four-season calendar whose epoch is {@code 0001-01-01 = Spring 1, Year 1}. */
public final class AetherhavenCalendar {
    public static final int DAYS_PER_SEASON = 28;
    public static final int SEASONS_PER_YEAR = 4;
    public static final int DAYS_PER_YEAR = DAYS_PER_SEASON * SEASONS_PER_YEAR;
    public static final LocalDate EPOCH_DATE = LocalDate.of(1, 1, 1);

    private static final DateTimeFormatter CLOCK_FORMAT =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);

    private AetherhavenCalendar() {}

    @Nonnull
    public static CalendarDate from(@Nonnull LocalDateTime gameTime) {
        long epochDay = gameTime.toLocalDate().toEpochDay() - EPOCH_DATE.toEpochDay();
        long yearIndex = Math.floorDiv(epochDay, DAYS_PER_YEAR);
        int dayOfYear = (int) Math.floorMod(epochDay, DAYS_PER_YEAR);
        Season season = Season.values()[dayOfYear / DAYS_PER_SEASON];
        int dayOfSeason = dayOfYear % DAYS_PER_SEASON + 1;
        return new CalendarDate(season, dayOfSeason, yearIndex + 1L);
    }

    @Nonnull
    public static String formatDate(@Nonnull LocalDateTime gameTime) {
        return from(gameTime).displayText();
    }

    /** Formats a Hytale game {@link LocalDate#toEpochDay()} value for player-facing UI. */
    @Nonnull
    public static String formatDateFromEpochDay(long epochDay) {
        return formatDate(LocalDate.ofEpochDay(epochDay).atStartOfDay());
    }

    /** Formats the game wall clock as a 12-hour clock with lowercase {@code am}/{@code pm}. */
    @Nonnull
    public static String formatClock(@Nonnull LocalDateTime gameTime) {
        return gameTime.format(CLOCK_FORMAT).toLowerCase(Locale.ROOT);
    }

    /** Midnight on the given Aetherhaven calendar date (epoch {@code 0001-01-01 = Spring 1, Year 1}). */
    @Nonnull
    public static LocalDateTime toLocalDateTime(@Nonnull Season season, int dayOfSeason, long year) {
        if (year < 1L) {
            throw new IllegalArgumentException("year must be at least 1");
        }
        if (dayOfSeason < 1 || dayOfSeason > DAYS_PER_SEASON) {
            throw new IllegalArgumentException("dayOfSeason must be between 1 and " + DAYS_PER_SEASON);
        }
        int dayOfYear = season.ordinal() * DAYS_PER_SEASON + (dayOfSeason - 1);
        long epochDay = EPOCH_DATE.toEpochDay() + (year - 1L) * DAYS_PER_YEAR + dayOfYear;
        return LocalDate.ofEpochDay(epochDay).atStartOfDay();
    }

    @Nullable
    public static Season parseSeason(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim();
        for (Season season : Season.values()) {
            if (season.displayName().equalsIgnoreCase(normalized) || season.name().equalsIgnoreCase(normalized)) {
                return season;
            }
        }
        return null;
    }

    @Nonnull
    public static String formatSeasonHeader(@Nonnull CalendarDate date) {
        return date.season().displayName() + ", Year " + date.year();
    }

    public enum Season {
        SPRING("Spring"),
        SUMMER("Summer"),
        AUTUMN("Autumn"),
        WINTER("Winter");

        @Nonnull
        private final String displayName;

        Season(@Nonnull String displayName) {
            this.displayName = displayName;
        }

        @Nonnull
        public String displayName() {
            return displayName;
        }
    }

    public record CalendarDate(@Nonnull Season season, int dayOfSeason, long year) {
        public CalendarDate {
            if (dayOfSeason < 1 || dayOfSeason > DAYS_PER_SEASON) {
                throw new IllegalArgumentException("dayOfSeason must be between 1 and " + DAYS_PER_SEASON);
            }
        }

        @Nonnull
        public String displayText() {
            return season.displayName() + " " + dayOfSeason + ", Year " + year;
        }
    }
}
