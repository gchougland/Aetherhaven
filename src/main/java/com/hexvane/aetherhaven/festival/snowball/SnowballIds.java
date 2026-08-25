package com.hexvane.aetherhaven.festival.snowball;

import javax.annotation.Nonnull;

/** Shared ids and constants for the Snowball Throwing Festival. */
public final class SnowballIds {
    public static final String FESTIVAL_ID = "snowball";
    public static final String MECHANIC_ID = "snowball";

    public static final String WINTER_TICKET_ITEM_ID = "Aetherhaven_Festival_Ticket_Winter";
    public static final String SNOWBALL_ITEM_ID = "Aetherhaven_Snowball";
    public static final String PILE_BLOCK_ID = "Aetherhaven_Snowball_Pile";
    public static final String PROJECTILE_CONFIG_ID = "Projectile_Config_Aetherhaven_Snowball";
    public static final String PROJECTILE_MODEL_ID = "Aetherhaven_Snowball_Projectile";

    public static final String MERCHANT_NPC_ROLE = "Aetherhaven_Festival_Snowball_Merchant";
    public static final String MERCHANT_MODEL = "Aetherhaven_Festival_Snowball_Merchant";
    public static final String SHOP_ID = "Aetherhaven_Festival_Snowball";
    public static final String DIALOGUE_MERCHANT = "aetherhaven_festival_snowball_merchant";

    public static final String INTERACTION_HINT =
        "aetherhaven_festivals.aetherhaven.festival.interactionHints.takeSnowballs";

    public static final int MAX_PLAYERS = 8;
    public static final int TEAM_SIZE = 4;
    public static final int TEAM_COUNT = 2;
    public static final int LIVES = 3;
    public static final int PILE_SNOWBALLS = 3;
    public static final int WIN_TICKETS = 5;
    public static final int DEFAULT_TOURIST_SPOTS = 8;
    public static final int DEFAULT_PILE_SPOTS = 8;

    public static final long FIGHT_DURATION_MS = 4L * 60L * 1000L;
    public static final long PILE_RESPAWN_MS = 10_000L;
    public static final long RESULTS_HOLD_MS = 4_000L;

    public static final long VILLAGER_CROUCH_MIN_MS = 4_000L;
    public static final long VILLAGER_CROUCH_MAX_MS = 10_000L;
    public static final long VILLAGER_STAND_BEFORE_THROW_MS = 1_000L;
    public static final long VILLAGER_STAND_AFTER_THROW_MS = 1_000L;

    /** Extra reach around a fighter so a snowball that clips the body still counts. */
    public static final double HIT_PAD_BLOCKS = 0.85;
    /** Used when a snowball has no bounding box, and as the swept-path half size. */
    public static final double HIT_PROJECTILE_RADIUS = 0.3;
    /** Slack on the spatial lookup around a snowball, so nobody standing at the edge is missed. */
    public static final double HIT_TARGET_SEARCH_BLOCKS = 4.0;
    public static final int PLACE_SETTINGS = 10;

    public static final String HUD_KEY = "AetherhavenSnowballFight";
    public static final String HUD_SNOWFLAKE_ICON = "UI/Custom/winter.png";
    public static final String HIT_PARTICLE = "Effect_Snow_Impact";
    public static final String HIT_SOUND = "SFX_Snow_Hit";
    public static final String HIT_NOTIFY_ICON = "Icons/ItemsGenerated/Aetherhaven_Snowball.png";

    public enum Team {
        A,
        B;

        @Nonnull
        public String langKey() {
            return this == A ? "red" : "blue";
        }

        /** Swatch colour used by the roster overlay so teams read at a glance. */
        @Nonnull
        public String rosterColor() {
            return this == A ? "#e06a6a" : "#6aa8f0";
        }
    }

    private SnowballIds() {}
}
