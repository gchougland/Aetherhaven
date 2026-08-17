package com.hexvane.aetherhaven.festival.wintertide;

import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import com.hexvane.aetherhaven.villager.gift.VillagerGiftRules;
import javax.annotation.Nonnull;

/** Shared ids and constants for the Wintertide gift exchange. */
public final class WintertideIds {
    public static final String FESTIVAL_ID = "wintertide";
    public static final String MECHANIC_ID = "wintertide";

    public static final String WINTER_TICKET_ITEM_ID = "Aetherhaven_Festival_Ticket_Winter";
    public static final String MERCHANT_NPC_ROLE = "Aetherhaven_Festival_Wintertide_Merchant";
    public static final String MERCHANT_MODEL = "Aetherhaven_Festival_Wintertide_Merchant";
    public static final String SHOP_ID = "Aetherhaven_Festival_Wintertide";

    public static final String DIALOGUE_MERCHANT = "aetherhaven_festival_wintertide_merchant";
    public static final String DIALOGUE_GIFT_REACTION = "aetherhaven_festival_wintertide_gift";
    public static final String DIALOGUE_INCOMING = "aetherhaven_festival_wintertide_incoming";
    public static final String DIALOGUE_PLAYER_RATE = "aetherhaven_festival_wintertide_player_gift";

    public static final String ROOT_INTERACTION_PLAYER_GIFT = "Aetherhaven_Festival_Wintertide_Player_Gift_Root";
    public static final String INTERACTION_HINT =
        "aetherhaven_festivals.aetherhaven.festival.interactionHints.wintertideGift";

    public static final int DEFAULT_TOURIST_SPOTS = 8;
    public static final double SEEK_STAND_DISTANCE = 2.0;
    public static final double SEEK_ARRIVED_DIST_SQ = 2.4 * 2.4;

    private WintertideIds() {}

    public static int ticketCount(@Nonnull GiftPreference preference) {
        return switch (preference) {
            case DISLIKE -> 1;
            case NEUTRAL -> 3;
            case LIKE -> 5;
            case LOVE -> 10;
        };
    }

    /** Friendship from a Wintertide villager gift. Liked gifts count twice as much as a regular gift. */
    public static int reputationDelta(@Nonnull GiftPreference preference) {
        int delta = VillagerGiftRules.reputationDelta(preference);
        return delta > 0 ? delta * 2 : delta;
    }
}
