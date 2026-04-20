package com.hexvane.aetherhaven.time;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

/**
 * Runs once per {@link EntityStore} tick after core {@link com.hypixel.hytale.server.core.modules.time.WorldTimeSystems}
 * (rely on plugin registration order). Detects smooth minute advances vs discontinuities and notifies {@link AetherhavenGameTimeHub}.
 */
public final class AetherhavenGameTimeCoordinatorSystem extends TickingSystem<EntityStore> {
    @Nonnull
    private final AetherhavenGameTimeHub hub;
    @Nonnull
    private final ResourceType<EntityStore, AetherhavenGameTimeCursorResource> cursorType;

    public AetherhavenGameTimeCoordinatorSystem(
        @Nonnull AetherhavenGameTimeHub hub,
        @Nonnull ResourceType<EntityStore, AetherhavenGameTimeCursorResource> cursorType
    ) {
        this.hub = hub;
        this.cursorType = cursorType;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world.getWorldConfig().isGameTimePaused()) {
            return;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        AetherhavenGameTimeCursorResource cursor = store.getResource(this.cursorType);
        if (cursor == null) {
            return;
        }
        LocalDateTime nowDt = wtr.getGameDateTime();
        Instant now = wtr.getGameTime();
        long epochMinNow = GameTimeEpochs.gameEpochMinute(nowDt);

        if (!cursor.isInitialized()) {
            cursor.initFrom(now, epochMinNow);
            return;
        }

        Instant prev = cursor.getLastSeenGameTime();
        long epochMinPrev = cursor.getLastSeenEpochMinute();
        if (prev == null) {
            cursor.initFrom(now, epochMinNow);
            return;
        }

        boolean backward = now.isBefore(prev);
        long deltaMinutes = epochMinNow - epochMinPrev;
        boolean discontinuity =
            backward
                || deltaMinutes < 0L
                || deltaMinutes > 1L
                || GameTimeEpochs.largeSameMinuteInstantDelta(prev, now);

        if (!discontinuity) {
            if (epochMinNow != epochMinPrev) {
                this.hub.notifySmoothMinute(store, world, wtr, epochMinPrev, epochMinNow);
            }
        } else {
            this.hub.notifyDiscontinuity(store, world, wtr, prev, now, nowDt, backward);
        }

        cursor.initFrom(now, epochMinNow);
    }
}
