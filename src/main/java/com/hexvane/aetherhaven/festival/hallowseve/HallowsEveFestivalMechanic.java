package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalMechanic;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.HallowsEveMazeHudSupport;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Hallow's Eve: maze orb collect, growing jack o lantern, and autumn ticket shop. */
public final class HallowsEveFestivalMechanic implements FestivalMechanic {
    public static final String MECHANIC_ID = HallowsEveIds.MECHANIC_ID;

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
        HallowsEveSession session = HallowsEveSessionIndex.getOrCreate(town.getTownId());
        session.clearAll();
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        if (square == null) {
            square = festivalPlot;
        }
        FestivalDefinition.MazeStartLocalRow start = festival.getMazeStartLocal();
        if (start != null) {
            var pos =
                FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    square,
                    start.getLocalX(),
                    start.getLocalY(),
                    start.getLocalZ()
                );
            session.setStartPad(pos.x, pos.y, pos.z, start.getYawDegrees());
        }
        HallowsEveOrbSpawnService.captureMarkers(world, square, festival, session);
        HallowsEvePumpkinSpawnService.spawnCenterpiece(world, town, square, festival);
    }

    @Override
    public void onEnd(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        UUID townId = town.getTownId();
        HallowsEveSession session = HallowsEveSessionIndex.get(townId);
        UUID racer = session != null ? session.getPlayerUuid() : null;
        HallowsEveOrbSpawnService.despawnRaceOrbs(world, townId);
        HallowsEvePumpkinSpawnService.despawnCenterpiece(world, townId);
        var entityStore = world.getEntityStore();
        if (entityStore != null && racer != null) {
            Store<EntityStore> store = entityStore.getStore();
            store.forEachChunk(
                Query.and(Player.getComponentType(), PlayerRef.getComponentType(), UUIDComponent.getComponentType()),
                (chunk, commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                        if (uc == null || !racer.equals(uc.getUuid())) {
                            continue;
                        }
                        HallowsEveTeleport.thaw(chunk.getReferenceTo(i), commandBuffer);
                        Player player = chunk.getComponent(i, Player.getComponentType());
                        PlayerRef playerRef = chunk.getComponent(i, PlayerRef.getComponentType());
                        if (player != null && playerRef != null) {
                            HallowsEveMazeHudSupport.removeHud(player, playerRef);
                        }
                    }
                }
            );
        }
        HallowsEveSessionIndex.remove(townId);
    }
}
