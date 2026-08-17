package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalMechanic;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.SnowballFightHudSupport;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Snowball Throwing Festival: piles spawn for the day; fights start from the merchant lobby. */
public final class SnowballFestivalMechanic implements FestivalMechanic {
    public static final String MECHANIC_ID = SnowballIds.MECHANIC_ID;

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
        SnowballSession session = SnowballSessionIndex.getOrCreate(town.getTownId());
        session.clearAll();
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        if (square == null) {
            square = festivalPlot;
        }
        SnowballCourse.applyFromFestival(plugin, square, festival, session);
        world.execute(() -> SnowballPileService.placeAll(world, session));
    }

    @Override
    public void onEnd(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        SnowballSession session = SnowballSessionIndex.get(town.getTownId());
        if (session != null) {
            Store<EntityStore> store = world.getEntityStore().getStore();
            SnowballAudio.stopFightMusic(store, null, session);
        }
        world.execute(() -> {
            if (session != null) {
                SnowballPileService.clearAll(world, session);
                SnowballTeleport.thawVillagerFighters(world.getEntityStore().getStore(), session);
            }
            clearFightHuds(world);
        });
        SnowballSessionIndex.remove(town.getTownId());
    }

    private static void clearFightHuds(@Nonnull World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(playerRef.getUuid());
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null && SnowballFightHudSupport.isActive(player)) {
                SnowballFightHudSupport.removeHud(player, playerRef);
            }
        }
    }
}
