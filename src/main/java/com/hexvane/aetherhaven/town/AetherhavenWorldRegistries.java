package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.farming.SprinklerWateringService;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.inn.InnkeeperSpawnService;
import com.hexvane.aetherhaven.pathtool.PathToolPersistence;
import com.hexvane.aetherhaven.pathtool.PathToolRegistry;
import com.hexvane.aetherhaven.poi.PoiPersistence;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** Holds {@link TownManager} and {@link PoiRegistry} per loaded world. */
public final class AetherhavenWorldRegistries {
    private static final ConcurrentHashMap<String, TownManager> TOWN_MANAGERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PoiRegistry> POI_REGISTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PathToolRegistry> PATH_TOOL_REGISTRIES = new ConcurrentHashMap<>();

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
    public static PathToolRegistry getOrCreatePathToolRegistry(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return PATH_TOOL_REGISTRIES.computeIfAbsent(world.getName(), n -> {
            PathToolRegistry r = new PathToolRegistry(world);
            PathToolPersistence.load(world, plugin, r);
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
        SprinklerWateringService.clearWorldState(world.getName());
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
        PathToolRegistry pathReg = PATH_TOOL_REGISTRIES.remove(world.getName());
        if (pathReg != null) {
            AetherhavenPlugin p2 = AetherhavenPlugin.get();
            if (p2 != null) {
                PathToolPersistence.save(world, p2, pathReg);
            }
        }
    }

    /** Save all town files (e.g. server shutdown). */
    public static void saveAll() {
        for (TownManager tm : TOWN_MANAGERS.values()) {
            tm.saveToDisk();
        }
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p != null) {
            for (var e : PATH_TOOL_REGISTRIES.entrySet()) {
                World w = e.getValue().getWorld();
                PathToolPersistence.save(w, p, e.getValue());
            }
        }
    }

    public static void bootstrapWorld(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        getOrCreateTownManager(world, plugin);
        getOrCreatePoiRegistry(world, plugin);
        getOrCreatePathToolRegistry(world, plugin);
        TownNpcMigration.ensureElderBindingsOnWorldThread(world, plugin);
        InnkeeperSpawnService.reconcileAfterWorldLoad(world, plugin);
        InnPoolService.reconcileAfterWorldLoad(world, plugin);
    }
}
