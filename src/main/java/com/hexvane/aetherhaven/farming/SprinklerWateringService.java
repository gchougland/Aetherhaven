package com.hexvane.aetherhaven.farming;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.SprinklerBlock;
import com.hexvane.aetherhaven.time.AetherhavenMorningWindow;
import com.hexvane.aetherhaven.plot.SprinklerBlock;
import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import org.joml.Vector3i;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Automatic watering uses the shared in-game <strong>morning window</strong> and <strong>calendar epoch day</strong>
 * (see {@link com.hexvane.aetherhaven.config.AetherhavenPluginConfig#getGameMorningStartHour()} and inn visitor refresh).
 * Soil updates mirror {@code UseWateringCanInteraction}.
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
     * Throttled to once per game-second per world; runs automatic pass when in the shared morning window and the calendar
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

    /**
     * {@link com.hexvane.aetherhaven.time.AetherhavenGameTimeCoordinatorSystem} — once per game minute (smooth) or after
     * a discontinuity (with optional catch-up); replaces per-player tick spam.
     */
    public static void scheduleFromHub(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull AetherhavenPlugin plugin) {
        onGameTimeTick(world, store, plugin);
    }

    /**
     * If game time jumps over a configured morning hour, run one automatic watering pass when that morning was missed.
     */
    public static void catchUpAfterTimeJump(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Instant from,
        @Nonnull Instant to
    ) {
        int morningStart = plugin.getConfig().get().getGameMorningStartHour();
        LinkedHashSet<Long> days = new LinkedHashSet<>();
        com.hexvane.aetherhaven.time.GameTimeEpochs.collectEpochDaysWhereMorningStartOccurred(
            from, to, morningStart, WorldTimeResource.ZONE_OFFSET, days
        );
        if (days.isEmpty()) {
            return;
        }
        long maxDay = Long.MIN_VALUE;
        for (Long d : days) {
            maxDay = Math.max(maxDay, d);
        }
        final long claimedDay = maxDay;
        String w = world.getName();
        Long lastMorningDay = LAST_AUTOMATIC_SPRINKLER_CALENDAR_DAY.get(w);
        if (lastMorningDay != null && lastMorningDay >= claimedDay) {
            return;
        }
        LAST_AUTOMATIC_SPRINKLER_CALENDAR_DAY.put(w, claimedDay);
        world.execute(() -> {
            try {
                waterAllSprinklers(world, store);
            } catch (Throwable t) {
                LOGGER.at(Level.SEVERE).withCause(t).log(
                    "[Aetherhaven] Sprinkler jump catch-up watering failed world=%s — will retry",
                    w
                );
                LAST_AUTOMATIC_SPRINKLER_CALENDAR_DAY.remove(w, claimedDay);
            }
        });
    }

    static void onGameTimeTick(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull AetherhavenPlugin plugin) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        int morningStart = plugin.getConfig().get().getGameMorningStartHour();
        int morningEndEx = plugin.getConfig().get().getGameMorningEndHourExclusive();
        if (!isMorningWindowForSprinkler(wtr, morningStart, morningEndEx)) {
            return;
        }
        long calendarEpochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        String w = world.getName();
        Long lastMorningDay = LAST_AUTOMATIC_SPRINKLER_CALENDAR_DAY.get(w);
        if (lastMorningDay != null && lastMorningDay >= calendarEpochDay) {
            return;
        }
        // Claim this calendar day before queueing — same deferred pattern as inn pool tick (world.execute).
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

    /** Same morning-window rules as {@link com.hexvane.aetherhaven.time.AetherhavenMorningWindow}. */
    private static boolean isMorningWindowForSprinkler(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        return AetherhavenMorningWindow.isGameMorning(wtr, morningStartHour, morningEndExclusive);
    }

    /**
     * Use (F) on a sprinkler block: runs the same soil pass as the automatic morning job for that one sprinkler.
     *
     * @return number of soil cells that accepted watering (may be 0 if no valid farmland), or -1 if the block is not
     *     a sprinkler
     */
    public static int activateSprinklerAt(@Nonnull World world, @Nonnull Store<EntityStore> entityStore, @Nonnull Vector3i pos) {
        return activateSprinklerAt(world, entityStore, pos.x(), pos.y(), pos.z());
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
        if (ChunkSectionBlockUtil.resolveTickingChunk(world, x, z) == null) {
            return -1;
        }
        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
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
                Ref<ChunkStore> sectionRef = bsi.getSectionRef();
                ChunkSection chunkSection = commandBuffer.getComponent(sectionRef, ChunkSection.getComponentType());
                if (chunkSection == null) {
                    continue;
                }
                Ref<ChunkStore> columnRef = chunkSection.getChunkColumnReference();
                WorldChunk columnChunk =
                    columnRef != null && columnRef.isValid()
                        ? commandBuffer.getComponent(columnRef, WorldChunk.getComponentType())
                        : null;
                if (columnChunk == null) {
                    continue;
                }
                int index = bsi.getIndex();
                int lx = ChunkUtil.xFromIndex(index);
                int ly = ChunkUtil.yFromIndex(index);
                int lz = ChunkUtil.zFromIndex(index);
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
        int r = Math.min(8, SprinklerBlock.radiusForTier(tier));
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
     * {@code com.hexvane.dragonlings.behaviors.BlueDragonlingWaterBehavior}: {@link BlockEntity#ensureBlockEntity} when
     * the block ECS entity is missing, and a default {@link TilledSoilBlock} when the block exists but the component was
     * never attached (dry farmland).
     */
    private static boolean waterBlockAt(@Nonnull World world, int x, int y, int z, @Nonnull Instant wateredUntil) {
        if (waterSoilCell(world, x, y, z, wateredUntil)) {
            return true;
        }
        return waterSoilCell(world, x, y - 1, z, wateredUntil);
    }

    private static boolean waterSoilCell(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull Instant wateredUntil
    ) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) {
            return false;
        }
        BlockSection blockSection = store.getComponent(section, BlockSection.getComponentType());
        BlockComponentSection blockComponentSection =
            store.getComponent(section, BlockComponentSection.getComponentType());
        if (blockSection == null || blockComponentSection == null) {
            return false;
        }
        int blockIndex = ChunkUtil.indexBlock(x, y, z);
        if (blockSection.getFiller(blockIndex) != FillerBlockUtil.NO_FILLER) {
            return false;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockSection.get(blockIndex));
        if (blockType == null) {
            return false;
        }
        Ref<ChunkStore> blockRef = blockComponentSection.getBlockReference(blockIndex);
        if (blockRef == null || !blockRef.isValid()) {
            blockRef =
                BlockEntity.ensureBlockEntity(
                    store,
                    section,
                    blockComponentSection,
                    x,
                    y,
                    z,
                    blockType,
                    blockSection.getFiller(blockIndex)
                );
        }
        if (blockRef == null || !blockRef.isValid()) {
            return false;
        }
        TilledSoilBlock soil = store.getComponent(blockRef, TilledSoilBlock.getComponentType());
        if (soil == null && isFarmlandOrSoilBlock(blockType)) {
            soil = new TilledSoilBlock(false, false, false, null, null);
            store.addComponent(blockRef, TilledSoilBlock.getComponentType(), soil);
        }
        if (soil == null) {
            return false;
        }
        soil.setWateredUntil(wateredUntil);
        blockComponentSection.markBlockNeedsSaving(blockIndex);
        blockSection.setTicking(x, y, z, true);
        blockSection.scheduleTick(blockIndex, wateredUntil);
        if (ChunkUtil.chunkCoordinate(y) == ChunkUtil.chunkCoordinate(y + 1)) {
            blockSection.setTicking(x, y + 1, z, true);
            return true;
        }
        Ref<ChunkStore> aboveSection = chunkStore.getChunkSectionReferenceAtBlock(x, y + 1, z);
        if (aboveSection != null && aboveSection.isValid()) {
            BlockSection aboveBlockSection = store.getComponent(aboveSection, BlockSection.getComponentType());
            if (aboveBlockSection != null) {
                aboveBlockSection.setTicking(x, y + 1, z, true);
            }
        }
        return true;
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

}
