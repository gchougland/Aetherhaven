package com.hexvane.aetherhaven.festival.treeclimb;

import javax.annotation.Nonnull;

/** Shared ids and constants for the Tree Climbing Festival mechanic. */
public final class TreeClimbIds {
    public static final String FESTIVAL_ID = "tree_climbing";
    public static final String MECHANIC_ID = "tree_climbing";

    public static final String SUMMER_TICKET_ITEM_ID = "Aetherhaven_Festival_Ticket_Summer";

    public static final String MERCHANT_NPC_ROLE = "Aetherhaven_Festival_Tree_Climb_Merchant";
    public static final String ATTENDANT_NPC_ROLE = "Aetherhaven_Festival_Tree_Climb_Attendant";

    /** Racer limit when the festival JSON does not set {@code maxRacers}. */
    public static final int DEFAULT_MAX_RACERS = 12;
    /** Start pads a freshly authored tree climb square gets; extra racers share the last pad. */
    public static final int DEFAULT_START_PADS = 4;
    public static final int DEFAULT_TOURIST_SPOTS = 8;

    /** Resolves the racer limit for a square, preferring the festival JSON value. */
    public static int maxRacers(int fromFestivalJson) {
        return fromFestivalJson > 0 ? fromFestivalJson : DEFAULT_MAX_RACERS;
    }

    /** Gold coins charged when a player joins the race. */
    public static final int RACE_COST_GOLD = 10;

    /** Finish at or under this many seconds for the maximum ticket payout. */
    public static final double MAX_TICKET_SECONDS = 13.0;
    /** Finish at or above this many seconds yields no tickets. */
    public static final double MIN_TICKET_SECONDS = 17.0;
    public static final int MAX_TICKETS = 5;

    /** Racers who have not finished by this many seconds are marked DNF. */
    public static final long DNF_TIMEOUT_MS = 180_000L;

    /** Brief hold in RESULTS before returning to LOBBY. */
    public static final long RESULTS_HOLD_MS = 2_000L;

    /** Horizontal distance (blocks) from finish crystal center to count as finished. */
    public static final double FINISH_RADIUS = 1.35;
    /** Player feet must be at least this far above the finish block Y. */
    public static final double FINISH_MIN_Y_OFFSET = 0.2;
    /** Player feet must be at most this far above the finish block Y. */
    public static final double FINISH_MAX_Y_OFFSET = 2.75;

    public static final String HUD_KEY = "AetherhavenTreeClimbRace";

    private TreeClimbIds() {}

    /** Tickets for a finished climb time in seconds. Full payout at or under 13s, none at or above 17s. */
    public static int ticketReward(double seconds) {
        if (seconds < 0.0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            return 0;
        }
        if (seconds <= MAX_TICKET_SECONDS) {
            return MAX_TICKETS;
        }
        if (seconds >= MIN_TICKET_SECONDS) {
            return 0;
        }
        double span = MIN_TICKET_SECONDS - MAX_TICKET_SECONDS;
        double t = (seconds - MAX_TICKET_SECONDS) / span;
        return Math.max(0, (int) Math.floor(MAX_TICKETS * (1.0 - t)));
    }

    @Nonnull
    public static String formatTime(double seconds) {
        if (seconds < 0.0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            return "--";
        }
        int whole = (int) Math.floor(seconds);
        int mins = whole / 60;
        int secs = whole % 60;
        int hundredths = (int) Math.floor((seconds - whole) * 100.0);
        if (mins > 0) {
            return String.format("%d:%02d.%02d", mins, secs, hundredths);
        }
        return String.format("%d.%02d", secs, hundredths);
    }
}
