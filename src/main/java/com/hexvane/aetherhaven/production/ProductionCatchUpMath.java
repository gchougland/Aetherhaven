package com.hexvane.aetherhaven.production;

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/** Converts simulated in-game work time into production entity ticks (matches live {@link ProductionTickSystem}). */
final class ProductionCatchUpMath {
    private ProductionCatchUpMath() {}

    /**
     * Credits the same entity ticks live production would have accrued over {@code workGameSeconds} of scheduled
     * {@code work}, using elapsed <em>in-game</em> seconds (not wall clock).
     *
     * <p>Live {@link ProductionTickSystem} applies one tick per server tick while working. While game time runs
     * normally, {@code WorldTimeResource.getSecondsPerTick(world)} game seconds pass per real second on average, so
     * each server tick covers {@code secondsPerTick / tps} game seconds and contributes one production tick.
     */
    static int creditTicksForWorkGameSeconds(
        @Nonnull World world,
        double workGameSeconds,
        double offlineMultiplier
    ) {
        if (workGameSeconds <= 0.0 || offlineMultiplier <= 0.0) {
            return 0;
        }
        double secondsPerTick = WorldTimeResource.getSecondsPerTick(world);
        if (secondsPerTick <= 0.0 || Double.isNaN(secondsPerTick)) {
            secondsPerTick = 1.0;
        }
        float tps = world.getTps();
        if (tps <= 0f || Float.isNaN(tps)) {
            tps = 20f;
        }
        double gameSecondsPerEntityTick = secondsPerTick / (double) tps;
        double entityTicks = workGameSeconds / gameSecondsPerEntityTick;
        return (int) Math.floor(entityTicks * offlineMultiplier);
    }
}
