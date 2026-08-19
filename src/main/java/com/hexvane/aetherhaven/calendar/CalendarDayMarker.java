package com.hexvane.aetherhaven.calendar;

import javax.annotation.Nonnull;

/** Which icon a calendar day cell should show. */
public final class CalendarDayMarker {
    public enum Kind {
        PLAYER_BIRTHDAY,
        FESTIVAL,
        VILLAGER,
        NONE
    }

    private CalendarDayMarker() {}

    @Nonnull
    public static Kind resolve(boolean playerBirthday, boolean festival, boolean villagerBirthday) {
        if (playerBirthday) {
            return Kind.PLAYER_BIRTHDAY;
        }
        if (festival) {
            return Kind.FESTIVAL;
        }
        if (villagerBirthday) {
            return Kind.VILLAGER;
        }
        return Kind.NONE;
    }
}
