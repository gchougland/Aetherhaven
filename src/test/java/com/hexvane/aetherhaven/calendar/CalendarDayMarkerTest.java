package com.hexvane.aetherhaven.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hexvane.aetherhaven.calendar.CalendarDayMarker.Kind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class CalendarDayMarkerTest {
    @Test
    void playerBirthdayWinsOverFestivalAndVillager() {
        assertEquals(Kind.PLAYER_BIRTHDAY, CalendarDayMarker.resolve(true, true, true));
        assertEquals(Kind.PLAYER_BIRTHDAY, CalendarDayMarker.resolve(true, false, true));
        assertEquals(Kind.FESTIVAL, CalendarDayMarker.resolve(false, true, true));
        assertEquals(Kind.VILLAGER, CalendarDayMarker.resolve(false, false, true));
        assertEquals(Kind.NONE, CalendarDayMarker.resolve(false, false, false));
    }

    @Test
    void playerBirthdayIconIsGiftBox() {
        assertEquals("UI/Custom/gift-box.png", PlayerBirthdayIds.CALENDAR_ICON);
    }
}
