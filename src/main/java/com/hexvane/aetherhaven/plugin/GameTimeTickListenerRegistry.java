package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.time.AetherhavenGameTimeSubscriber;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;

/** Fan-out target for optional subplugin game-time ticks. */
public final class GameTimeTickListenerRegistry implements AetherhavenGameTimeSubscriber {
    private final List<GameTimeTickListener> listeners = new CopyOnWriteArrayList<>();

    public void register(@Nonnull GameTimeTickListener listener) {
        listeners.add(listener);
    }

    public void unregister(@Nonnull GameTimeTickListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onSmoothGameMinuteAdvanced(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        long prevEpochMinute,
        long newEpochMinute
    ) {
        for (GameTimeTickListener listener : listeners) {
            listener.onSmoothGameMinuteAdvanced(store, world, wtr, prevEpochMinute, newEpochMinute);
        }
    }

    @Override
    public void onGameTimeDiscontinuity(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Instant from,
        @Nonnull Instant to,
        @Nonnull LocalDateTime toDateTime,
        boolean backward
    ) {
        for (GameTimeTickListener listener : listeners) {
            listener.onGameTimeDiscontinuity(store, world, wtr, from, to, toDateTime, backward);
        }
    }
}
