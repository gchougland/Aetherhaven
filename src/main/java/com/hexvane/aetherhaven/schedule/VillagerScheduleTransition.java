package com.hexvane.aetherhaven.schedule;

import javax.annotation.Nullable;

/** One weekly transition: game time when {@link #location} becomes active until the next transition. */
public final class VillagerScheduleTransition {
    /** Optional stable id for crossmod schedule patches. */
    @Nullable
    private String id;

    /** {@link java.time.DayOfWeek} name (e.g. MONDAY) or 1–7 with Monday=1. */
    @Nullable
    private Object dayOfWeek;

    private int hour;
    private int minute;
    /** Symbol: home, work, inn, park, gaia_altar, shop, or a registered custom symbol (case-insensitive). */
    @Nullable
    private String location;

    @Nullable
    public String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    @Nullable
    public Object getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(@Nullable Object dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public int getMinute() {
        return minute;
    }

    public void setMinute(int minute) {
        this.minute = minute;
    }

    @Nullable
    public String getLocation() {
        return location;
    }

    public void setLocation(@Nullable String location) {
        this.location = location;
    }
}
