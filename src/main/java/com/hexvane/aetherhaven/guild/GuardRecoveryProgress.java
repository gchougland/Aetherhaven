package com.hexvane.aetherhaven.guild;

/** Loaded rest time only. Slow recovery keeps early guards usable before a clinic exists. */
public final class GuardRecoveryProgress {
    public static final double QUIET_SECONDS = 30, INTERVAL_SECONDS = 10;
    private double quiet, interval;
    private float lastHealth = Float.NaN;

    public float advance(double dt, float health, float max, boolean eligible) {
        if (!Double.isFinite(dt) || dt <= 0 || !Float.isFinite(health) || !Float.isFinite(max)) return 0;
        boolean injuredAgain = Float.isFinite(lastHealth) && health < lastHealth;
        lastHealth = health;
        if (!eligible || injuredAgain || health <= 0 || health >= max) {
            quiet = interval = 0;
            return 0;
        }
        double step = Math.min(dt, .25);
        if (quiet < QUIET_SECONDS) { quiet = Math.min(QUIET_SECONDS, quiet + step); return 0; }
        interval += step;
        if (interval < INTERVAL_SECONDS) return 0;
        interval -= INTERVAL_SECONDS;
        return Math.min(max - health, Math.max(1, max * .01f));
    }
}
