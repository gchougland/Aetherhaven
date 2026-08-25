package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.festival.lettuce.FestivalLettuceSpawnService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/** New Life Festival: puts the giant lettuce in the middle of the square and takes it away again at the end. */
public final class NewLifeFestivalMechanic implements FestivalMechanic {
    public static final String MECHANIC_ID = "new_life";

    @Override
    public void onStart(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        FestivalLettuceSpawnService.spawnCenterpiece(world, town, festivalPlot, festival);
    }

    @Override
    public void onEnd(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        FestivalLettuceSpawnService.despawnCenterpiece(world, festivalPlot, town.getTownId());
    }
}
