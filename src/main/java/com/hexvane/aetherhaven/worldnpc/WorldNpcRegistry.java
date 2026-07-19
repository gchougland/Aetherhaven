package com.hexvane.aetherhaven.worldnpc;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WorldNpcRegistry {
    private final World world;
    private final Map<String, WorldNpcPlacementRecord> placementsById = new LinkedHashMap<>();
    private final Map<String, WorldNpcRouteRecord> routesById = new LinkedHashMap<>();
    private final Map<UUID, WorldNpcPlayerProgress> playersByUuid = new LinkedHashMap<>();
    private boolean dirty;

    public WorldNpcRegistry(@Nonnull World world) {
        this.world = world;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void replaceAll(
        @Nonnull Collection<WorldNpcPlacementRecord> placements,
        @Nonnull Collection<WorldNpcRouteRecord> routes,
        @Nonnull Collection<WorldNpcPlayerProgress> players
    ) {
        placementsById.clear();
        routesById.clear();
        playersByUuid.clear();
        for (WorldNpcPlacementRecord p : placements) {
            String id = p.placementIdOrEmpty();
            if (!id.isEmpty()) {
                placementsById.put(id, p);
            }
        }
        for (WorldNpcRouteRecord r : routes) {
            String id = r.routeIdOrEmpty();
            if (!id.isEmpty()) {
                routesById.put(id, r);
            }
        }
        for (WorldNpcPlayerProgress progress : players) {
            try {
                UUID uuid = progress.playerUuidOrThrow();
                playersByUuid.put(uuid, progress);
            } catch (RuntimeException ignored) {
                // skip malformed rows
            }
        }
        dirty = false;
    }

    @Nonnull
    public List<WorldNpcPlacementRecord> allPlacements() {
        return List.copyOf(placementsById.values());
    }

    @Nonnull
    public List<WorldNpcRouteRecord> allRoutes() {
        return List.copyOf(routesById.values());
    }

    public int playerCount() {
        return playersByUuid.size();
    }

    @Nonnull
    public Collection<UUID> allPlayerUuids() {
        return List.copyOf(playersByUuid.keySet());
    }

    @Nullable
    public WorldNpcPlacementRecord findPlacement(@Nonnull String placementId) {
        return placementsById.get(placementId.trim());
    }

    public void upsertPlacement(@Nonnull WorldNpcPlacementRecord placement) {
        String id = placement.placementIdOrEmpty();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("placementId required");
        }
        placementsById.put(id, placement);
        dirty = true;
    }

    public boolean removePlacement(@Nonnull String placementId) {
        WorldNpcPlacementRecord removed = placementsById.remove(placementId.trim());
        if (removed != null) {
            dirty = true;
            return true;
        }
        return false;
    }

    @Nullable
    public WorldNpcRouteRecord findRoute(@Nonnull String routeId) {
        return routesById.get(routeId.trim());
    }

    public void upsertRoute(@Nonnull WorldNpcRouteRecord route) {
        String id = route.routeIdOrEmpty();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("routeId required");
        }
        routesById.put(id, route);
        dirty = true;
    }

    public boolean removeRoute(@Nonnull String routeId) {
        WorldNpcRouteRecord removed = routesById.remove(routeId.trim());
        if (removed != null) {
            dirty = true;
            return true;
        }
        return false;
    }

    @Nullable
    public WorldNpcPlayerProgress findPlayerProgress(@Nonnull UUID playerUuid) {
        return playersByUuid.get(playerUuid);
    }

    @Nonnull
    public WorldNpcPlayerProgress getOrCreatePlayerProgress(@Nonnull UUID playerUuid) {
        return playersByUuid.computeIfAbsent(playerUuid, uuid -> {
            WorldNpcPlayerProgress p = new WorldNpcPlayerProgress();
            p.setPlayerUuid(uuid);
            dirty = true;
            return p;
        });
    }

    public void markPlayerDirty() {
        dirty = true;
    }

    @Nullable
    public WorldNpcPlacementRecord findByEntityUuid(@Nonnull UUID entityUuid) {
        for (WorldNpcPlacementRecord p : placementsById.values()) {
            UUID u = p.entityUuidOrNull();
            if (entityUuid.equals(u)) {
                return p;
            }
        }
        return null;
    }

    @Nonnull
    public List<String> listPlacementIds() {
        return new ArrayList<>(placementsById.keySet());
    }
}
