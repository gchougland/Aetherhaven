package com.hexvane.aetherhaven.time;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.GameTimeTickListenerRegistry;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

/**
 * Forwards game-time ticks to optional subplugin listeners. Core-only town ticks that must always run stay here
 * only if no subplugin owns them; feature ticks register via {@link GameTimeTickListenerRegistry}.
 */
public final class AetherhavenGameTimeBridgeSubscriber implements AetherhavenGameTimeSubscriber {
    @Nonnull
    private final AetherhavenPlugin plugin;

    @Nonnull
    private final GameTimeTickListenerRegistry tickListeners;

    public AetherhavenGameTimeBridgeSubscriber(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull GameTimeTickListenerRegistry tickListeners
    ) {
        this.plugin = plugin;
        this.tickListeners = tickListeners;
    }

    @Override
    public void onSmoothGameMinuteAdvanced(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        long prevEpochMinute,
        long newEpochMinute
    ) {
        tickListeners.onSmoothGameMinuteAdvanced(store, world, wtr, prevEpochMinute, newEpochMinute);
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
        tickListeners.onGameTimeDiscontinuity(store, world, wtr, from, to, toDateTime, backward);
    }
}
