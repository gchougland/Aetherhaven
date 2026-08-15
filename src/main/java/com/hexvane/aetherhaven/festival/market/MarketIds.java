package com.hexvane.aetherhaven.festival.market;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared ids and constants for the Market Festival. */
public final class MarketIds {
    public static final String FESTIVAL_ID = "market";
    public static final String MECHANIC_ID = "market";

    public static final String AUTUMN_TICKET_ITEM_ID = "Aetherhaven_Festival_Ticket_Autumn";
    public static final String CORIN_PLUSHIE_ITEM_ID = "Aetherhaven_Corin_Plushie";
    public static final String HEARTBERRY_ITEM_ID = "Aetherhaven_Heartberry";

    public static final String KIND_MARKET_SHOP = "market_shop";
    public static final String STAND_KIND_PREFIX = "market_stand_";

    public static final String ROOT_INTERACTION_STALL = "Aetherhaven_Festival_Market_Stall_Root";
    public static final String INTERACTION_HINT =
        "aetherhaven_festivals.aetherhaven.festival.interactionHints.openMarketStall";

    public static final int SLOT_COUNT = 9;
    public static final int STAND_COUNT = 4;
    public static final int SHOP_SPOT_COUNT = 3;
    public static final int DEFAULT_TOURIST_SPOTS = 8;
    public static final int DEFAULT_DISPLAY_SLOTS = SLOT_COUNT;
    public static final int DEFAULT_STANDS = STAND_COUNT;

    public static final int CATEGORY_BONUS = 10;
    public static final int UNLISTED_POINTS = 1;
    public static final String UNLISTED_CATEGORY = "other";

    public static final int RIVAL_BRAMBLEFORD = 45;
    public static final int RIVAL_MILLSHADE = 85;
    public static final int RIVAL_GOLDHOLLOW = 125;

    public static final String RIVAL_BRAMBLEFORD_ID = "brambleford";
    public static final String RIVAL_MILLSHADE_ID = "millshade";
    public static final String RIVAL_GOLDHOLLOW_ID = "goldhollow";

    public static final long PONDER_MS = 4_000L;
    /** Ticket stall park distance (Seek leash is 0.5). */
    public static final double STALL_ARRIVED_DIST_SQ = 0.75 * 0.75;

    private MarketIds() {}

    @Nonnull
    public static String standKind(int index) {
        return STAND_KIND_PREFIX + Math.max(0, index);
    }

    public static boolean isStandKind(@Nullable String kind) {
        return kind != null && kind.trim().toLowerCase(Locale.ROOT).startsWith(STAND_KIND_PREFIX);
    }

    public static boolean isMarketShopKind(@Nullable String kind) {
        return kind != null && KIND_MARKET_SHOP.equalsIgnoreCase(kind.trim());
    }

    @Nonnull
    public static String shopIdForKind(@Nullable String kind) {
        String key = kind != null ? kind.trim().toLowerCase(Locale.ROOT) : "";
        return switch (key) {
            case "farmer" -> "Aetherhaven_Festival_Market_Farmer";
            case "florist" -> "Aetherhaven_Festival_Market_Florist";
            case "blacksmith" -> "Aetherhaven_Festival_Market_Blacksmith";
            case "chef" -> "Aetherhaven_Festival_Market_Chef";
            case "miner" -> "Aetherhaven_Festival_Market_Miner";
            case "logger" -> "Aetherhaven_Festival_Market_Logger";
            case "rancher" -> "Aetherhaven_Festival_Market_Rancher";
            case "innkeeper" -> "Aetherhaven_Festival_Market_Innkeeper";
            case "merchant" -> "Aetherhaven_Festival_Market_Merchant";
            case "priestess" -> "Aetherhaven_Festival_Market_Priestess";
            case "builder" -> "Aetherhaven_Festival_Market_Builder";
            case "bard" -> "Aetherhaven_Festival_Market_Bard";
            case "crystal_keeper" -> "Aetherhaven_Festival_Market_Crystal_Keeper";
            case "pyrotechnic" -> "Aetherhaven_Festival_Market_Pyrotechnic";
            default -> "";
        };
    }

    public static boolean hasShopTable(@Nullable String kind) {
        return !shopIdForKind(kind).isEmpty();
    }
}
