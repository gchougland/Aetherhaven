package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Season day lookup for the wall calendar and the daily festival check. */
public final class FestivalCalendarIndex {
    private final Map<Season, FestivalDefinition[]> bySeason;

    private FestivalCalendarIndex(@Nonnull Map<Season, FestivalDefinition[]> bySeason) {
        this.bySeason = bySeason;
    }

    @Nonnull
    public static FestivalCalendarIndex fromCatalog(@Nonnull FestivalCatalog catalog) {
        Map<Season, FestivalDefinition[]> map = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            map.put(season, new FestivalDefinition[AetherhavenCalendar.DAYS_PER_SEASON]);
        }
        for (FestivalDefinition def : catalog.list()) {
            FestivalDefinition[] days = map.get(def.getSeason());
            int slot = def.getDayOfSeason() - 1;
            if (days != null && slot >= 0 && slot < days.length && days[slot] == null) {
                days[slot] = def;
            }
        }
        return new FestivalCalendarIndex(map);
    }

    @Nullable
    public FestivalDefinition festivalOn(@Nonnull Season season, int dayOfSeason) {
        FestivalDefinition[] days = bySeason.get(season);
        if (days == null || dayOfSeason < 1 || dayOfSeason > days.length) {
            return null;
        }
        return days[dayOfSeason - 1];
    }

    @Nullable
    public FestivalDefinition festivalOn(@Nonnull LocalDateTime gameTime) {
        AetherhavenCalendar.CalendarDate date = AetherhavenCalendar.from(gameTime);
        return festivalOn(date.season(), date.dayOfSeason());
    }
}
