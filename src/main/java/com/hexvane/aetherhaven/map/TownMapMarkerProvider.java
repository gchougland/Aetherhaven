package com.hexvane.aetherhaven.map;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * World-map waypoints for towns; Y is surface height so map teleport lands on the ground.
 * {@link #MARKER_ICON} is a map-only asset (cropped from the memories category art), not the UI page texture.
 */
public final class TownMapMarkerProvider implements WorldMapManager.MarkerProvider {
    public static final String MARKER_ICON = "Aetherhaven_Town.png";
    public static final TownMapMarkerProvider INSTANCE = new TownMapMarkerProvider();
    private static final String MARKER_ID_PREFIX = "aetherhaven-town-";

    private TownMapMarkerProvider() {}

    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        String worldName = world.getName();
        for (TownRecord town : tm.allTowns()) {
            if (worldName.equals(town.getWorldName())) {
                collector.addIgnoreViewDistance(buildMarker(world, town));
            }
        }
    }

    @Nonnull
    public static MapMarker buildMarker(@Nonnull World world, @Nonnull TownRecord town) {
        double x = town.getCharterX() + 0.5;
        double z = town.getCharterZ() + 0.5;
        double y = standingY(world, town);
        return new MapMarkerBuilder(markerId(town.getTownId()), MARKER_ICON, new Transform(x, y, z))
            .withCustomName(town.getDisplayName())
            .build();
    }

    /** Block Y for the player's feet (air above the heightmap sample). */
    static double standingY(@Nonnull World world, @Nonnull TownRecord town) {
        int bx = town.getCharterX();
        int bz = town.getCharterZ();
        WorldChunk chunk = world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
        if (chunk != null) {
            return chunk.getHeight(bx, bz) + 1.0;
        }
        return town.getCharterY() + 1.0;
    }

    @Nonnull
    public static String markerId(@Nonnull UUID townId) {
        return MARKER_ID_PREFIX + townId;
    }

    public static boolean isTownMarkerId(@Nonnull String id) {
        return id.startsWith(MARKER_ID_PREFIX);
    }
}
