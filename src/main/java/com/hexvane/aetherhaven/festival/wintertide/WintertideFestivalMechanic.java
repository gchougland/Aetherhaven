package com.hexvane.aetherhaven.festival.wintertide;

import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalMechanic;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Wintertide: assigned gift exchange and a holiday ticket shop. */
public final class WintertideFestivalMechanic implements FestivalMechanic {
    public static final String MECHANIC_ID = WintertideIds.MECHANIC_ID;

    @Override
    public void onStart(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        WintertideSession session = WintertideSessionIndex.getOrCreate(town.getTownId());
        session.clearAll();
        var entityStore = world.getEntityStore();
        Store<EntityStore> store = entityStore != null ? entityStore.getStore() : null;
        long year = 1L;
        if (store != null) {
            WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
            if (wtr != null) {
                year = AetherhavenCalendar.from(wtr.getGameDateTime()).year();
            }
        }
        session.setYear(year);
        if (store != null) {
            WintertideGiftService.ensureAssignments(town, store, session);
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
        WintertideSessionIndex.remove(townId);
    }
}
