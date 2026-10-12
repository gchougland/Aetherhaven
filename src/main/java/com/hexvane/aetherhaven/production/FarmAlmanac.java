package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The Farmer's Almanac: which crops are favoured in which week. Weeks are the town calendar's own weeks, seven days
 * counted from Spring 1 of Year 1, four to a season. The choice depends on nothing but the week and the crops the
 * Farmer can grow, so every player and every reload sees the same Almanac.
 */
public final class FarmAlmanac {
    private FarmAlmanac() {}
    public static final int DAYS_PER_WEEK = 7;
    public static final int WEEKS_PER_SEASON = AetherhavenCalendar.DAYS_PER_SEASON / DAYS_PER_WEEK;

    /** A week of the town calendar: its season, and its place in that season from 1 to 4. */
    public record Week(long index, AetherhavenCalendar.Season season, int weekOfSeason, long year) {}

    /**
     * Whole weeks since Spring 1 of Year 1. The game's own clock counts that day as far below zero, so the count
     * goes through the town calendar's first day and never through the sign of the clock.
     */
    public static long week(LocalDateTime gameTime) {
        long day = gameTime.toLocalDate().toEpochDay() - AetherhavenCalendar.EPOCH_DATE.toEpochDay();
        return Math.floorDiv(day, DAYS_PER_WEEK);
    }
    /** Days left in the week of this time, counting today: 7 on its first day, 1 on its last. */
    public static int daysLeft(LocalDateTime gameTime) {
        long day = gameTime.toLocalDate().toEpochDay() - AetherhavenCalendar.EPOCH_DATE.toEpochDay();
        return DAYS_PER_WEEK - (int) Math.floorMod(day, DAYS_PER_WEEK);
    }
    public static Week describe(long week) {
        int weeksPerYear = WEEKS_PER_SEASON * AetherhavenCalendar.SEASONS_PER_YEAR;
        int ofYear = (int) Math.floorMod(week, weeksPerYear);
        return new Week(week, AetherhavenCalendar.Season.values()[ofYear / WEEKS_PER_SEASON], ofYear % WEEKS_PER_SEASON + 1,
            Math.floorDiv(week, weeksPerYear) + 1);
    }

    /** With few crops to choose from, at most half of them are favoured at once, so favour always means something. */
    public static int favouredCount(int growable, int allowed) {
        return growable < 1 ? 0 : Math.clamp(Math.min(allowed, growable / 2), 1, growable);
    }

    /**
     * The crops favoured in a week. The Farmer's crops take turns in their usual order, a few each week, so every
     * crop has its week and none is favoured twice before the others had theirs.
     *
     * @param growable the crops this farm's Farmer can grow, in their usual order
     * @param allowed how many crops the Almanac favours in a week
     */
    public static List<String> favoured(long week, List<String> growable, int allowed) {
        int count = favouredCount(growable.size(), allowed);
        var result = new ArrayList<String>(count);
        if (count == 0) return result;
        int start = (int) Math.floorMod(week * count, growable.size());
        for (int i = 0; i < count; i++) result.add(growable.get((start + i) % growable.size()));
        return result;
    }
}
