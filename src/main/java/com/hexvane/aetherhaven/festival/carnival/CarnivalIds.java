package com.hexvane.aetherhaven.festival.carnival;

/** Shared ids for the Carnival Festival mechanic. */
public final class CarnivalIds {
    public static final String FESTIVAL_ID = "carnival";
    public static final String MECHANIC_ID = "carnival";

    public static final String SUMMER_TICKET_ITEM_ID = "Aetherhaven_Festival_Ticket_Summer";
    public static final String DART_ITEM_ID = "Weapon_Dart_Tribal";
    /** Carnival-only club; single-target swings so one hit cannot splash multiple goblins. */
    public static final String CLUB_ITEM_ID = "Aetherhaven_Carnival_Goblin_Whacker";
    /** Perfect clear unlock pattern for the town wardrobe (Goblin Lobber backpack). */
    public static final String WHACK_PERFECT_COSMETIC_ITEM_ID = "Aetherhaven_Villager_Cosmetic_Goblin_Lobber_Backpack";
    public static final String WHACK_PERFECT_COSMETIC_ID = "GoblinLobberBackpack";
    /**
     * Balloon hat wardrobe unlocks. Texture file names do not match display colors:
     * Floating_Gift = red, Floating_Gift_Green = yellow, Floating_Gift_Red = blue.
     */
    public static final String[] BALLOON_HAT_COSMETIC_IDS = {
        "BalloonHat.Red",
        "BalloonHat.Yellow",
        "BalloonHat.Blue"
    };
    public static final String[] BALLOON_HAT_COSMETIC_ITEM_IDS = {
        "Aetherhaven_Villager_Cosmetic_Balloon_Hat_Red",
        "Aetherhaven_Villager_Cosmetic_Balloon_Hat_Yellow",
        "Aetherhaven_Villager_Cosmetic_Balloon_Hat_Blue"
    };
    public static final String WHEEL_BLOCK_ID = "Aetherhaven_Carnival_Wheel";

    public static final String BALLOON_NPC_ROLE = "Aetherhaven_Festival_Carnival_Balloon";
    public static final String WHEEL_NPC_ROLE = "Aetherhaven_Festival_Carnival_Wheel";
    public static final String WHACK_NPC_ROLE = "Aetherhaven_Festival_Carnival_Whack";
    public static final String MERCHANT_NPC_ROLE = "Aetherhaven_Festival_Carnival_Merchant";

    public static final String WHEEL_FACE_MODEL = "Aetherhaven_Carnival_Wheel_Face";
    public static final String WHACK_GOBLIN_MODEL = "Aetherhaven_Festival_Carnival_Whack_Goblin";

    public static final String[] BALLOON_MODELS = {
        "Aetherhaven_Festival_Balloon",
        "Aetherhaven_Festival_Balloon_Green",
        "Aetherhaven_Festival_Balloon_Red"
    };

    public static final int GAME_COST_GOLD = 10;
    public static final int BALLOON_DARTS = 10;
    public static final int BALLOON_TOTAL = 10;
    public static final int WHEEL_WIN_TICKETS = 2;

    public static final float BALLOON_FLOAT_SECONDS = 4.5f;
    public static final float BALLOON_RISE_SPEED = 0.675f;
    /** Delay between each balloon appearing during a game. */
    public static final float BALLOON_SPAWN_INTERVAL = 1.0f;
    public static final double BALLOON_HIT_PAD = 0.35;

    public static final float WHEEL_SPIN_SECONDS = 5.5f;
    public static final float WHEEL_IDLE_OFFSET_RAD = (float) (Math.PI / 8.0);
    public static final float WHEEL_TICK_SFX_INTERVAL = 0.12f;
    /** Entity face is authored like the block static face but needs 2x world scale to match. */
    public static final float WHEEL_FACE_SCALE = 2.0f;

    public static final float WHACK_DURATION_SECONDS = 60f;
    /** Pause after the player starts a round before the first goblin can pop up. */
    public static final float WHACK_START_DELAY_SECONDS = 2.0f;
    public static final int WHACK_TOTAL_POPS = 20;
    public static final int WHACK_MAX_UP = 2;
    /** How long a goblin stays fully up before retracting if missed. */
    public static final float WHACK_UP_SECONDS = 1.15f;
    public static final float WHACK_RISE_SECONDS = 0.15f;
    public static final float WHACK_RETRACT_SECONDS = 0.15f;
    /**
     * Delay between spawn attempts. Kept below {@link #WHACK_UP_SECONDS} so a second goblin can overlap while the
     * first is still up (harder cadence, still capped by {@link #WHACK_MAX_UP}).
     */
    public static final float WHACK_SPAWN_INTERVAL = 0.75f;
    /** How far the goblin rises from its buried pose (half a block). */
    public static final double WHACK_POP_HEIGHT = 0.5;
    public static final float WHACK_MODEL_SCALE = 0.5f;
    /**
     * Engine selectors can fork one target per tick across the swing window; this lock enforces a single scored hit
     * per club swing.
     */
    public static final float WHACK_HIT_LOCK_SECONDS = 0.4f;
    public static final String SOUND_GOBLIN_HURT = "SFX_Goblin_Hurt";
    public static final String SOUND_GOBLIN_ALERT = "SFX_Goblin_Alerted";

    private CarnivalIds() {}

    public static int balloonTicketReward(int popped) {
        if (popped >= 10) {
            return 8;
        }
        if (popped >= 8) {
            return 4;
        }
        if (popped >= 5) {
            return 2;
        }
        if (popped >= 3) {
            return 1;
        }
        return 0;
    }

    /**
     * Below half hits: 0. At half: 1. At all: 5. Steps evenly between.
     */
    public static int whackTicketReward(int hits, int total) {
        if (total <= 0 || hits <= 0) {
            return 0;
        }
        int half = (total + 1) / 2;
        if (hits < half) {
            return 0;
        }
        if (hits >= total) {
            return 5;
        }
        float t = (hits - half) / (float) Math.max(1, total - half);
        return 1 + Math.round(t * 4f);
    }
}
