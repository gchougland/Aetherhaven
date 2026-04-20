package com.hexvane.aetherhaven.time;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.farming.SprinklerWateringService;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.schedule.VillagerScheduleService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

/** Wires {@link AetherhavenGameTimeHub} to inn, sprinklers, and villager schedules. */
public final class AetherhavenGameTimeBridgeSubscriber implements AetherhavenGameTimeSubscriber {
    @Nonnull
    private final AetherhavenPlugin plugin;

    public AetherhavenGameTimeBridgeSubscriber(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSmoothGameMinuteAdvanced(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        long prevEpochMinute,
        long newEpochMinute
    ) {
        VillagerScheduleService.applyForWorld(world, store, plugin, false);
        InnPoolService.scheduleTickFromHub(world, plugin, wtr);
        SprinklerWateringService.scheduleFromHub(world, store, plugin);
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
        if (!backward) {
            InnPoolService.catchUpAfterTimeJump(world, plugin, store, wtr, from, to);
            SprinklerWateringService.catchUpAfterTimeJump(world, store, plugin, from, to);
        }
        VillagerScheduleService.applyForWorld(world, store, plugin, true);
        InnPoolService.scheduleTickFromHub(world, plugin, wtr);
        SprinklerWateringService.scheduleFromHub(world, store, plugin);
    }
}
