package com.hexvane.aetherhaven.calendar;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.CalendarDate;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Birthday date checks and gift eligibility for players. */
public final class PlayerBirthdayService {
    private PlayerBirthdayService() {}

    public static boolean hasBirthday(@Nullable PlayerTownJournalState state) {
        return state != null && state.hasBirthday();
    }

    public static boolean isBirthdayToday(@Nullable PlayerTownJournalState state, @Nonnull CalendarDate today) {
        return matches(state, today.season(), today.dayOfSeason());
    }

    public static boolean matches(
        @Nullable PlayerTownJournalState state,
        @Nonnull Season season,
        int dayOfSeason
    ) {
        return hasBirthday(state) && state.matchesBirthday(season, dayOfSeason);
    }

    public static boolean matches(
        @Nullable String seasonName,
        int dayOfSeason,
        @Nonnull Season todaySeason,
        int todayDay
    ) {
        Season season = AetherhavenCalendar.parseSeason(seasonName);
        if (season == null || dayOfSeason < 1 || dayOfSeason > AetherhavenCalendar.DAYS_PER_SEASON) {
            return false;
        }
        return season == todaySeason && dayOfSeason == todayDay;
    }

    public static boolean alreadyGiftedThisYear(
        long storedYear,
        @Nonnull Set<UUID> giftedVillagerUuids,
        long year,
        @Nonnull UUID villagerUuid
    ) {
        if (storedYear != year) {
            return false;
        }
        return giftedVillagerUuids.contains(villagerUuid);
    }

    public static boolean alreadyGiftedThisYear(
        @Nullable PlayerTownJournalState state,
        long year,
        @Nonnull UUID villagerUuid
    ) {
        if (state == null) {
            return false;
        }
        return alreadyGiftedThisYear(
            state.getBirthdayGiftYear(),
            state.getBirthdayGiftedVillagerUuids(),
            year,
            villagerUuid
        );
    }

    public static boolean isMaxedFriendship(
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull UUID villagerUuid
    ) {
        return VillagerReputationService.peekReputation(town, playerUuid, villagerUuid)
            >= VillagerReputationService.MAX_REPUTATION;
    }

    public static boolean isGiftableKind(@Nullable String kind) {
        if (kind == null || kind.isBlank()) {
            return false;
        }
        String trimmed = kind.trim();
        return !TownVillagerBinding.isVisitorKind(trimmed)
            && !TownVillagerBinding.isRescueKind(trimmed)
            && !TownVillagerBinding.KIND_GUARD.equals(trimmed)
            && !TownVillagerBinding.KIND_TOWNSFOLK.equals(trimmed);
    }

    public static int parseDay(@Nullable String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int day = Integer.parseInt(raw.trim());
            if (day < 1 || day > AetherhavenCalendar.DAYS_PER_SEASON) {
                return fallback;
            }
            return day;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    public static Season parseSeason(@Nullable String raw, @Nullable Season fallback) {
        Season parsed = AetherhavenCalendar.parseSeason(raw);
        return parsed != null ? parsed : fallback;
    }

    @Nonnull
    public static String seasonValue(@Nonnull Season season) {
        return season.name().toLowerCase(Locale.ROOT);
    }
}
