package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.difficulty.WorldDifficultyPersistence;
import com.hexvane.aetherhaven.difficulty.WorldDifficultyState;
import com.hexvane.aetherhaven.construction.assembly.AssemblyMarkerSpawner;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.autonomy.pathnav.PathNavGraphService;
import com.hexvane.aetherhaven.farming.SprinklerWateringService;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.inn.InnkeeperSpawnService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolPersistence;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.patrol.PatrolRoutePersistence;
import com.hexvane.aetherhaven.patrol.PatrolRouteRegistry;
import com.hexvane.aetherhaven.pathtool.PathToolPersistence;
import com.hexvane.aetherhaven.pathtool.PathToolRegistry;
import com.hexvane.aetherhaven.map.TownBorderMapOverlayService;
import com.hexvane.aetherhaven.map.RaidQuestMarkerProvider;
import com.hexvane.aetherhaven.map.TownMapMarkerProvider;
import com.hexvane.aetherhaven.map.TownSharedMapMarkerService;
import com.hexvane.aetherhaven.poi.PoiPersistence;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.shopspot.ShopSpotDailyRerollService;
import com.hexvane.aetherhaven.shopspot.ShopSpotPersistence;
import com.hexvane.aetherhaven.shopspot.ShopSpotRegistry;
import com.hexvane.aetherhaven.tourist.TouristPortalPersistence;
import com.hexvane.aetherhaven.tourist.TouristPortalRegistry;
import com.hexvane.aetherhaven.tourist.TouristReconcileService;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hexvane.aetherhaven.worldnpc.WorldNpcExistenceReconcile;
import com.hexvane.aetherhaven.worldnpc.WorldNpcPersistence;
import com.hexvane.aetherhaven.worldnpc.WorldNpcRegistry;
import com.hexvane.aetherhaven.worldnpc.WorldNpcSpawnService;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Holds {@link TownManager} and {@link PoiRegistry} per loaded world. */
public final class AetherhavenWorldRegistries {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<String, TownManager> TOWN_MANAGERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PoiRegistry> POI_REGISTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PathToolRegistry> PATH_TOOL_REGISTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PathNavGraphService> PATH_NAV_GRAPH_SERVICES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PatrolRouteRegistry> PATROL_ROUTE_REGISTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ShopSpotRegistry> SHOP_SPOT_REGISTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, TouristPortalRegistry> TOURIST_PORTAL_REGISTRIES =
        new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, WorldNpcRegistry> WORLD_NPC_REGISTRIES = new ConcurrentHashMap<>();

    private AetherhavenWorldRegistries() {}

    /**
     * Reloads {@code towns.json} when a {@link TownManager} is already cached (singleplayer re-enter without
     * {@link #unloadWorld}).
     */
    public static void refreshTownDataFromDisk(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager existing = TOWN_MANAGERS.get(world.getName());
        if (existing == null) {
            return;
        }
        long diskMtime = existing.getSaveFileLastModifiedMs();
        if (diskMtime > 0L && existing.getLastSavedToDiskMs() > diskMtime) {
            LOGGER.atInfo().log(
                "Skipping towns.json reload for world %s: in-memory state is newer than disk",
                world.getName()
            );
            return;
        }
        int plotsBefore = existing.countAllPlotInstances();
        existing.loadFromDisk();
        int plotsAfter = existing.countAllPlotInstances();
        if (plotsAfter < plotsBefore) {
            LOGGER.atWarning().log(
                "towns.json reload for world %s dropped plot count from %d to %d",
                world.getName(),
                plotsBefore,
                plotsAfter
            );
        }
        existing.clampAllPlotProductionToCatalog(
            plugin.getProductionCatalog(),
            plugin.getWorkplaceUnlockCatalog(),
            plugin.getConstructionCatalog()
        );
    }

    @Nonnull
    public static TownManager getOrCreateTownManager(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return TOWN_MANAGERS.computeIfAbsent(world.getName(), n -> {
            TownManager m = new TownManager(world, TownManager.pluginData(plugin));
            m.loadFromDisk();
            m.clampAllPlotProductionToCatalog(
                plugin.getProductionCatalog(),
                plugin.getWorkplaceUnlockCatalog(),
                plugin.getConstructionCatalog()
            );
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
            getOrCreatePathNavGraphService(world).rebuildAll(r, plugin.getConfig().get());
            return r;
        });
    }

    @Nonnull
    public static PathNavGraphService getOrCreatePathNavGraphService(@Nonnull World world) {
        return PATH_NAV_GRAPH_SERVICES.computeIfAbsent(world.getName(), n -> new PathNavGraphService());
    }

