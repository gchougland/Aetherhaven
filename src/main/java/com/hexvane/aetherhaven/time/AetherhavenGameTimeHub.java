package com.hexvane.aetherhaven.time;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;

/** In-process pub/sub for {@link AetherhavenGameTimeCoordinatorSystem}. */
public final class AetherhavenGameTimeHub {
    private final List<AetherhavenGameTimeSubscriber> subscribers = new CopyOnWriteArrayList<>();

    public void register(@Nonnull AetherhavenGameTimeSubscriber subscriber) {
        subscribers.add(subscriber);
    }

    public void unregister(@Nonnull AetherhavenGameTimeSubscriber subscriber) {
        subscribers.remove(subscriber);
    }

    void notifySmoothMinute(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        long prevEpochMinute,
        long newEpochMinute
    ) {
        for (AetherhavenGameTimeSubscriber s : subscribers) {
            s.onSmoothGameMinuteAdvanced(store, world, wtr, prevEpochMinute, newEpochMinute);
        }
    }

    void notifyDiscontinuity(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Instant from,
        @Nonnull Instant to,
        @Nonnull LocalDateTime toDateTime,
        boolean backward
    ) {
        for (AetherhavenGameTimeSubscriber s : subscribers) {
            s.onGameTimeDiscontinuity(store, world, wtr, from, to, toDateTime, backward);
        }
    }
}
