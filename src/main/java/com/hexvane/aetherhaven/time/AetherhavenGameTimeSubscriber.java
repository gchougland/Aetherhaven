package com.hexvane.aetherhaven.time;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

/**
 * Subscribers run on the entity store thread from {@link AetherhavenGameTimeCoordinatorSystem}; keep work cheap and
 * defer heavy mutations with {@link World#execute}.
 */
public interface AetherhavenGameTimeSubscriber {
    /**
     * Smooth play: game calendar minute advanced by one (same wall-clock tick pipeline).
     *
     * @param prevEpochMinute previous game epoch minute index
     * @param newEpochMinute current game epoch minute (differs from {@code prevEpochMinute})
     */
    default void onSmoothGameMinuteAdvanced(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        long prevEpochMinute,
        long newEpochMinute
    ) {}

    /**
     * Time moved discontinuously (large forward skip, backward, or multi-minute jump). Schedules should use final
     * instant {@code to}; inn/sprinkler logic may use {@code from}..{@code to} for catch-up.
     */
    default void onGameTimeDiscontinuity(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Instant from,
        @Nonnull Instant to,
        @Nonnull LocalDateTime toDateTime,
        boolean backward
    ) {}
}
