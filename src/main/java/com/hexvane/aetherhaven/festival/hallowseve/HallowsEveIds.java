package com.hexvane.aetherhaven.festival.hallowseve;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared ids and constants for the Hallow's Eve maze festival. */
public final class HallowsEveIds {
    public static final String FESTIVAL_ID = "hallows_eve";
    public static final String MECHANIC_ID = "hallows_eve";

    public static final String AUTUMN_TICKET_ITEM_ID = "Aetherhaven_Festival_Ticket_Autumn";
    public static final String CANDY_ITEM_ID = "Aetherhaven_Spooky_Candy";
    public static final String ICE_ESSENCE_ITEM_ID = "Ingredient_Ice_Essence";

    public static final String MERCHANT_NPC_ROLE = "Aetherhaven_Festival_Hallows_Eve_Merchant";
    public static final String PUMPKIN_NPC_ROLE = "Aetherhaven_Festival_Jack_Lantern";
    public static final String PUMPKIN_MODEL_ASSET_ID = "Aetherhaven_Festival_Jack_Lantern";
    public static final String ORB_NPC_ROLE = "Aetherhaven_Festival_Maze_Orb";

    public static final String HUD_KEY = "AetherhavenHallowsEveMaze";
    public static final String ROOT_INTERACTION_BURST = "Aetherhaven_Festival_Jack_Lantern_Burst_Root";
    public static final String INTERACTION_HINT =
        "aetherhaven_festivals.aetherhaven.festival.interactionHints.burstJackLantern";

    public static final String COLLECT_SOUND = "Aetherhaven_Festival_Hallows_Eve_Orb";

    public static final int DEFAULT_TOURIST_SPOTS = 8;
    public static final int DEFAULT_ORB_SPAWNS = 8;

    public static final long COUNTDOWN_MS = 3_000L;
    public static final long RACE_MS = 30_000L;

    public static final double ORB_COLLECT_RADIUS_XZ = 1.5;
    public static final double ORB_COLLECT_RADIUS_Y = 1.25;
    public static final double PUMPKIN_SPAWN_Y_OFFSET = 0.5;

    public static final float PUMPKIN_MIN_SCALE = 1.2f;
    public static final float PUMPKIN_MAX_SCALE = 4.0f;

    private HallowsEveIds() {}

    public static boolean isIceEssenceMarker(@Nullable String itemId) {
        return itemId != null && itemId.trim().toLowerCase(Locale.ROOT).contains("ice_essence");
    }

    @Nonnull
    public static String formatCountdown(int secondsLeft) {
        if (secondsLeft <= 0) {
            return "Go!";
        }
        return String.valueOf(secondsLeft);
    }

    @Nonnull
    public static String formatRaceTime(long remainingMs) {
        long clamped = Math.max(0L, remainingMs);
        int totalSeconds = (int) Math.ceil(clamped / 1000.0);
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%d:%02d", mins, secs);
    }
}
