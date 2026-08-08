package com.hexvane.aetherhaven.calendar;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Maps Aetherhaven calendar season days to villagers with configured birthdays. */
public final class VillagerBirthdayIndex {
    private final Map<Season, List<VillagerDefinition>[]> bySeasonDay;

    private VillagerBirthdayIndex(@Nonnull Map<Season, List<VillagerDefinition>[]> bySeasonDay) {
        this.bySeasonDay = bySeasonDay;
    }

    @Nonnull
    public static VillagerBirthdayIndex fromCatalog(@Nonnull VillagerDefinitionCatalog catalog) {
        Map<Season, List<VillagerDefinition>[]> map = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            @SuppressWarnings("unchecked")
            List<VillagerDefinition>[] days = new List[AetherhavenCalendar.DAYS_PER_SEASON];
            map.put(season, days);
        }
        for (VillagerDefinition def : catalog.allByNpcRoleId().values()) {
            AetherhavenCalendar.Season season = def.getBirthdaySeasonOrNull();
            Integer day = def.getBirthdayDayOrNull();
            if (season == null || day == null) {
                continue;
            }
            List<VillagerDefinition>[] days = map.get(season);
            if (days[day - 1] == null) {
                days[day - 1] = new ArrayList<>();
            }
            days[day - 1].add(def);
        }
        for (Season season : Season.values()) {
            List<VillagerDefinition>[] days = map.get(season);
            for (int i = 0; i < days.length; i++) {
                if (days[i] != null) {
                    days[i] = Collections.unmodifiableList(days[i]);
                } else {
                    days[i] = List.of();
                }
            }
        }
        return new VillagerBirthdayIndex(map);
    }

    @Nonnull
    public List<VillagerDefinition> birthdaysOn(@Nonnull Season season, int dayOfSeason) {
        if (dayOfSeason < 1 || dayOfSeason > AetherhavenCalendar.DAYS_PER_SEASON) {
            return List.of();
        }
        List<VillagerDefinition>[] days = bySeasonDay.get(season);
        if (days == null) {
            return List.of();
        }
        return days[dayOfSeason - 1];
    }

    @Nonnull
    public List<VillagerDefinition> birthdaysOn(@Nonnull AetherhavenCalendar.CalendarDate date) {
        return birthdaysOn(date.season(), date.dayOfSeason());
    }
}
