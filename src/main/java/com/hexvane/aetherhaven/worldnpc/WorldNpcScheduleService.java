package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.villager.AetherhavenNpcTeleport;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Moves world NPCs for stations/routes. Never writes Store from the listener itself — always
 * {@link World#execute}.
 */
public final class WorldNpcScheduleService {
    private static final Map<String, Integer> ROUTE_NODE_INDEX = new ConcurrentHashMap<>();
    private static final Map<String, Long> ROUTE_WAIT_UNTIL_MINUTE = new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_STATION_ID = new ConcurrentHashMap<>();

    private WorldNpcScheduleService() {}

    @Nonnull
    public static GameTimeTickListener createListener(@Nonnull AetherhavenPlugin plugin) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                tick(plugin, world, wtr, newEpochMinute);
            }

            @Override
            public void onGameTimeDiscontinuity(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                @Nonnull Instant from,
                @Nonnull Instant to,
                @Nonnull LocalDateTime toDateTime,
                boolean backward
            ) {
                tick(plugin, world, wtr, epochMinute(toDateTime));
            }
        };
    }

    private static long epochMinute(@Nonnull LocalDateTime gameTime) {
        return gameTime.toLocalDate().toEpochDay() * 24L * 60L + gameTime.toLocalTime().toSecondOfDay() / 60L;
    }

    private static void tick(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        long epochMinute
    ) {
        WorldNpcExistenceReconcile.tickWorld(world, plugin);
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        int secondOfDay = wtr.getGameDateTime().toLocalTime().toSecondOfDay();
        for (WorldNpcPlacementRecord placement : registry.allPlacements()) {
            WorldNpcScheduleMode mode = placement.scheduleModeOrDefault();
            if (mode == WorldNpcScheduleMode.STATIC) {
                scheduleStaticHold(world, placement);
                continue;
            }
            if (mode == WorldNpcScheduleMode.STATIONS) {
                scheduleStations(world, placement, secondOfDay);
            } else if (mode == WorldNpcScheduleMode.ROUTE) {
                scheduleRoute(world, plugin, registry, placement, epochMinute);
            }
        }
    }

    private static void scheduleStaticHold(@Nonnull World world, @Nonnull WorldNpcPlacementRecord placement) {
        UUID entityUuid = placement.entityUuidOrNull();
        if (entityUuid == null) {
            return;
        }
        WorldNpcPlacementRecord snap = placement;
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) {
                return;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
            if (ref == null || !ref.isValid()) {
                return;
            }
            WorldNpcSpawnService.applyStaticIdleHold(store, ref, snap);
        });
    }

    private static void scheduleStations(
        @Nonnull World world,
        @Nonnull WorldNpcPlacementRecord placement,
        int secondOfDay
    ) {
        WorldNpcStationRecord active = null;
        for (WorldNpcStationRecord station : placement.stationsOrEmpty()) {
            if (station.isActiveAtSecondOfDay(secondOfDay)) {
                active = station;
                break;
            }
        }
        if (active == null) {
            return;
        }
        String key = world.getName() + ":" + placement.placementIdOrEmpty();
        String stationId = active.stationIdOrEmpty();
        if (stationId.isEmpty()) {
            stationId = active.getX() + "," + active.getY() + "," + active.getZ();
        }
        String prev = LAST_STATION_ID.get(key);
        if (stationId.equals(prev)) {
            return;
        }
        LAST_STATION_ID.put(key, stationId);
        double x = active.getX();
        double y = active.getY();
        double z = active.getZ();
        float yaw = active.getYawDegrees();
        UUID entityUuid = placement.entityUuidOrNull();
        if (entityUuid == null) {
            return;
        }
        world.execute(() -> teleportEntity(world, entityUuid, x, y, z, yaw));
    }

    private static void scheduleRoute(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldNpcRegistry registry,
        @Nonnull WorldNpcPlacementRecord placement,
        long epochMinute
    ) {
        String routeId = placement.routeIdOrEmpty();
        if (routeId.isEmpty()) {
            return;
        }
        WorldNpcRouteRecord route = registry.findRoute(routeId);
        if (route == null || route.nodesOrEmpty().isEmpty()) {
            return;
        }
        List<WorldNpcRouteNodeRecord> nodes = route.nodesOrEmpty();
        String key = world.getName() + ":" + placement.placementIdOrEmpty();
        Long waitUntil = ROUTE_WAIT_UNTIL_MINUTE.get(key);
        if (waitUntil != null && epochMinute < waitUntil) {
            return;
        }
        int index = ROUTE_NODE_INDEX.getOrDefault(key, 0);
        if (index < 0 || index >= nodes.size()) {
            index = 0;
        }
        WorldNpcRouteNodeRecord node = nodes.get(index);
        UUID entityUuid = placement.entityUuidOrNull();
        if (entityUuid != null) {
            double x = node.getX();
            double y = node.getY();
            double z = node.getZ();
            float yaw = node.getYawDegrees();
            world.execute(() -> teleportEntity(world, entityUuid, x, y, z, yaw));
        }
        int wait = node.getWaitSeconds();
        if (wait > 0) {
            ROUTE_WAIT_UNTIL_MINUTE.put(key, epochMinute + Math.max(1, wait / 60));
        } else {
            ROUTE_WAIT_UNTIL_MINUTE.remove(key);
        }
        ROUTE_NODE_INDEX.put(key, (index + 1) % nodes.size());
    }

    private static void teleportEntity(
        @Nonnull World world,
        @Nonnull UUID entityUuid,
        double x,
        double y,
        double z,
        float yawDegrees
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        float yaw = (float) Math.toRadians(yawDegrees);
        Teleport teleport = Teleport.createExact(new Vector3d(x, y, z), new Rotation3f(0f, yaw, 0f));
        store.tryRemoveComponent(ref, com.hypixel.hytale.server.core.entity.Frozen.getComponentType());
        AetherhavenNpcTeleport.apply(ref, store, teleport);
    }
}
