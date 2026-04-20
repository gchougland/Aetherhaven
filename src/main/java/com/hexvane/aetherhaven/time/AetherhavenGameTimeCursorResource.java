package com.hexvane.aetherhaven.time;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tracks the last game time processed by {@link AetherhavenGameTimeCoordinatorSystem} so smooth advance vs jumps can be
 * detected. Not required for correctness of {@link com.hypixel.hytale.server.core.modules.time.WorldTimeResource}; mod-only.
 */
public final class AetherhavenGameTimeCursorResource implements Resource<EntityStore> {
    /** Unset until first tick initializes from {@code WorldTimeResource}. */
    @Nullable
    private Instant lastSeenGameTime;

    private long lastSeenEpochMinute = Long.MIN_VALUE;

    @Nullable
    public Instant getLastSeenGameTime() {
        return lastSeenGameTime;
    }

    public void setLastSeenGameTime(@Nonnull Instant lastSeenGameTime) {
        this.lastSeenGameTime = lastSeenGameTime;
    }

    public long getLastSeenEpochMinute() {
        return lastSeenEpochMinute;
    }

    public void setLastSeenEpochMinute(long lastSeenEpochMinute) {
        this.lastSeenEpochMinute = lastSeenEpochMinute;
    }

    /** First store tick: mirror WTR without treating the frame as a discontinuity. */
    public void initFrom(@Nonnull Instant gameTime, long epochMinute) {
        this.lastSeenGameTime = gameTime;
        this.lastSeenEpochMinute = epochMinute;
    }

    public boolean isInitialized() {
        return lastSeenGameTime != null;
    }

    @Override
    public Resource<EntityStore> clone() {
        AetherhavenGameTimeCursorResource c = new AetherhavenGameTimeCursorResource();
        c.lastSeenGameTime = lastSeenGameTime;
        c.lastSeenEpochMinute = lastSeenEpochMinute;
        return c;
    }
}
