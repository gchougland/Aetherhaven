package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

/**
 * Decides whether a festival is running right now. A festival occupies one calendar day; within that day it is either
 * all day or a start-to-end clock window. When the end time is at or before the start time the window wraps past
 * midnight and stays open into the small hours of the following day.
 */
public final class FestivalWindow {
    private FestivalWindow() {}

    /** True when {@code gameTime} falls on the festival's calendar day (ignores the clock window). */
    public static boolean isFestivalDay(@Nonnull FestivalDefinition def, @Nonnull LocalDateTime gameTime) {
        AetherhavenCalendar.CalendarDate date = AetherhavenCalendar.from(gameTime);
        return date.season() == def.getSeason() && date.dayOfSeason() == def.getDayOfSeason();
    }

    /** True when the festival should be running at {@code gameTime}. */
    public static boolean isActive(@Nonnull FestivalDefinition def, @Nonnull LocalDateTime gameTime) {
        int minuteOfDay = gameTime.getHour() * 60 + gameTime.getMinute();
        if (def.isAllDay()) {
            return isFestivalDay(def, gameTime);
        }
        int start = def.startMinuteOfDay();
        int end = def.endMinuteOfDay();
        if (end > start) {
            return isFestivalDay(def, gameTime) && minuteOfDay >= start && minuteOfDay < end;
        }
        // Overnight: open on the festival day from the start time, and on the next day until the end time.
        if (isFestivalDay(def, gameTime)) {
            return minuteOfDay >= start;
        }
        return isFestivalDay(def, gameTime.minusDays(1)) && minuteOfDay < end;
    }

    /**
     * Wall clock minute of the day at which a running festival closes. Callers pair this with the day so a restart
     * mid festival still knows when to swap the base prefab back.
     */
    public static int closingMinuteOfDay(@Nonnull FestivalDefinition def) {
        return def.isAllDay() ? 24 * 60 : def.endMinuteOfDay();
    }
}
