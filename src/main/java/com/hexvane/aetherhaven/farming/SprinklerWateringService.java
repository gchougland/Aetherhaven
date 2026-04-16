package com.hexvane.aetherhaven.farming;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.SprinklerBlock;
import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Automatic watering uses the same in-game <strong>morning window</strong> and <strong>calendar epoch day</strong> as
 * {@link com.hexvane.aetherhaven.inn.InnPoolService} (see config {@code InnPoolMorningStartHour} /
 * {@code InnPoolMorningEndHour}). Soil updates mirror {@code UseWateringCanInteraction}.
 */
public final class SprinklerWateringService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Horizontal block extent of one chunk (see HytaleModding block-components guide: {@code local + getX() * size}).
     */
    private static final int CHUNK_HORIZONTAL_BLOCK_SIZE = 32;

    /** Per world: last calendar game day ({@link java.time.LocalDate#toEpochDay()}) we ran automatic morning watering. */
    private static final ConcurrentHashMap<String, Long> LAST_AUTOMATIC_SPRINKLER_CALENDAR_DAY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> LAST_TICK_GAME_EPOCH_SECOND = new ConcurrentHashMap<>();

    private static final long WATER_DURATION_SECONDS = 86400L;

    private SprinklerWateringService() {}

    public static void clearWorldState(@Nonnull String worldName) {
        LAST_AUTOMATIC_SPRINKLER_CALENDAR_DAY.remove(worldName);
        LAST_TICK_GAME_EPOCH_SECOND.remove(worldName);
    }

    /**
     * Throttled to once per game-second per world; runs automatic pass when in the inn morning window and the calendar
     * day has not been watered yet.
     */
    public static void tickThrottled(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull AetherhavenPlugin plugin) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        long sec = wtr.getGameTime().getEpochSecond();
        String w = world.getName();
        Long lastSec = LAST_TICK_GAME_EPOCH_SECOND.put(w, sec);
        if (lastSec != null && lastSec == sec) {
            return;
        }
        onGameTimeTick(world, store, plugin);
    }

    static void onGameTimeTick(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull AetherhavenPlugin plugin) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        int morningStart = plugin.getConfig().get().getInnPoolMorningStartHour();
        int morningEndEx = plugin.getConfig().get().getInnPoolMorningEndHourExclusive();
        if (!isMorningWindowForSprinkler(wtr, morningStart, morningEndEx)) {
            return;
        }
        long calendarEpochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        String w = world.getName();
        Long lastMorningDay = LAST_AUTOMATIC_SPRINKLER_CALENDAR_DAY.get(w);
        if (lastMorningDay != null && lastMorningDay >= calendarEpochDay) {
            return;
        }
        // Claim this calendar day before queueing — same second /tick ordering as InnPoolService using world.execute.
        LAST_AUTOMATIC_SPRINKLER_CALENDAR_DAY.put(w, calendarEpochDay);
        world.execute(() -> {
            try {
                waterAllSprinklers(world, store);
            } catch (Throwable t) {
                LOGGER.at(Level.SEVERE).withCause(t).log(
                    "[Aetherhaven] Sprinkler automatic watering failed world=%s — will retry another morning",
                    w
                );
                LAST_AUTOMATIC_SPRINKLER_CALENDAR_DAY.remove(w, calendarEpochDay);
            }
        });
    }

    /** Same rules as {@link com.hexvane.aetherhaven.inn.InnPoolService} morning checks. */
    private static boolean isMorningWindowForSprinkler(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        if (isInMorningHourWindow(wtr, morningStartHour, morningEndExclusive)) {
            return true;
        }
        return wtr.isScaledDayTimeWithinRange(0.18f, 0.42f);
    }

    private static boolean isInMorningHourWindow(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        int h = wtr.getCurrentHour();
        int start = Math.max(0, Math.min(23, morningStartHour));
        int end = morningEndExclusive;
        if (end <= start) {
            end = Math.min(start + 6, 24);
        }
        return h >= start && h < end;
    }

    /**
     * Use (F) on a sprinkler block: runs the same soil pass as the automatic morning job for that one sprinkler.
     *
     * @return number of soil cells that accepted watering (may be 0 if no valid farmland), or -1 if the block is not
     *     a sprinkler
     */
    public static int activateSprinklerAt(@Nonnull World world, @Nonnull Store<EntityStore> entityStore, @Nonnull Vector3i pos) {
        return activateSprinklerAt(world, entityStore, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * @return watered cells, or -1 if no {@link SprinklerBlock} at (x,y,z)
     */
    public static int activateSprinklerAt(@Nonnull World world, @Nonnull Store<EntityStore> entityStore, int x, int y, int z) {
        WorldTimeResource wtr = entityStore.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return -1;
        }
        Instant wateredUntil = wtr.getGameTime().plus(WATER_DURATION_SECONDS, ChronoUnit.SECONDS);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunk(chunkIndex);
        if (chunk == null) {
            return -1;
        }
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        SprinklerBlock sb = blockRef != null && blockRef.isValid()
            ? chunkStore.getComponent(blockRef, SprinklerBlock.getComponentType())
            : null;
        if (sb == null) {
            return -1;
        }
        int watered = waterSprinklerCells(world, x, y, z, sb.getTier(), wateredUntil);
        SprinklerActivationEffects.playAtSprinklerBlock(entityStore, x, y, z);
        return watered;
    }

    static void waterAllSprinklers(@Nonnull World world, @Nonnull Store<EntityStore> entityStore) {
        Instant gameTime = entityStore.getResource(WorldTimeResource.getResourceType()).getGameTime();
        Instant wateredUntil = gameTime.plus(WATER_DURATION_SECONDS, ChronoUnit.SECONDS);
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Query<ChunkStore> q = Query.and(SprinklerBlock.getComponentType(), BlockModule.BlockStateInfo.getComponentType());
        chunkStore.forEachChunk(q, (archetypeChunk, commandBuffer) -> {
            for (int i = 0; i < archetypeChunk.size(); i++) {
                Ref<ChunkStore> blockRef = archetypeChunk.getReferenceTo(i);
                if (blockRef == null || !blockRef.isValid()) {
                    continue;
                }
                SprinklerBlock sb = commandBuffer.getComponent(blockRef, SprinklerBlock.getComponentType());
                BlockModule.BlockStateInfo bsi = commandBuffer.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
                if (sb == null || bsi == null) {
                    continue;
                }
                WorldChunk columnChunk = commandBuffer.getComponent(bsi.getChunkRef(), WorldChunk.getComponentType());
                if (columnChunk == null) {
                    continue;
                }
                int index = bsi.getIndex();
                int lx = ChunkUtil.xFromBlockInColumn(index);
                int ly = ChunkUtil.yFromBlockInColumn(index);
                int lz = ChunkUtil.zFromBlockInColumn(index);
                int sx = lx + columnChunk.getX() * CHUNK_HORIZONTAL_BLOCK_SIZE;
                int sy = ly;
                int sz = lz + columnChunk.getZ() * CHUNK_HORIZONTAL_BLOCK_SIZE;
                waterSprinklerCells(world, sx, sy, sz, sb.getTier(), wateredUntil);
                SprinklerActivationEffects.playAtSprinklerBlock(entityStore, sx, sy, sz);
            }
        });
    }

    /** Waters soil in the Chebyshev radius below the sprinkler block (same as vanilla area). */
    private static int waterSprinklerCells(
        @Nonnull World world,
        int sprinklerX,
        int sprinklerY,
        int sprinklerZ,
        int tier,
        @Nonnull Instant wateredUntil
    ) {
        int soilY = sprinklerY - 1;
        int r = SprinklerBlock.radiusForTier(tier);
        int watered = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (waterBlockAt(world, sprinklerX + dx, soilY, sprinklerZ + dz, wateredUntil)) {
                    watered++;
                }
            }
        }
        return watered;
    }

    /**
     * Ported from {@code UseWateringCanInteraction#waterBlockAt}, with the same extras as
     * {@code com.hexvane.dragonlings.behaviors.BlueDragonlingWaterBehavior}: {@link BlockModule#ensureBlockEntity} when
     * the block ECS entity is missing, and a default {@link TilledSoilBlock} when the block exists but the component was
     * never attached (dry farmland).
     */
    @SuppressWarnings("deprecation")
    private static boolean waterBlockAt(@Nonnull World world, int x, int y, int z, @Nonnull Instant wateredUntil) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk worldChunk = world.getChunk(chunkIndex);
        if (worldChunk == null) {
            return false;
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Ref<ChunkStore> blockRef = worldChunk.getBlockComponentEntity(x, y, z);
        if (blockRef == null) {
            blockRef = BlockModule.ensureBlockEntity(worldChunk, x, y, z);
        }

        if (blockRef != null && blockRef.isValid()) {
            TilledSoilBlock soil = chunkStore.getComponent(blockRef, TilledSoilBlock.getComponentType());
            if (soil == null) {
                BlockType bt = worldChunk.getBlockType(x, y, z);
                if (bt != null && isFarmlandOrSoilBlock(bt)) {
                    soil = new TilledSoilBlock(false, false, false, null, null);
                    chunkStore.addComponent(blockRef, TilledSoilBlock.getComponentType(), soil);
                }
            }
            if (soil != null) {
                soil.setWateredUntil(wateredUntil);
                worldChunk.setTicking(x, y, z, true);
                scheduleBlockSectionTick(worldChunk, x, y, z, wateredUntil);
                worldChunk.setTicking(x, y + 1, z, true);
                return true;
            }
        }

        Ref<ChunkStore> soilBlockRef = worldChunk.getBlockComponentEntity(x, y - 1, z);
        if (soilBlockRef == null) {
            soilBlockRef = BlockModule.ensureBlockEntity(worldChunk, x, y - 1, z);
        }
        if (soilBlockRef != null && soilBlockRef.isValid()) {
            TilledSoilBlock soil = chunkStore.getComponent(soilBlockRef, TilledSoilBlock.getComponentType());
            if (soil == null) {
                BlockType bt = worldChunk.getBlockType(x, y - 1, z);
                if (bt != null && isFarmlandOrSoilBlock(bt)) {
                    soil = new TilledSoilBlock(false, false, false, null, null);
                    chunkStore.addComponent(soilBlockRef, TilledSoilBlock.getComponentType(), soil);
                }
            }
            if (soil != null) {
                soil.setWateredUntil(wateredUntil);
                scheduleBlockSectionTick(worldChunk, x, y - 1, z, wateredUntil);
                worldChunk.setTicking(x, y - 1, z, true);
                worldChunk.setTicking(x, y, z, true);
                return true;
            }
        }
        return false;
    }

    /** Matches dragonlings farmland scan: tilled/farm soil or any block with {@link BlockType#getFarming()}. */
    private static boolean isFarmlandOrSoilBlock(@Nonnull BlockType blockType) {
        if (blockType.getFarming() != null) {
            return true;
        }
        String id = blockType.getId();
        if (id == null) {
            return false;
        }
        return id.contains("Tilled") || id.contains("Farmland") || id.contains("Soil");
    }

    /**
     * Same as {@code UseWateringCanInteraction#waterBlockAt}: ticks are scheduled on the section's {@link
     * com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection}. {@link
     * com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk#getSectionAtBlockY(int)} is still the accessor
     * vanilla uses until a non-deprecated entry point exists.
     */
    @SuppressWarnings("deprecation")
    private static void scheduleBlockSectionTick(
        @Nonnull WorldChunk worldChunk,
        int x,
        int y,
        int z,
        @Nonnull Instant when
    ) {
        worldChunk.getBlockChunk().getSectionAtBlockY(y).scheduleTick(ChunkUtil.indexBlock(x, y, z), when);
    }
}
