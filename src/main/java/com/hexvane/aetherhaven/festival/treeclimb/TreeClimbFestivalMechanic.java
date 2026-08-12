package com.hexvane.aetherhaven.festival.treeclimb;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalMechanic;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/** Tree Climbing Festival: one multiplayer climb race lobby per town for the festival day. */
public final class TreeClimbFestivalMechanic implements FestivalMechanic {
    public static final String MECHANIC_ID = TreeClimbIds.MECHANIC_ID;

    @Override
    public void onStart(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        TreeClimbSession session = TreeClimbSessionIndex.getOrCreate(town.getTownId());
        session.clearAll();
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        if (square == null) {
            square = festivalPlot;
        }
        TreeClimbCourse.applyFromFestival(plugin, square, festival, session);
    }

    @Override
    public void onEnd(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        TreeClimbSessionIndex.remove(town.getTownId());
    }
}
