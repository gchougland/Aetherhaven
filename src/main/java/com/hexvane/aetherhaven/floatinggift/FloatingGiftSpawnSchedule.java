package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-player balloon spawn pacing using {@link WorldTimeResource#getGameTime()} (calendar instant). Advances with dilated world ticks. */
public final class FloatingGiftSpawnSchedule {
    /**
     * On spawn failure (model missing, etc.), reschedule this many calendar seconds forward so overdue entries do not churn every 2s.
     */
    private static final long FAILURE_BACKOFF_CALENDAR_SECONDS = 900;

    private static final Map<UUID, Instant> NEXT = new ConcurrentHashMap<>();

    private FloatingGiftSpawnSchedule() {}

    @Nonnull
    public static Instant ensureAndGet(@Nonnull UUID playerUuid, @Nonnull Instant gameNow, @Nonnull AetherhavenPluginConfig cfg) {
        return NEXT.computeIfAbsent(playerUuid, u -> randomNext(gameNow, cfg));
    }

    /** Optional view without mutating schedule (reads current entry only). */
    @Nullable
    public static Instant get(@Nonnull UUID playerUuid) {
        return NEXT.get(playerUuid);
    }

    public static void onSpawnSucceeded(@Nonnull UUID playerUuid, @Nonnull Instant gameNow, @Nonnull AetherhavenPluginConfig cfg) {
        NEXT.put(playerUuid, randomNext(gameNow, cfg));
    }

    public static void onSpawnFailed(@Nonnull UUID playerUuid, @Nonnull Instant gameNow) {
        NEXT.put(playerUuid, gameNow.plusSeconds(FAILURE_BACKOFF_CALENDAR_SECONDS));
    }

    @Nonnull
    public static Instant randomNext(@Nonnull Instant gameNow, @Nonnull AetherhavenPluginConfig cfg) {
        long daySecs = Math.max(1L, WorldTimeResource.SECONDS_PER_DAY);
        long minSecs = Math.max(1L, Math.round(daySecs * cfg.getFloatingGiftSpawnIntervalDaysMin()));
        long maxSecs = Math.max(minSecs, Math.round(daySecs * cfg.getFloatingGiftSpawnIntervalDaysMax()));
        long jitter = ThreadLocalRandom.current().nextLong(minSecs, maxSecs + 1L);
        return gameNow.plusSeconds(jitter);
    }

    /**
     * Remaining calendar time on {@link WorldTimeResource#getGameTime()} until spawn is allowed; negative/zero if overdue.
     */
    @Nonnull
    public static Duration calendarWait(@Nonnull Instant gameNow, @Nullable Instant scheduled) {
        if (scheduled == null) {
            return Duration.ZERO;
        }
        return Duration.between(gameNow, scheduled);
    }

    /**
     * Rough wall-clock seconds until calendar wait elapses — uses configurable day-night length vs 86400s calendar slice and dilation.
     * Nonlinear sunrise/sunset is ignored (good enough for a debug ETA).
     */
    public static double approximateWallClockSeconds(@Nonnull Duration calendarWait, int fullDayCycleSecondsWorldConfig, float timeDilationModifier) {
        double dil = Math.max(0.01, timeDilationModifier);
        double day = Math.max(1.0, fullDayCycleSecondsWorldConfig);
        double secs = Math.max(0.0, calendarWait.getSeconds() + calendarWait.getNano() * 1e-9);
        return secs * day / WorldTimeResource.SECONDS_PER_DAY / dil;
    }
}