    @Nonnull
    public static ShopSpotRegistry getOrCreateShopSpotRegistry(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return SHOP_SPOT_REGISTRIES.computeIfAbsent(world.getName(), n -> {
            ShopSpotRegistry r = new ShopSpotRegistry(world);
            ShopSpotPersistence.load(world, plugin, r);
            return r;
        });
    }

    @Nonnull
    public static TouristPortalRegistry getOrCreateTouristPortalRegistry(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin
    ) {
        return TOURIST_PORTAL_REGISTRIES.computeIfAbsent(world.getName(), n -> {
            TouristPortalRegistry r = new TouristPortalRegistry(world);
            TouristPortalPersistence.load(world, plugin, r);
            return r;
        });
    }

    @Nonnull
    public static PatrolRouteRegistry getOrCreatePatrolRouteRegistry(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin
    ) {
        return PATROL_ROUTE_REGISTRIES.computeIfAbsent(world.getName(), n -> {
            PatrolRouteRegistry r = new PatrolRouteRegistry(world);
            PatrolRoutePersistence.load(world, plugin, r);
            return r;
        });
    }

    @Nonnull
    public static WorldNpcRegistry getOrCreateWorldNpcRegistry(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin
    ) {
        return WORLD_NPC_REGISTRIES.computeIfAbsent(world.getName(), n -> {
            WorldNpcRegistry r = new WorldNpcRegistry(world);
            WorldNpcPersistence.load(world, plugin, r);
            return r;
        });
    }

    /** Existing world NPC registry only; does not load or create. Safe for HUD tick reads. */
    @Nullable
    public static WorldNpcRegistry getWorldNpcRegistry(@Nonnull World world) {
        return WORLD_NPC_REGISTRIES.get(world.getName());
    }

    @Nonnull
    public static TownManager getTownManager(@Nonnull World world) {
        TownManager m = TOWN_MANAGERS.get(world.getName());
        if (m == null) {
            throw new IllegalStateException("TownManager not loaded for world " + world.getName());
        }
        return m;
    }

