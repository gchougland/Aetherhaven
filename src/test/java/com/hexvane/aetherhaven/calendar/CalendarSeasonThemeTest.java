package com.hexvane.aetherhaven.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class CalendarSeasonThemeTest {
    @Test
    void eachSeasonHasDistinctAccentColors() {
        assertTrue(CalendarSeasonTheme.forSeason(Season.SPRING).accentBarColor().startsWith("#8EC4"));
        assertTrue(CalendarSeasonTheme.forSeason(Season.SUMMER).accentBarColor().startsWith("#E8C8"));
        assertTrue(CalendarSeasonTheme.forSeason(Season.AUTUMN).accentBarColor().startsWith("#E090"));
        assertTrue(CalendarSeasonTheme.forSeason(Season.WINTER).accentBarColor().startsWith("#8AB0"));
    }

    @Test
    void springThemeUsesGreenishPalette() {
        CalendarSeasonTheme spring = CalendarSeasonTheme.forSeason(Season.SPRING);
        assertEquals("#7AAB72", spring.borderColor());
        assertEquals("#DFF2D8", spring.headerTextColor());
        assertEquals("#DFF2D899", spring.flourishSoftColor());
        assertEquals("#8EC48773", spring.accentBarColor());
    }

    @Test
    void runtimeColorsUseHexNotUiAlphaSyntax() {
        CalendarSeasonTheme theme = CalendarSeasonTheme.forSeason(Season.SPRING);
        assertTrue(theme.borderColor().matches("#[0-9A-F]{6}"));
        assertTrue(theme.todayFillColor().matches("#[0-9A-F]{8}"));
    }
}
