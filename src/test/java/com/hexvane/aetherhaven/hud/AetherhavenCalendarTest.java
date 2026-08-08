package com.hexvane.aetherhaven.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class AetherhavenCalendarTest {
    @Test
    void epochIsFirstDayOfSpringYearOne() {
        LocalDateTime epoch = LocalDateTime.of(1, 1, 1, 0, 0);

        assertEquals("Spring 1, Year 1", AetherhavenCalendar.formatDate(epoch));
    }

    @Test
    void mapsEverySeasonBoundaryAndNextYear() {
        LocalDateTime epoch = LocalDateTime.of(1, 1, 1, 8, 0);

        assertEquals("Spring 28, Year 1", AetherhavenCalendar.formatDate(epoch.plusDays(27)));
        assertEquals("Summer 1, Year 1", AetherhavenCalendar.formatDate(epoch.plusDays(28)));
        assertEquals("Autumn 1, Year 1", AetherhavenCalendar.formatDate(epoch.plusDays(56)));
        assertEquals("Winter 1, Year 1", AetherhavenCalendar.formatDate(epoch.plusDays(84)));
        assertEquals("Winter 28, Year 1", AetherhavenCalendar.formatDate(epoch.plusDays(111)));
        assertEquals("Spring 1, Year 2", AetherhavenCalendar.formatDate(epoch.plusDays(112)));
    }

    @Test
    void formatsLowercaseTwelveHourClock() {
        assertEquals("12:00 am", AetherhavenCalendar.formatClock(LocalDateTime.of(1, 1, 1, 0, 0)));
        assertEquals("9:07 am", AetherhavenCalendar.formatClock(LocalDateTime.of(1, 1, 1, 9, 7)));
        assertEquals("12:00 pm", AetherhavenCalendar.formatClock(LocalDateTime.of(1, 1, 1, 12, 0)));
        assertEquals("11:59 pm", AetherhavenCalendar.formatClock(LocalDateTime.of(1, 1, 1, 23, 59)));
    }

    @Test
    void toLocalDateTimeRoundTripsEverySeasonBoundary() {
        assertEquals(
            new AetherhavenCalendar.CalendarDate(Season.SPRING, 1, 1L),
            AetherhavenCalendar.from(AetherhavenCalendar.toLocalDateTime(Season.SPRING, 1, 1L))
        );
        assertEquals(
            new AetherhavenCalendar.CalendarDate(Season.SPRING, 28, 1L),
            AetherhavenCalendar.from(AetherhavenCalendar.toLocalDateTime(Season.SPRING, 28, 1L))
        );
        assertEquals(
            new AetherhavenCalendar.CalendarDate(Season.SUMMER, 1, 1L),
            AetherhavenCalendar.from(AetherhavenCalendar.toLocalDateTime(Season.SUMMER, 1, 1L))
        );
        assertEquals(
            new AetherhavenCalendar.CalendarDate(Season.WINTER, 28, 2L),
            AetherhavenCalendar.from(AetherhavenCalendar.toLocalDateTime(Season.WINTER, 28, 2L))
        );
        assertEquals(
            new AetherhavenCalendar.CalendarDate(Season.SPRING, 1, 3L),
            AetherhavenCalendar.from(AetherhavenCalendar.toLocalDateTime(Season.SPRING, 1, 3L))
        );
    }

    @Test
    void parseSeasonAcceptsDisplayNameOrEnum() {
        assertEquals(Season.AUTUMN, AetherhavenCalendar.parseSeason("Autumn"));
        assertEquals(Season.WINTER, AetherhavenCalendar.parseSeason("WINTER"));
        assertNull(AetherhavenCalendar.parseSeason("March"));
    }

    @Test
    void formatSeasonHeader() {
        assertEquals(
            "Summer, Year 2",
            AetherhavenCalendar.formatSeasonHeader(new AetherhavenCalendar.CalendarDate(Season.SUMMER, 14, 2L))
        );
    }
}
