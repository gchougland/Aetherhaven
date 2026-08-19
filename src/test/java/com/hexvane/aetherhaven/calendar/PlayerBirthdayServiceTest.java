package com.hexvane.aetherhaven.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class PlayerBirthdayServiceTest {
    @Test
    void matchesSeasonAndDayAndIgnoresYear() {
        assertTrue(PlayerBirthdayService.matches("Summer", 12, Season.SUMMER, 12));
        assertTrue(PlayerBirthdayService.matches("summer", 12, Season.SUMMER, 12));
        assertFalse(PlayerBirthdayService.matches("Summer", 12, Season.SPRING, 12));
        assertFalse(PlayerBirthdayService.matches("Summer", 11, Season.SUMMER, 12));
        assertFalse(PlayerBirthdayService.matches(null, 12, Season.SUMMER, 12));
        assertFalse(PlayerBirthdayService.matches("Summer", 0, Season.SUMMER, 12));
    }

    @Test
    void giftedThisYearClearsOnYearRolloverAndBlocksRepeats() {
        UUID villager = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Set<UUID> gifted = new LinkedHashSet<>();
        gifted.add(villager);

        assertTrue(PlayerBirthdayService.alreadyGiftedThisYear(3L, gifted, 3L, villager));
        assertFalse(PlayerBirthdayService.alreadyGiftedThisYear(3L, gifted, 4L, villager));
        assertFalse(
            PlayerBirthdayService.alreadyGiftedThisYear(
                3L,
                gifted,
                3L,
                UUID.fromString("22222222-2222-2222-2222-222222222222")
            )
        );
    }

    @Test
    void parseDayClampsToSeasonLength() {
        assertEquals(7, PlayerBirthdayService.parseDay("7", 1));
        assertEquals(1, PlayerBirthdayService.parseDay("0", 1));
        assertEquals(1, PlayerBirthdayService.parseDay("29", 1));
        assertEquals(4, PlayerBirthdayService.parseDay("nope", 4));
    }
}
