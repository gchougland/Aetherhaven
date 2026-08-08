package com.hexvane.aetherhaven.calendar;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import java.time.LocalDateTime;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Birthday checks and lookups for villager calendar events. */
public final class VillagerBirthdayService {
    private VillagerBirthdayService() {}

    public static boolean isBirthdayToday(@Nullable VillagerDefinition def, @Nonnull LocalDateTime gameTime) {
        if (def == null || !def.hasBirthday()) {
            return false;
        }
        return def.matchesBirthday(AetherhavenCalendar.from(gameTime));
    }

    @Nonnull
    public static List<VillagerDefinition> birthdaysOn(
        @Nonnull VillagerDefinitionCatalog catalog,
        @Nonnull AetherhavenCalendar.Season season,
        int dayOfSeason
    ) {
        return VillagerBirthdayIndex.fromCatalog(catalog).birthdaysOn(season, dayOfSeason);
    }

    @Nonnull
    public static List<VillagerDefinition> birthdaysOn(
        @Nonnull VillagerBirthdayIndex index,
        @Nonnull AetherhavenCalendar.Season season,
        int dayOfSeason
    ) {
        return index.birthdaysOn(season, dayOfSeason);
    }

    @Nullable
    public static String birthdayReactionNodeId(@Nonnull String baseNodeId, boolean birthdayToday) {
        if (!birthdayToday || baseNodeId.isBlank()) {
            return baseNodeId;
        }
        return baseNodeId + "_birthday";
    }
}
