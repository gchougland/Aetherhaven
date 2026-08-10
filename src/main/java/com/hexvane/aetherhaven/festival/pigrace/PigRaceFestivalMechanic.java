package com.hexvane.aetherhaven.festival.pigrace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalMechanic;
import com.hexvane.aetherhaven.festival.FestivalRaceLanes;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;

/** Pig Racing Festival: pigs stay for the whole day; races start from the lobby session. */
public final class PigRaceFestivalMechanic implements FestivalMechanic {
    public static final String MECHANIC_ID = PigRaceLanes.MECHANIC_ID;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public void onStart(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        var entityStore = world.getEntityStore();
        if (plugin == null || entityStore == null) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        PigRaceSession session = PigRaceSessionIndex.getOrCreate(town.getTownId());
        session.clearAll();
        List<PigRaceSession.Racer> racers =
            PigRaceSpawnService.spawnRacers(world, store, plugin, town, festivalPlot);
        int expectedLanes = FestivalRaceLanes.resolve(festival).size();
        if (racers.size() < expectedLanes) {
            LOGGER.atWarning().log(
                "Pig race spawned %s/%s pigs for town %s",
                racers.size(),
                expectedLanes,
                town.getTownId()
            );
        }
        session.setRacers(racers);
    }

    @Override
    public void onEnd(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        PigRaceSession session = PigRaceSessionIndex.get(town.getTownId());
        if (session != null) {
            var entityStore = world.getEntityStore();
            if (entityStore != null) {
                PigRaceAudio.stopAllRaceAudio(entityStore.getStore(), null, session);
            }
            PigRaceSpawnService.despawnSessionRacers(world, session);
        }
        PigRaceSessionIndex.remove(town.getTownId());
    }
}
