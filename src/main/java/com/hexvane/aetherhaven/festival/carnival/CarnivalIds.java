package com.hexvane.aetherhaven.festival.carnival;

/** Shared ids for the Carnival Festival mechanic. */
public final class CarnivalIds {
    public static final String FESTIVAL_ID = "carnival";
    public static final String MECHANIC_ID = "carnival";

    public static final String SUMMER_TICKET_ITEM_ID = "Aetherhaven_Festival_Ticket_Summer";
    public static final String DART_ITEM_ID = "Weapon_Dart_Tribal";
    public static final String WHEEL_BLOCK_ID = "Aetherhaven_Carnival_Wheel";

    public static final String BALLOON_NPC_ROLE = "Aetherhaven_Festival_Carnival_Balloon";
    public static final String WHEEL_NPC_ROLE = "Aetherhaven_Festival_Carnival_Wheel";
    public static final String MERCHANT_NPC_ROLE = "Aetherhaven_Festival_Carnival_Merchant";

    public static final String WHEEL_FACE_MODEL = "Aetherhaven_Carnival_Wheel_Face";

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
}
