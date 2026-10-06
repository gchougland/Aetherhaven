package com.hexvane.aetherhaven.production;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Saved, single-output node cycle. Time alone never awards resources. */
public final class MiningCycle {
    public static final double EXTRACTION_SECONDS = 3.9;
    private String item;
    private double respawnSeconds;
    private double workSeconds;

    public String item() { return item; }
    public double remaining() { return Math.max(0, respawnSeconds); }
    public double progress() { return Math.clamp(workSeconds / EXTRACTION_SECONDS, 0, 1); }
    public boolean ready() { return item != null && remaining() == 0; }

    /** Selection changes discard work but retain the longer remaining cooldown. */
    public boolean select(String selected, double period) {
        if (Objects.equals(item, selected)) return false;
        item = selected;
        respawnSeconds = Math.max(remaining(), validPeriod(period));
        workSeconds = 0;
        return true;
    }

    /** Only call for a loaded, eligible mine while a town member is online. */
    public void simulate(double dt) {
        if (item != null) respawnSeconds = Math.max(0, remaining() - step(dt));
    }

    public void work(double dt, boolean physicallyWorking, boolean outputHasSpace) {
        if (ready() && physicallyWorking && outputHasSpace)
            workSeconds = Math.min(EXTRACTION_SECONDS, workSeconds + step(dt));
    }

    /** Block depletion succeeds before the caller receives permission to award an item. */
    public boolean extract(String selected, double nextPeriod, boolean outputHasSpace, BooleanSupplier deplete) {
        if (!ready() || !Objects.equals(item, selected) || workSeconds < EXTRACTION_SECONDS || !outputHasSpace)
            return false;
        if (!deplete.getAsBoolean()) return false;
        respawnSeconds = validPeriod(nextPeriod);
        workSeconds = 0;
        return true;
    }

    private static double step(double dt) { return Double.isFinite(dt) ? Math.clamp(dt, 0, .25) : 0; }
    private static double validPeriod(double seconds) { return Double.isFinite(seconds) ? Math.max(1, seconds) : 60; }
}
