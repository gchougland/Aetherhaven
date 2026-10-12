package com.hexvane.aetherhaven.testtown;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownSaveCoordinator;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider;
import com.hypixel.hytale.server.core.util.io.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The test town's own world: a named, flat, saved world that is separate from every world a player made. Nothing here
 * ever touches another world.
 */
public final class TestTownWorlds {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private TestTownWorlds() {}

    public static World loaded(TestTownSpec.WorldSpec spec) {
        return Universe.get().getWorld(spec.name());
    }

    /** True when the world is loaded or its folder is on disk. */
    public static boolean exists(TestTownSpec.WorldSpec spec) {
        return loaded(spec) != null || Universe.get().isWorldLoadable(spec.name());
    }

    /** Returns the loaded test world, loading it from disk or creating it when needed. */
    public static CompletableFuture<World> ensure(TestTownSpec.WorldSpec spec) {
        Universe universe = Universe.get();
        World existing = universe.getWorld(spec.name());
        if (existing != null) return CompletableFuture.completedFuture(existing);
        try {
            if (universe.isWorldLoadable(spec.name())) return universe.loadWorld(spec.name());
            Path path = universe.validateWorldPath(spec.name());
            return universe.makeWorld(spec.name(), path, newConfig(spec)).thenCompose(world ->
                CompletableFuture.supplyAsync(() -> {
                    var store = world.getEntityStore().getStore();
                    // A new world opens at dawn; start at midday so the first look at the town is in daylight.
                    store.getResource(WorldTimeResource.getResourceType()).setDayTime(0.5f, world, store);
                    return world;
                }, world));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static WorldConfig newConfig(TestTownSpec.WorldSpec spec) {
        WorldConfig config = new WorldConfig();
        config.setDisplayName(spec.displayName());
        List<FlatWorldGenProvider.Layer> layers = new ArrayList<>();
        int from = 0;
        for (TestTownSpec.Layer layer : spec.layers()) {
            if (BlockType.getAssetMap().getAsset(layer.block()) == null)
                throw new IllegalStateException("Unknown ground block " + layer.block());
            layers.add(new FlatWorldGenProvider.Layer(from, from + layer.thickness(), spec.environment(), layer.block()));
            from += layer.thickness();
        }
        // The air above the ground carries the environment too, so weather and daylight match a normal overworld.
        // The generator trims the top of this layer to the height of the world.
        layers.add(new FlatWorldGenProvider.Layer(from, Integer.MAX_VALUE, spec.environment(), BlockType.EMPTY_KEY));
        config.setWorldGenProvider(new FlatWorldGenProvider(FlatWorldGenProvider.DEFAULT_TINT,
            layers.toArray(FlatWorldGenProvider.Layer[]::new)));
        config.setSpawnProvider(new GlobalSpawnProvider(new Transform(0.5, spec.surfaceY() + 1, 8.5)));
        config.setGameMode(GameMode.Creative);
        // A world a person plays in: it ticks, time passes, villagers spawn, and everything is saved.
        config.setTicking(true);
        config.setBlockTicking(true);
        config.setGameTimePaused(false);
        config.setSpawningNPC(spec.naturalSpawns());
        config.setSavingPlayers(true);
        config.setCanSaveChunks(true);
        config.setSaveNewChunks(true);
        config.setCanUnloadChunks(true);
        config.setDeleteOnRemove(false);
        config.setDeleteOnUniverseStart(false);
        config.setPvpEnabled(false);
        config.markChanged();
        return config;
    }

    /**
     * Loads every chunk column and chunk section of a block box and sets them ticking, so blocks, block entities and
     * villagers can be placed and read there. Call on the world thread. Used only inside the test world, and only while
     * the town is being built or checked; nothing is kept loaded afterwards.
     */
    public static CompletableFuture<Void> loadBox(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<CompletableFuture<?>> loads = new ArrayList<>();
        var chunks = world.getChunkStore();
        for (int cx = ChunkUtil.chunkCoordinate(minX); cx <= ChunkUtil.chunkCoordinate(maxX); cx++) {
            for (int cz = ChunkUtil.chunkCoordinate(minZ); cz <= ChunkUtil.chunkCoordinate(maxZ); cz++) {
                loads.add(chunks.getChunkReferenceAsync(ChunkUtil.indexChunk(cx, cz), GetChunkFlags.SET_TICKING));
                for (int cy = ChunkUtil.chunkCoordinate(Math.max(ChunkUtil.MIN_Y, minY)); cy <= ChunkUtil.chunkCoordinate(maxY); cy++)
                    loads.add(chunks.getChunkSectionReferenceAsync(cx, cy, cz, GetChunkFlags.SET_TICKING));
            }
        }
        LOG.atInfo().log("Test town is loading %s chunk columns and sections", loads.size());
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    /**
     * Removes the test world and deletes its folder and the mod's saved records for it. Players standing in it are
     * moved to the default world by the engine first. Must not be called from the test world's own thread.
     *
     * @return what was removed, for the report
     */
    public static String remove(TestTownSpec.WorldSpec spec, AetherhavenPlugin plugin) throws IOException {
        Universe universe = Universe.get();
        String name = spec.name();
        if (name.equalsIgnoreCase(com.hypixel.hytale.server.core.HytaleServer.get().getConfig().getDefaults().getWorld()))
            throw new IllegalStateException("The test town world is the server's default world; refusing to remove it.");
        List<String> done = new ArrayList<>();
        Path folder = universe.validateWorldPath(name);
        boolean onDisk = Files.isDirectory(folder);
        World world = universe.getWorld(name);
        if (world != null) {
            // The engine deletes the folder of a world removed with this flag set.
            world.getWorldConfig().setDeleteOnRemove(true);
            if (!universe.removeWorld(name)) throw new IllegalStateException("Another plugin refused the world removal.");
            done.add("world unloaded");
            // The world goes on ticking for a moment after the mod is told it is leaving, and those last ticks load
            // its town, places and paths back into memory. Clear them again now that the world has stopped, or a
            // world made later under the same name would start with the old town's leftovers.
            AetherhavenWorldRegistries.unloadWorld(world);
        }
        if (Files.isDirectory(folder)) FileUtil.deleteDirectory(folder);
        if (onDisk) done.add("world folder deleted");
        TownSaveCoordinator.awaitPendingWrites();
        // The mod keeps its per world records beside its config. Only this world's folder is deleted.
        Path records = TownManager.pluginData(plugin).resolve("worlds").resolve(name).normalize();
        Path root = TownManager.pluginData(plugin).resolve("worlds").normalize();
        if (records.startsWith(root) && !records.equals(root) && Files.isDirectory(records)) {
            FileUtil.deleteDirectory(records);
            done.add("town records deleted");
        }
        LOG.atInfo().log("Test town world %s removed: %s", name, done);
        return done.isEmpty() ? "nothing to remove" : String.join(", ", done);
    }
}
