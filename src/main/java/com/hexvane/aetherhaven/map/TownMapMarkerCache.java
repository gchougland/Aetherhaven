package com.hexvane.aetherhaven.map;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Pre-built town map markers per world. Written on the world thread ({@link #scheduleRebuild}),
 * read on the map-marker thread ({@link TownMapMarkerProvider}).
 */
public final class TownMapMarkerCache {
    private static final ConcurrentHashMap<String, List<MapMarker>> BY_WORLD = new ConcurrentHashMap<>();

    private TownMapMarkerCache() {}

    /** Schedules a cache rebuild on the world thread. Safe from any thread. */
    public static void scheduleRebuild(@Nonnull World world) {
        world.execute(() -> rebuildOnWorldThread(world));
    }

    /** Rebuilds markers for {@code world}. Must run on the world thread. */
    public static void rebuildOnWorldThread(@Nonnull World world) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            BY_WORLD.remove(world.getName());
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        rebuildOnWorldThread(world, tm);
    }

    /** Rebuilds markers for {@code world}. Must run on the world thread. */
    public static void rebuildOnWorldThread(@Nonnull World world, @Nonnull TownManager tm) {
        String worldName = world.getName();
        List<MapMarker> markers = new ArrayList<>();
        for (TownRecord town : filterTownsForWorld(tm.allTowns(), worldName)) {
            markers.add(TownMapMarkerProvider.buildMarker(world, town));
        }
        BY_WORLD.put(worldName, List.copyOf(markers));
    }

    @Nonnull
    static List<TownRecord> filterTownsForWorld(@Nonnull List<TownRecord> towns, @Nonnull String worldName) {
        List<TownRecord> filtered = new ArrayList<>();
        for (TownRecord town : towns) {
            if (worldName.equals(town.getWorldName())) {
                filtered.add(town);
            }
        }
        return filtered;
    }

    @Nonnull
    public static List<MapMarker> markersForWorld(@Nonnull String worldName) {
        List<MapMarker> markers = BY_WORLD.get(worldName);
        return markers != null ? markers : List.of();
    }

    public static void clearWorld(@Nonnull String worldName) {
        BY_WORLD.remove(worldName);
    }

    /** @visibleForTesting */
    static void putMarkersForTesting(@Nonnull String worldName, @Nonnull List<MapMarker> markers) {
        BY_WORLD.put(worldName, List.copyOf(markers));
    }

    /** @visibleForTesting */
    static void clearAllForTesting() {
        BY_WORLD.clear();
    }
}
