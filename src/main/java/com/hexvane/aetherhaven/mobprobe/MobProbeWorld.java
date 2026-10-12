package com.hexvane.aetherhaven.mobprobe;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3d;

/**
 * The ground the creature check stands on when there is no town: a flat, throwaway world with a real floor, where the
 * land the check uses is kept awake for as long as the check runs. Nothing from Aetherhaven is used here.
 */
public final class MobProbeWorld {
    /** Y of the top ground block; a creature stands one above it. Same ground as the test town's world. */
    public static final int SURFACE = 80;
    public static final String ENVIRONMENT = "Env_Zone1_Plains";

    private MobProbeWorld() {}

    /** A new flat world that is never saved, has no natural spawns and never puts its land to sleep. */
    public static CompletableFuture<World> create(String prefix) {
        String name = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        Universe universe = Universe.get();
        WorldConfig config = new WorldConfig();
        config.setWorldGenProvider(new FlatWorldGenProvider(FlatWorldGenProvider.DEFAULT_TINT, new FlatWorldGenProvider.Layer[] {
            new FlatWorldGenProvider.Layer(0, 76, ENVIRONMENT, "Rock_Stone"),
            new FlatWorldGenProvider.Layer(76, 80, ENVIRONMENT, "Soil_Dirt"),
            new FlatWorldGenProvider.Layer(80, 81, ENVIRONMENT, "Soil_Grass"),
            new FlatWorldGenProvider.Layer(81, Integer.MAX_VALUE, ENVIRONMENT, BlockType.EMPTY_KEY)}));
        config.setSpawnProvider(new GlobalSpawnProvider(new Transform(0.5, SURFACE + 1, 0.5)));
        config.setTicking(true);
        config.setBlockTicking(true);
        config.setGameTimePaused(true);
        config.setSpawningNPC(false);
        config.setSavingPlayers(false);
        config.setCanSaveChunks(false);
        config.setSaveNewChunks(false);
        config.setCanUnloadChunks(false);
        config.setDeleteOnRemove(true);
        config.setDeleteOnUniverseStart(true);
        config.markChanged();
        return universe.makeWorld(name, universe.validateWorldPath(name), config);
    }

    public static void remove(World world) {
        Universe.get().removeWorld(world.getName());
    }

    /**
     * Loads and wakes every chunk column and section around the given places, from the ground to well above a
     * creature's head. Call from any thread; the answer completes when the land is awake.
     */
    public static CompletableFuture<Void> wake(World world, List<Vector3d> places, int marginBlocks) {
        List<CompletableFuture<?>> loads = new ArrayList<>();
        var chunks = world.getChunkStore();
        java.util.Set<Long> columns = new java.util.HashSet<>();
        for (Vector3d place : places) {
            int minX = ChunkUtil.chunkCoordinate((int) Math.floor(place.x) - marginBlocks), maxX = ChunkUtil.chunkCoordinate((int) Math.floor(place.x) + marginBlocks);
            int minZ = ChunkUtil.chunkCoordinate((int) Math.floor(place.z) - marginBlocks), maxZ = ChunkUtil.chunkCoordinate((int) Math.floor(place.z) + marginBlocks);
            int minY = ChunkUtil.chunkCoordinate(Math.max(ChunkUtil.MIN_Y, (int) Math.floor(place.y) - 34)), maxY = ChunkUtil.chunkCoordinate((int) Math.floor(place.y) + 34);
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    if (!columns.add(ChunkUtil.indexChunk(cx, cz))) continue;
                    loads.add(chunks.getChunkReferenceAsync(ChunkUtil.indexChunk(cx, cz), GetChunkFlags.SET_TICKING));
                    for (int cy = minY; cy <= maxY; cy++) loads.add(chunks.getChunkSectionReferenceAsync(cx, cy, cz, GetChunkFlags.SET_TICKING));
                }
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    /** Midday or midnight. Call on the world thread. */
    public static void setTime(World world, boolean night) {
        var store = world.getEntityStore().getStore();
        store.getResource(WorldTimeResource.getResourceType()).setDayTime(night ? 0.0f : 0.5f, world, store);
    }
}