    /**
     * Finds the player's town across all loaded world managers. Tries {@code prefer} first when non-null
     * (current-world affinity), then scans the rest. Temporary-instance managers are empty and skipped
     * naturally.
     */
    @Nullable
    public static TownRecord findTownForPlayerAcrossWorlds(
        @Nonnull UUID playerUuid,
        @Nullable TownManager prefer
    ) {
        if (prefer != null) {
            TownRecord local = prefer.findTownForPlayerInWorld(playerUuid);
            if (local != null) {
                return local;
            }
        }
        for (TownManager tm : TOWN_MANAGERS.values()) {
            if (tm == prefer) {
                continue;
            }
            TownRecord t = tm.findTownForPlayerInWorld(playerUuid);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /** Looks up a town id in every loaded {@link TownManager}, preferring {@code prefer} when set. */
    @Nullable
    public static TownRecord getTownAcrossWorlds(@Nonnull UUID townId, @Nullable TownManager prefer) {
        if (prefer != null) {
            TownRecord local = prefer.getTown(townId);
            if (local != null) {
                return local;
            }
        }
        for (TownManager tm : TOWN_MANAGERS.values()) {
            if (tm == prefer) {
                continue;
            }
            TownRecord t = tm.getTown(townId);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /** Manager that owns this town's persistence (keyed by {@link TownRecord#getWorldName()}). */
    @Nullable
    public static TownManager townManagerForTown(@Nonnull TownRecord town) {
        return TOWN_MANAGERS.get(town.getWorldName());
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
        AssemblyMarkerSpawner.purgeAllInWorld(world);
        TownBorderMapOverlayService.stopWorld(world);
        AssemblyWorldRegistry.unloadWorld(world.getName());
        SprinklerWateringService.clearWorldState(world.getName());
        ShopSpotDailyRerollService.clearWorldState(world.getName());
        CitizenDawnRevivalService.clearWorldState(world.getName());
        if (PersistentWorldSupport.isTemporaryInstance(world)) {
            TOWN_MANAGERS.remove(world.getName());
            POI_REGISTRIES.remove(world.getName());
            PATH_TOOL_REGISTRIES.remove(world.getName());
            PATH_NAV_GRAPH_SERVICES.remove(world.getName());
            PATROL_ROUTE_REGISTRIES.remove(world.getName());
            SHOP_SPOT_REGISTRIES.remove(world.getName());
            TOURIST_PORTAL_REGISTRIES.remove(world.getName());
            WORLD_NPC_REGISTRIES.remove(world.getName());
            WorldNpcExistenceReconcile.clearWorld(world.getName());
            WorldDifficultyPersistence.unloadWorld(world);
            TownsfolkPoolPersistence.unloadWorld(world);
            return;
        }
        TownManager tm = TOWN_MANAGERS.remove(world.getName());
        if (tm != null) {
            tm.saveToDisk();
        }
        PlotLinkReconcileService.clearWorldState(world.getName());
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
        PATH_NAV_GRAPH_SERVICES.remove(world.getName());
        PatrolRouteRegistry patrolReg = PATROL_ROUTE_REGISTRIES.remove(world.getName());
        if (patrolReg != null) {
            AetherhavenPlugin p3 = AetherhavenPlugin.get();
            if (p3 != null) {
                PatrolRoutePersistence.save(world, p3, patrolReg);
            }
        }
        ShopSpotRegistry shopReg = SHOP_SPOT_REGISTRIES.remove(world.getName());
        if (shopReg != null) {
            AetherhavenPlugin p4 = AetherhavenPlugin.get();
            if (p4 != null) {
                ShopSpotPersistence.save(world, p4, shopReg);
            }
        }
        TouristPortalRegistry touristReg = TOURIST_PORTAL_REGISTRIES.remove(world.getName());
        if (touristReg != null) {
            AetherhavenPlugin p5 = AetherhavenPlugin.get();
            if (p5 != null) {
                TouristPortalPersistence.save(world, p5, touristReg);
            }
        }
        WorldNpcRegistry worldNpcReg = WORLD_NPC_REGISTRIES.remove(world.getName());
        if (worldNpcReg != null) {
            AetherhavenPlugin p6 = AetherhavenPlugin.get();
            if (p6 != null) {
                WorldNpcPersistence.save(world, p6, worldNpcReg);
            }
        }
        WorldNpcExistenceReconcile.clearWorld(world.getName());
        WorldDifficultyPersistence.unloadWorld(world);
        TownsfolkPoolPersistence.unloadWorld(world);
    }

    @Nonnull
    public static WorldDifficultyState getOrLoadWorldDifficulty(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return WorldDifficultyPersistence.getOrLoad(world, plugin);
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
            for (var e : PATROL_ROUTE_REGISTRIES.entrySet()) {
                World w = e.getValue().getWorld();
                PatrolRoutePersistence.save(w, p, e.getValue());
            }
            for (var e : SHOP_SPOT_REGISTRIES.entrySet()) {
                World w = e.getValue().getWorld();
                ShopSpotPersistence.save(w, p, e.getValue());
            }
            for (var e : TOURIST_PORTAL_REGISTRIES.entrySet()) {
                World w = e.getValue().getWorld();
                TouristPortalPersistence.save(w, p, e.getValue());
            }
            for (var e : WORLD_NPC_REGISTRIES.entrySet()) {
                World w = e.getValue().getWorld();
                WorldNpcPersistence.save(w, p, e.getValue());
            }
        }
        WorldDifficultyPersistence.saveAll();
        TownsfolkPoolPersistence.saveAll();
    }

    public static void bootstrapWorld(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        if (PersistentWorldSupport.isTemporaryInstance(world)) {
            return;
        }
        WorldDifficultyPersistence.loadFromDisk(world, plugin);
        refreshTownDataFromDisk(world, plugin);
        getOrCreateTownManager(world, plugin);
        getOrCreatePoiRegistry(world, plugin);
        getOrCreatePathToolRegistry(world, plugin);
        getOrCreatePatrolRouteRegistry(world, plugin);
        getOrCreateShopSpotRegistry(world, plugin);
        getOrCreateTouristPortalRegistry(world, plugin);
        getOrCreateWorldNpcRegistry(world, plugin);
        getOrCreatePathNavGraphService(world);
        TownNpcMigration.ensureElderBindingsOnWorldThread(world, plugin);
        WorldNpcSpawnService.reconcileAfterWorldLoad(world, plugin);
        TouristReconcileService.scheduleAfterWorldLoad(world, plugin);
        ElderReconcileService.scheduleAfterWorldLoad(world, plugin);
        InnkeeperSpawnService.reconcileAfterWorldLoad(world, plugin);
        InnPoolService.reconcileAfterWorldLoad(world, plugin);
        TownsfolkSpawnService.reconcileAfterWorldLoad(world, plugin);
        PlotAssemblyService.scheduleRehydrateAfterWorldLoad(world, plugin);
        com.hexvane.aetherhaven.shopspot.ShopSpotBootstrap.reconcileAfterWorldLoad(world, plugin);
        PlotLinkReconcileService.scheduleAfterWorldLoad(world, plugin);
        WorkplaceJobPlotReconcileService.scheduleAfterWorldLoad(world, plugin);
        TownBorderMapOverlayService.startWorld(world);
        world.getWorldMapManager().addMarkerProvider("aetherhaven-towns", TownMapMarkerProvider.INSTANCE);
        world.getWorldMapManager().addMarkerProvider("aetherhaven-raid-quests", RaidQuestMarkerProvider.INSTANCE);
        TownSharedMapMarkerService.purgeLegacyStoredMarkers(world);
    }
}
