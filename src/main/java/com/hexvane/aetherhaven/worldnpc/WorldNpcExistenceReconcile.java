package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Periodically ensures world NPC placements still have a live entity when confirmed absent. Never treats unloaded
 * chunks as missing. Store mutations are deferred via {@link World#execute}.
 */
public final class WorldNpcExistenceReconcile {
    private static final ConcurrentHashMap<String, Long> LAST_CHECK_MS = new ConcurrentHashMap<>();
    private static final long INTERVAL_MS = 15_000L;

    private WorldNpcExistenceReconcile() {}

    public static void tickWorld(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        long now = System.currentTimeMillis();
        Long prev = LAST_CHECK_MS.get(world.getName());
        if (prev != null && now - prev < INTERVAL_MS) {
            return;
        }
        LAST_CHECK_MS.put(world.getName(), now);
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        List<WorldNpcPlacementRecord> toRespawn = new ArrayList<>();
        for (WorldNpcPlacementRecord placement : registry.allPlacements()) {
            UUID entityUuid = placement.entityUuidOrNull();
            if (entityUuid == null) {
                toRespawn.add(placement);
                continue;
            }
            EntityPresenceUtil.EntityPresence presence = EntityPresenceUtil.resolve(store, entityUuid);
            if (EntityPresenceUtil.isConfirmedAbsent(presence)) {
                toRespawn.add(placement);
            }
        }
        if (toRespawn.isEmpty()) {
            return;
        }
        world.execute(() -> {
            for (WorldNpcPlacementRecord placement : toRespawn) {
                WorldNpcSpawnService.ensurePlacement(world, plugin, placement);
            }
        });
    }

    public static void clearWorld(@Nonnull String worldName) {
        LAST_CHECK_MS.remove(worldName);
    }
}
