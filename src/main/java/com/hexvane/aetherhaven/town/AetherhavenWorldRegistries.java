package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.inn.InnkeeperSpawnService;
import com.hexvane.aetherhaven.poi.PoiPersistence;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** Holds {@link TownManager} and {@link PoiRegistry} per loaded world. */
public final class AetherhavenWorldRegistries {
    private static final ConcurrentHashMap<String, TownManager> TOWN_MANAGERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PoiRegistry> POI_REGISTRIES = new ConcurrentHashMap<>();

    private AetherhavenWorldRegistries() {}

    @Nonnull
    public static TownManager getOrCreateTownManager(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return TOWN_MANAGERS.computeIfAbsent(world.getName(), n -> {
            TownManager m = new TownManager(world, TownManager.pluginData(plugin));
            m.loadFromDisk();
            return m;
        });
    }

    @Nonnull
    public static PoiRegistry getOrCreatePoiRegistry(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return POI_REGISTRIES.computeIfAbsent(world.getName(), n -> {
            PoiRegistry r = new PoiRegistry(world);
            PoiPersistence.load(world, plugin, r);
            return r;
        });
    }

    @Nonnull
    public static TownManager getTownManager(@Nonnull World world) {
        TownManager m = TOWN_MANAGERS.get(world.getName());
        if (m == null) {
            throw new IllegalStateException("TownManager not loaded for world " + world.getName());
        }
        return m;
    }

    @Nonnull
    public static PoiRegistry getPoiRegistry(@Nonnull World world) {
        PoiRegistry r = POI_REGISTRIES.get(world.getName());
        if (r == null) {
            throw new IllegalStateException("PoiRegistry not loaded for world " + world.getName());
        }
        return r;
    }

    public static void unloadWorld(@Nonnull World world) {
        TownManager tm = TOWN_MANAGERS.remove(world.getName());
        if (tm != null) {
            tm.saveToDisk();
        }
        PoiRegistry pr = POI_REGISTRIES.remove(world.getName());
        if (pr != null) {
            AetherhavenPlugin p = AetherhavenPlugin.get();
            if (p != null) {
                PoiPersistence.save(world, p, pr);
            }
        }
    }

    /** Save all town files (e.g. server shutdown). */
    public static void saveAll() {
        for (TownManager tm : TOWN_MANAGERS.values()) {
            tm.saveToDisk();
        }
    }

    public static void bootstrapWorld(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        getOrCreateTownManager(world, plugin);
        getOrCreatePoiRegistry(world, plugin);
        TownNpcMigration.ensureElderBindingsOnWorldThread(world, plugin);
        InnkeeperSpawnService.reconcileAfterWorldLoad(world, plugin);
        InnPoolService.reconcileAfterWorldLoad(world, plugin);
    }
}
