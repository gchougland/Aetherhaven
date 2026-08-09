package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/**
 * The unique gameplay a festival adds on top of its prefab and villager spots. Register one from another mod with
 * {@code AetherhavenPlugin.get().getFestivalMechanicRegistry().register(id, mechanic)} and point a festival JSON at it
 * with {@code "mechanicId"}.
 *
 * <p>All three callbacks run on the world thread, after the prefab swap has finished pasting.
 */
public interface FestivalMechanic {
    /** Called once when the festival opens, after its prefab is in the world. */
    default void onStart(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {}

    /** Called once when the festival closes, before the base festival square prefab is pasted back. */
    default void onEnd(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {}
}
