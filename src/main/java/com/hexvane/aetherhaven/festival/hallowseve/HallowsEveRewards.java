package com.hexvane.aetherhaven.festival.hallowseve;

/** Ticket and candy payouts from a maze run. */
public final class HallowsEveRewards {
    private HallowsEveRewards() {}

    /** About one autumn ticket per two orbs, plus one extra ticket for collecting every orb. */
    public static int ticketCount(int collected, int total) {
        if (collected <= 0) {
            return 0;
        }
        int tickets = Math.max(0, (int) Math.round(collected / 2.0));
        if (total > 0 && collected >= total) {
            tickets += 1;
        }
        return tickets;
    }

    /** About one candy for every two orbs collected. */
    public static int candyCount(int collected) {
        if (collected <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.round(collected / 2.0));
    }
}
