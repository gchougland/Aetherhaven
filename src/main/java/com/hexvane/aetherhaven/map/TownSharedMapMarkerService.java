package com.hexvane.aetherhaven.map;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Removes legacy town markers from {@link WorldMarkersResource}. Town markers are supplied by
 * {@link TownMapMarkerProvider} so Y is not hardcoded to 100.
 */
public final class TownSharedMapMarkerService {
    private TownSharedMapMarkerService() {}

    public static void purgeLegacyStoredMarkers(@Nonnull World world) {
        world.execute(() -> purgeLegacyStoredMarkersOnThread(world));
    }

    private static void purgeLegacyStoredMarkersOnThread(@Nonnull World world) {
        WorldMarkersResource markers = worldMarkers(world);
        if (markers == null) {
            return;
        }
        List<UserMapMarker> next = new ArrayList<>();
        for (UserMapMarker existing : markers.getUserMapMarkers()) {
            if (!TownMapMarkerProvider.isTownMarkerId(existing.getId())) {
                next.add(existing);
            }
        }
        markers.setUserMapMarkers(next);
    }

    @Nullable
    private static WorldMarkersResource worldMarkers(@Nonnull World world) {
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return null;
        }
        return chunkStore.getStore().getResource(WorldMarkersResource.getResourceType());
    }
}
