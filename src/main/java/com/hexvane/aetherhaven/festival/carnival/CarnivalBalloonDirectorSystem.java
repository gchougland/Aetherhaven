package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Advances balloon spawn cadence for every town with an active balloon game. Runs as a world tick so it does not
 * depend on the wheel face entity (wheel and balloon games can run at the same time).
 */
public final class CarnivalBalloonDirectorSystem extends TickingSystem<EntityStore> {
    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (Map.Entry<UUID, CarnivalBalloonSession> entry : CarnivalBalloonSessionIndex.entries()) {
            UUID townId = entry.getKey();
            CarnivalBalloonSession session = entry.getValue();
            if (session == null || session.getPhase() != CarnivalBalloonSession.Phase.PLAYING) {
                continue;
            }
            TownRecord town = tm.getTown(townId);
            if (town == null || !CarnivalIds.FESTIVAL_ID.equals(town.getActiveFestivalId())) {
                continue;
            }
            session.addSpawnCooldown(dt);
            if (session.getSpawned() >= CarnivalIds.BALLOON_TOTAL || session.getSpawnCooldown() > 0f) {
                continue;
            }
            PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
            FestivalDefinition festival = plugin.getFestivalCatalog().get(town.getActiveFestivalId());
            if (square == null || festival == null) {
                continue;
            }
            CarnivalBalloonSpawnService.scheduleSpawn(world, townId, square, festival);
            session.setSpawnCooldown(CarnivalIds.BALLOON_SPAWN_INTERVAL);
        }
    }
}
