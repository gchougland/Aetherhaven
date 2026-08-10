package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalMechanic;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/** Carnival Festival: balloon popping and spin-the-wheel minigames at the festival square. */
public final class CarnivalFestivalMechanic implements FestivalMechanic {
    public static final String MECHANIC_ID = CarnivalIds.MECHANIC_ID;

    @Override
    public void onStart(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        CarnivalBalloonSessionIndex.getOrCreate(town.getTownId()).clearAll();
        CarnivalWheelSessionIndex.getOrCreate(town.getTownId()).clearAll();
        CarnivalWheelPlacementService.place(world, town.getTownId(), festivalPlot, festival);
    }

    @Override
    public void onEnd(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        CarnivalBalloonSpawnService.despawnAllForTown(world, town.getTownId());
        CarnivalWheelPlacementService.remove(world, town.getTownId());
        CarnivalBalloonSessionIndex.remove(town.getTownId());
        CarnivalWheelSessionIndex.remove(town.getTownId());
    }
}
