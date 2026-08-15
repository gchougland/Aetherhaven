package com.hexvane.aetherhaven.festival.market;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalMechanic;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Market Festival: shared stall contest, Lyren judging, and resident ticket shops. */
public final class MarketFestivalMechanic implements FestivalMechanic {
    public static final String MECHANIC_ID = MarketIds.MECHANIC_ID;

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
        MarketSession session = MarketSessionIndex.getOrCreate(town.getTownId());
        session.clearAll();
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        if (square == null) {
            square = festivalPlot;
        }
        MarketStallService.captureAndSpawn(world, town, square, festival);
        var entityStore = world.getEntityStore();
        Store<EntityStore> store = entityStore != null ? entityStore.getStore() : null;
        long year = 1L;
        if (store != null) {
            WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
            if (wtr != null) {
                year = AetherhavenCalendar.from(wtr.getGameDateTime()).year();
            }
            MarketVendorService.assignAndSend(store, town, year);
        } else {
            session.setYear(year);
        }
    }

    @Override
    public void onEnd(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        UUID townId = town.getTownId();
        MarketStallService.dropLeftovers(world, townId);
        MarketStallService.despawnAll(world, townId);
        MarketSessionIndex.remove(townId);
    }
}
