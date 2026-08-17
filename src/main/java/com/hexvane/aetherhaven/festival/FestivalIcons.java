package com.hexvane.aetherhaven.festival;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Common asset icon paths used by festival UI. */
public final class FestivalIcons {
    /** Calendar marker used when a festival JSON does not name its own icon. */
    public static final String DEFAULT_CALENDAR_ICON = "Icons/ItemsGenerated/Furniture_Flag_Orange.png";

    /** Flat snowflake used by the Snowball Throwing Festival. */
    public static final String WINTER = "UI/Custom/winter.png";

    private FestivalIcons() {}

    @Nonnull
    public static String resolveCalendarIcon(@Nullable String calendarIconPath, @Nullable String mechanicId) {
        String path = calendarIconPath != null ? calendarIconPath.trim() : "";
        if ("UI/Custom/snowflake.png".equalsIgnoreCase(path)) {
            return WINTER;
        }
        if (!path.isBlank() && !DEFAULT_CALENDAR_ICON.equals(path)) {
            return path;
        }
        if (mechanicId != null && "snowball".equalsIgnoreCase(mechanicId.trim())) {
            return WINTER;
        }
        return path.isBlank() ? DEFAULT_CALENDAR_ICON : path;
    }
}
