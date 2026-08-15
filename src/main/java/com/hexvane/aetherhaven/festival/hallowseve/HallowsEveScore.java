package com.hexvane.aetherhaven.festival.hallowseve;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One maze run used to rank the Hallow's Eve scoreboard. Higher collected always wins. */
public final class HallowsEveScore {
    private final int collected;
    private final int total;
    private final long remainingMs;

    private HallowsEveScore(int collected, int total, long remainingMs) {
        this.collected = Math.max(0, collected);
        this.total = Math.max(0, total);
        this.remainingMs = Math.max(0L, remainingMs);
    }

    @Nonnull
    public static HallowsEveScore of(int collected, int total, long remainingMs) {
        return new HallowsEveScore(collected, total, remainingMs);
    }

    public int collected() {
        return collected;
    }

    public int total() {
        return total;
    }

    public long remainingMs() {
        return remainingMs;
    }

    public boolean collectedAll() {
        return total > 0 && collected >= total;
    }

    public boolean isBetterThan(@Nullable HallowsEveScore other) {
        if (other == null) {
            return collected > 0;
        }
        if (collected != other.collected) {
            return collected > other.collected;
        }
        if (collectedAll() && other.collectedAll()) {
            return remainingMs > other.remainingMs;
        }
        return false;
    }

    public static int compareBestFirst(@Nonnull HallowsEveScore a, @Nonnull HallowsEveScore b) {
        if (a.isBetterThan(b)) {
            return -1;
        }
        if (b.isBetterThan(a)) {
            return 1;
        }
        return 0;
    }

    @Nonnull
    public String scoreLabel() {
        if (collectedAll() && remainingMs > 0L) {
            return collected + " orbs, " + formatSecondsLeft(remainingMs) + " left";
        }
        if (collected == 1) {
            return "1 orb";
        }
        return collected + " orbs";
    }

    @Nonnull
    public static String formatSecondsLeft(long remainingMs) {
        int secs = (int) Math.ceil(Math.max(0L, remainingMs) / 1000.0);
        if (secs == 1) {
            return "1 second";
        }
        return secs + " seconds";
    }
}
