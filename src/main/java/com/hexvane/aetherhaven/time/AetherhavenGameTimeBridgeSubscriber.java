package com.hexvane.aetherhaven.time;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.economy.TownEconomyTimeService;
import com.hexvane.aetherhaven.farming.SprinklerWateringService;
import com.hexvane.aetherhaven.feast.FeastService;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.guild.GuildHallAdventurerPoolService;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.schedule.VillagerScheduleService;
import com.hexvane.aetherhaven.shopspot.ShopSpotDailyRerollService;
import com.hexvane.aetherhaven.shopspot.ShopSpotRefreshSystem;
import com.hexvane.aetherhaven.questboard.QuestBoardOnlineDawnService;
import com.hexvane.aetherhaven.town.CitizenDawnRevivalService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

/**
 * Wires {@link AetherhavenGameTimeHub} to town economy (treasury tithe), inn visitor pool, farm sprinklers, and
 * villager schedules.
 */
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
        TownEconomyTimeService.onGameTimeFromHub(world, plugin, wtr, store);
        VillagerScheduleService.applyForWorld(world, store, plugin, false);
        InnPoolService.scheduleTickFromHub(world, plugin, wtr);
        GuildHallAdventurerPoolService.scheduleTickFromHub(world, plugin, wtr);
        TouristPortalTickService.scheduleTickFromHub(world, plugin, wtr);
        SprinklerWateringService.scheduleFromHub(world, store, plugin);
        FeastService.pruneExpiredForWorld(world, plugin, store);
        FeastService.checkGatherTimeoutsForWorld(world, plugin);
        ShopSpotDailyRerollService.scheduleTickFromHub(world, plugin, wtr);
        ShopSpotRefreshSystem.onGameMinute(world, store, plugin, wtr);
        QuestBoardOnlineDawnService.tickWorld(world, store, plugin, wtr);
        CitizenDawnRevivalService.scheduleTickFromHub(world, plugin, wtr);
        PlotAssemblyService.schedulePassiveFromHub(world, plugin);
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
        TouristPortalTickService.catchUpLeaveAfterTimeJump(world, plugin, store, wtr);
        if (!backward) {
            InnPoolService.catchUpAfterTimeJump(world, plugin, store, wtr, from, to);
            SprinklerWateringService.catchUpAfterTimeJump(world, store, plugin, from, to);
            ShopSpotDailyRerollService.catchUpAfterTimeJump(world, plugin, store, wtr, from, to);
            CitizenDawnRevivalService.catchUpAfterTimeJump(world, plugin, store, wtr, from, to);
            TownEconomyTimeService.onGameTimeFromHub(world, plugin, wtr, store);
        }
        VillagerScheduleService.applyForWorld(world, store, plugin, true);
        InnPoolService.scheduleTickFromHub(world, plugin, wtr);
        GuildHallAdventurerPoolService.scheduleTickFromHub(world, plugin, wtr);
        TouristPortalTickService.scheduleTickFromHub(world, plugin, wtr);
        SprinklerWateringService.scheduleFromHub(world, store, plugin);
        FeastService.pruneExpiredForWorld(world, plugin, store);
        FeastService.checkGatherTimeoutsForWorld(world, plugin);
        ShopSpotDailyRerollService.scheduleTickFromHub(world, plugin, wtr);
        ShopSpotRefreshSystem.onGameMinute(world, store, plugin, wtr);
        QuestBoardOnlineDawnService.tickWorld(world, store, plugin, wtr);
        CitizenDawnRevivalService.scheduleTickFromHub(world, plugin, wtr);
        PlotAssemblyService.schedulePassiveFromHub(world, plugin);
    }
}
