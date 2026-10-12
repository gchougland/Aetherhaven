package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The real world as the raid spawn search sees it. Reads only what is in memory: it never loads a chunk and never
 * sets one ticking. World thread only, and made fresh for each search.
 */
final class WorldRaidSpawnTerrain implements RaidSpawnTerrain {
    private static final int WORLD_HEIGHT = 320;

    private final World world;
    /** True for placing creatures: the land must be simulating. False for a camp site: in memory is enough. */
    private final boolean simulating;
    private final Long2BooleanOpenHashMap liveColumns = new Long2BooleanOpenHashMap();
    private final LongSet claimedChunks = new LongOpenHashSet();
    private final List<PlotFootprintRecord> buildings = new ArrayList<>();

    private WorldRaidSpawnTerrain(@Nonnull World world, boolean simulating) {
        this.world = world;
        this.simulating = simulating;
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        String worldName = world.getName();
        for (TownRecord town : AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).allTowns()) {
            if (!worldName.equals(town.getWorldName())) {
                continue;
            }
            claimedChunks.addAll(TownTerritoryClaims.toChunkIndexSet(town));
            for (PlotInstance plot : town.getPlotInstances()) {
                buildings.add(plot.toFootprint());
            }
        }
    }

    /** For placing creatures: only land that is simulating right now counts. */
    @Nonnull
    static WorldRaidSpawnTerrain simulating(@Nonnull World world) {
        return new WorldRaidSpawnTerrain(world, true);
    }

    /** For choosing a site that is pasted later: land in memory counts, awake or not. */
    @Nonnull
    static WorldRaidSpawnTerrain inMemory(@Nonnull World world) {
        return new WorldRaidSpawnTerrain(world, false);
    }

    @Override
    public boolean live(int x, int z) {
        long chunk = ChunkUtil.indexChunkFromBlock(x, z);
        if (liveColumns.containsKey(chunk)) {
            return liveColumns.get(chunk);
        }
        boolean live = simulating
            ? ChunkSectionBlockUtil.worldChunkIfTicking(world, chunk) != null
            : ChunkSectionBlockUtil.chunkRefIfInMemory(world, chunk) != null;
        liveColumns.put(chunk, live);
        return live;
    }

    @Override
    public int surface(int x, int z) {
        int height = ChunkSectionBlockUtil.surfaceHeightIfInMemory(world, x, z);
        return height == ChunkSectionBlockUtil.NO_SURFACE ? NO_SURFACE : height;
    }

    /**
     * The game decides by section, not by column, whether something added to the world stays in it: an entity added
     * to a section that is not ticking is stored again at once. This is the same test the game's own spawning uses.
     */
    @Override
    public boolean liveCell(int x, int y, int z) {
        if (y < 0 || y >= WORLD_HEIGHT) {
            return false;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) {
            return false;
        }
        return !simulating
            || !chunkStore.getStore().getArchetype(section).contains(ChunkStore.REGISTRY.getNonTickingComponentType());
    }

    @Override
    public boolean solid(int x, int y, int z) {
        BlockType type = blockType(x, y, z);
        return type != null && type != BlockType.EMPTY && type.getMaterial() == BlockMaterial.Solid;
    }

    @Override
    public boolean open(int x, int y, int z) {
        BlockType type = blockType(x, y, z);
        return type != null && (type == BlockType.EMPTY || type.getMaterial() == BlockMaterial.Empty);
    }

    @Override
    public boolean fluid(int x, int y, int z) {
        if (y < 0 || y >= WORLD_HEIGHT) {
            return false;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) {
            return false;
        }
        FluidSection fluids = chunkStore.getStore().getComponent(section, FluidSection.getComponentType());
        return fluids != null && fluids.getFluidId(x, y, z) != 0;
    }

    @Override
    public boolean building(int x, int z) {
        for (PlotFootprintRecord plot : buildings) {
            if (x >= plot.getMinX() && x <= plot.getMaxX() && z >= plot.getMinZ() && z <= plot.getMaxZ()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean claimed(int x, int z) {
        return claimedChunks.contains(ChunkUtil.indexChunkFromBlock(x, z));
    }

    @Nullable
    private BlockType blockType(int x, int y, int z) {
        if (y < 0 || y >= WORLD_HEIGHT) {
            return null;
        }
        return ChunkSectionBlockUtil.blockType(world, x, y, z);
    }
}
