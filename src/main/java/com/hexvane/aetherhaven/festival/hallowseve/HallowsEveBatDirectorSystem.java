package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Keeps a small flock of bats above every town running Hallow's Eve. */
public final class HallowsEveBatDirectorSystem extends TickingSystem<EntityStore> {
    private float secondsUntilRestock;

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        secondsUntilRestock -= dt;
        if (secondsUntilRestock > 0f) {
            return;
        }
        secondsUntilRestock = HallowsEveIds.BAT_RESTOCK_SECONDS;
        if (!HallowsEveBatComponent.isRegistered()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }
        String worldName = world.getName();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Set<UUID> activeTownIds = new HashSet<>();
        for (TownRecord town : tm.allTowns()) {
            if (!worldName.equals(town.getWorldName())) {
                continue;
            }
            if (!HallowsEveIds.FESTIVAL_ID.equals(town.getActiveFestivalId())) {
                continue;
            }
            activeTownIds.add(town.getTownId());
        }
        HallowsEveBatSpawnService.despawnOrphans(store, activeTownIds);
        if (activeTownIds.isEmpty()) {
            return;
        }
        Map<UUID, Integer> counts = HallowsEveBatSpawnService.countByTown(store);
        for (UUID townId : activeTownIds) {
            int have = counts.getOrDefault(townId, 0);
            if (have >= HallowsEveIds.BAT_COUNT) {
                continue;
            }
            TownRecord town = tm.getTown(townId);
            PlotInstance square = town != null ? FestivalService.findFestivalSquare(plugin, town) : null;
            if (square == null) {
                continue;
            }
            HallowsEveBatSpawnService.ensureBats(world, townId, square);
        }
    }
}
