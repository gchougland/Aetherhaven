package com.hexvane.aetherhaven.festival.market;

/** Autumn ticket payouts from a judged stall. */
public final class MarketRewards {
    private MarketRewards() {}

    /** About one ticket per twelve points, plus a place bonus for the top three. */
    public static int ticketCount(int score, int place) {
        if (score <= 0) {
            return 0;
        }
        int tickets = Math.max(0, (int) Math.round(score / 12.0));
        if (place == 1) {
            tickets += 4;
        } else if (place == 2) {
            tickets += 2;
        } else if (place == 3) {
            tickets += 1;
        }
        return tickets;
    }

    public static boolean grantsPlushie(int place) {
        return place == 1;
    }
}
