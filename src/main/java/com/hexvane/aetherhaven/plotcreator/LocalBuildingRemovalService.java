package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.prefabmaterials.PrefabMaterialsWriter;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintChunkUtil;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownDissolutionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownWorldFile;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/** Removes local custom buildings from disk and clears matching plots across all worlds. */
public final class LocalBuildingRemovalService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public enum Result {
        SUCCESS,
        NOT_CUSTOM,
        SESSION_ACTIVE,
        CHUNKS_NOT_LOADED,
        IO_ERROR
    }

    public record PlotCount(int total, int unloadedWorldPlots) {}

    private record TownPlot(@Nonnull TownRecord town, @Nonnull PlotInstance plot) {}

    private LocalBuildingRemovalService() {}

    @Nonnull
    public static PlotCount countPlots(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        String target = constructionId.trim();
        if (target.isEmpty()) {
            return new PlotCount(0, 0);
        }
        int total = 0;
        int unloaded = 0;
        Path worldsRoot = plugin.getDataDirectory().resolve("worlds");
        if (!Files.isDirectory(worldsRoot)) {
            return new PlotCount(0, 0);
        }
        try (Stream<Path> dirs = Files.list(worldsRoot)) {
            for (Path worldDir : dirs.toList()) {
                if (!Files.isDirectory(worldDir)) {
                    continue;
                }
                String sanitizedDir = worldDir.getFileName().toString();
                Path townsFile = worldDir.resolve("towns.json");
                int inFile = countMatchingPlotsInTownFile(catalog, target, townsFile);
                if (inFile <= 0) {
                    continue;
                }
                total += inFile;
                if (findLoadedWorldBySanitizedDir(sanitizedDir) == null) {
                    unloaded += inFile;
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to count plots for local building removal");
        }
        return new PlotCount(total, unloaded);
    }

    /**
     * Removes all plots of the building type and deletes local custom files. Call from a world thread when possible;
     * loaded-world plot cleanup is scheduled on each world's thread.
     */
    @Nonnull
    public static Result remove(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        String target = constructionId.trim();
        if (target.isEmpty()) {
            return Result.NOT_CUSTOM;
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        if (!catalog.isCustomConstruction(target)) {
            return Result.NOT_CUSTOM;
        }
        if (PlotCreatorSessions.isConstructionInActiveSession(target)) {
            return Result.SESSION_ACTIVE;
        }
        if (!areLoadedWorldPlotsFullyLoaded(plugin, catalog, target)) {
            return Result.CHUNKS_NOT_LOADED;
        }

        List<CompletableFuture<Void>> worldTasks = new ArrayList<>();
        for (World world : Universe.get().getWorlds().values()) {
            if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
                continue;
            }
            if (world.isInThread()) {
                removePlotsInLoadedWorld(world, plugin, catalog, target);
                continue;
            }
            CompletableFuture<Void> task = new CompletableFuture<>();
            worldTasks.add(task);
            world.execute(
                () -> {
                    try {
                        removePlotsInLoadedWorld(world, plugin, catalog, target);
                        task.complete(null);
                    } catch (Exception e) {
                        task.completeExceptionally(e);
                    }
                }
            );
        }
        if (!worldTasks.isEmpty()) {
            try {
                CompletableFuture.allOf(worldTasks.toArray(CompletableFuture[]::new)).join();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to remove plots for local building %s", target);
                return Result.IO_ERROR;
            }
        }

        int strippedFromUnloaded = stripPlotsFromUnloadedWorldFiles(plugin, catalog, target);
        if (strippedFromUnloaded > 0) {
            LOGGER.atInfo().log(
                "Removed %s plot row(s) from save data in unloaded worlds for %s",
                strippedFromUnloaded,
                target
            );
        }

        try {
            deleteLocalFiles(plugin.getDataDirectory(), target, catalog.get(target), plugin);
            plugin.reloadConfigsAndAssetCatalogs();
            return Result.SUCCESS;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to delete local building files for %s", target);
            return Result.IO_ERROR;
        }
    }

    static int countMatchingPlotsInTownFile(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String constructionId,
        @Nonnull Path townsFile
    ) throws IOException {
        TownWorldFile file = TownWorldFile.readOrEmpty(townsFile);
        int count = 0;
        for (TownRecord town : file.getTowns()) {
            for (PlotInstance plot : town.getPlotInstances()) {
                if (catalog.matchesGameplayConstruction(plot.getConstructionId(), constructionId)) {
                    count++;
                }
            }
        }
        return count;
    }

    static void deleteLocalFiles(
        @Nonnull Path dataDir,
        @Nonnull String constructionId,
        @javax.annotation.Nullable ConstructionDefinition def,
        @javax.annotation.Nullable AetherhavenPlugin plugin
    ) throws IOException {
        Files.deleteIfExists(CustomBuildingsPaths.buildingFile(dataDir, constructionId));
        Files.deleteIfExists(PrefabMaterialsWriter.outputFile(dataDir, constructionId));
        Files.deleteIfExists(CustomBuildingsPaths.iconFile(dataDir, constructionId));
        if (plugin != null) {
            CustomBuildingIconAssetRegistry.unregisterIconForConstruction(plugin, constructionId);
        }
        String prefabKey = def != null ? def.getPrefabPath() : constructionId + ".prefab.json";
        Path prefab = CustomBuildingsPaths.resolvePrefabFile(dataDir, prefabKey);
        if (prefab == null) {
            prefab = CustomBuildingsPaths.prefabsDirectory(dataDir).resolve(constructionId + ".prefab.json");
        }
        Files.deleteIfExists(prefab);
    }

    private static boolean areLoadedWorldPlotsFullyLoaded(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String constructionId
    ) {
        for (World world : Universe.get().getWorlds().values()) {
            if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
                continue;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            for (TownRecord town : tm.allTowns()) {
                for (PlotInstance plot : town.getPlotInstances()) {
                    if (!catalog.matchesGameplayConstruction(plot.getConstructionId(), constructionId)) {
                        continue;
                    }
                    if (!PlotFootprintChunkUtil.isPlotFullyLoaded(world, plot)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void removePlotsInLoadedWorld(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String constructionId
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        List<TownPlot> matches = new ArrayList<>();
        for (TownRecord town : tm.allTowns()) {
            for (PlotInstance plot : town.getPlotInstances()) {
                if (catalog.matchesGameplayConstruction(plot.getConstructionId(), constructionId)) {
                    matches.add(new TownPlot(town, plot));
                }
            }
        }
        Set<UUID> updatedTowns = new LinkedHashSet<>();
        for (TownPlot match : matches) {
            TownDissolutionService.clearPlotFromWorld(world, plugin, match.town(), match.plot(), entityStore, reg);
            FestivalService.onFestivalSquareRemoved(world, entityStore, plugin, tm, match.town(), match.plot());
            match.town().removePlotInstance(match.plot().getPlotId());
            updatedTowns.add(match.town().getTownId());
        }
        for (TownRecord town : tm.allTowns()) {
            if (updatedTowns.contains(town.getTownId())) {
                tm.updateTown(town);
            }
        }
    }

    private static int stripPlotsFromUnloadedWorldFiles(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String constructionId
    ) {
        Path worldsRoot = plugin.getDataDirectory().resolve("worlds");
        if (!Files.isDirectory(worldsRoot)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> dirs = Files.list(worldsRoot)) {
            for (Path worldDir : dirs.toList()) {
                if (!Files.isDirectory(worldDir)) {
                    continue;
                }
                String sanitizedDir = worldDir.getFileName().toString();
                if (findLoadedWorldBySanitizedDir(sanitizedDir) != null) {
                    continue;
                }
                if (sanitizedDir.startsWith(InstancesPlugin.INSTANCE_PREFIX)) {
                    continue;
                }
                Path townsFile = worldDir.resolve("towns.json");
                if (!Files.isRegularFile(townsFile)) {
                    continue;
                }
                try {
                    TownWorldFile file = TownWorldFile.readOrEmpty(townsFile);
                    boolean changed = false;
                    for (TownRecord town : file.getTowns()) {
                        List<PlotInstance> plots = town.getPlotInstances();
                        int before = plots.size();
                        plots.removeIf(p -> catalog.matchesGameplayConstruction(p.getConstructionId(), constructionId));
                        removed += before - plots.size();
                        if (before != plots.size()) {
                            changed = true;
                        }
                    }
                    if (changed) {
                        file.writeAtomic(townsFile);
                    }
                } catch (IOException e) {
                    LOGGER.atWarning().withCause(e).log("Failed to strip plots from %s", townsFile);
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to walk worlds directory for plot strip");
        }
        return removed;
    }

    @javax.annotation.Nullable
    private static World findLoadedWorldBySanitizedDir(@Nonnull String sanitizedDir) {
        for (World world : Universe.get().getWorlds().values()) {
            if (sanitizeWorldDirName(world.getName()).equals(sanitizedDir)) {
                return world;
            }
        }
        return null;
    }

    @Nonnull
    static String sanitizeWorldDirName(@Nonnull String worldName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < worldName.length(); i++) {
            char c = worldName.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.isEmpty() ? "world" : sb.toString();
    }
}
