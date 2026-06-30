package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Stamps plot-linked block components (management shelf, treasury, shop safe, Gaia statue). */
public final class PlotBlockStamper {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int PLACE_SETTINGS = 10;

    public enum StampOutcome {
        STAMPED,
        ALREADY_OK,
        CHUNK_UNLOADED,
        BLOCK_MISSING,
        FAILED
    }

    public static final class PlotBlockRepairResult {
        private int relinked;
        private int alreadyOk;
        private int skippedChunkUnloaded;
        private int failed;
        @Nonnull private final List<String> details = new ArrayList<>();

        public int getRelinked() {
            return relinked;
        }

        public int getAlreadyOk() {
            return alreadyOk;
        }

        public int getSkippedChunkUnloaded() {
            return skippedChunkUnloaded;
        }

        public int getFailed() {
            return failed;
        }

        @Nonnull
        public List<String> getDetails() {
            return details;
        }

        void record(@Nonnull StampOutcome outcome, @Nonnull String label) {
            switch (outcome) {
                case STAMPED -> {
                    relinked++;
                    details.add(label);
                }
                case ALREADY_OK -> alreadyOk++;
                case CHUNK_UNLOADED -> skippedChunkUnloaded++;
                case BLOCK_MISSING, FAILED -> failed++;
                default -> {}
            }
        }
    }

    private PlotBlockStamper() {}

    /** Re-place and stamp every linked block type for a completed build (always writes components). */
    public static void stampAllLinkedBlocks(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        UUID plotId = plot.getPlotId();
        stampManagementBlock(world, town, plotId, def, anchor, yaw, true, false);
        stampTreasuryBlock(world, town, plotId, def, anchor, yaw, true, false);
        stampShopSafeBlock(world, town, plotId, def, anchor, yaw, true, false);
        stampGaiaStatueBlock(world, town, plotId, plot, def, anchor, yaw, true, false);
    }

    /**
     * Verifies linked blocks for a plot; re-stamps only when missing, blank, or wrong plot/town ids.
     *
     * @return repair summary (may be empty when definition has no special blocks)
     */
    @Nonnull
    public static PlotBlockRepairResult verifyAndRepairPlot(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        return inspectOrRepairPlot(world, town, plot, def, anchor, yaw, false);
    }

    /** Read-only link check; {@link StampOutcome#STAMPED} means repair is needed. */
    @Nonnull
    public static PlotBlockRepairResult inspectPlotLinks(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        return inspectOrRepairPlot(world, town, plot, def, anchor, yaw, true);
    }

    @Nonnull
    private static PlotBlockRepairResult inspectOrRepairPlot(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        boolean dryRun
    ) {
        UUID plotId = plot.getPlotId();
        PlotBlockRepairResult result = new PlotBlockRepairResult();
        recordOutcome(result, "management", stampManagementBlock(world, town, plotId, def, anchor, yaw, false, dryRun));
        recordOutcome(result, "treasury", stampTreasuryBlock(world, town, plotId, def, anchor, yaw, false, dryRun));
        recordOutcome(result, "shopSafe", stampShopSafeBlock(world, town, plotId, def, anchor, yaw, false, dryRun));
        recordOutcome(result, "gaiaStatue", stampGaiaStatueBlock(world, town, plotId, plot, def, anchor, yaw, false, dryRun));
        return result;
    }

    private static void recordOutcome(
        @Nonnull PlotBlockRepairResult result, @Nonnull String label, @Nonnull StampOutcome outcome
    ) {
        if (outcome == StampOutcome.ALREADY_OK) {
            result.record(outcome, label);
            return;
        }
        if (outcome == StampOutcome.STAMPED) {
            result.record(outcome, plotLabel(label));
            return;
        }
        if (outcome != StampOutcome.CHUNK_UNLOADED
            && outcome != StampOutcome.BLOCK_MISSING
            && outcome != StampOutcome.FAILED) {
            return;
        }
        result.record(outcome, label);
    }

    @Nonnull
    private static String plotLabel(@Nonnull String blockKind) {
        return blockKind;
    }

    @Nonnull
    private static String wantPlotId(@Nonnull UUID plotId) {
        return plotId.toString();
    }

    @Nonnull
    private static String wantTownId(@Nonnull TownRecord town) {
        return town.getTownId().toString();
    }

    private static boolean linksMatch(@Nullable String plotId, @Nullable String townId, @Nonnull UUID wantPlot, @Nonnull TownRecord town) {
        return plotId != null
            && townId != null
            && !plotId.isBlank()
            && !townId.isBlank()
            && wantPlotId(wantPlot).equals(plotId.trim())
            && wantTownId(town).equals(townId.trim());
    }

    /** Block ids are case-insensitive in the asset map but {@link BlockType#getId()} uses the registered key. */
    private static boolean blockTypeIdMatches(@Nonnull String expectedId, @Nonnull String actualId) {
        if (expectedId.equals(actualId) || expectedId.equalsIgnoreCase(actualId)) {
            return true;
        }
        BlockTypeAssetMap<String, BlockType> map = BlockType.getAssetMap();
        int expectedIndex = map.getIndex(expectedId);
        if (expectedIndex < 0) {
            return false;
        }
        return expectedIndex == map.getIndex(actualId);
    }

    private static boolean isGaiaStatueBlockTypeId(@Nonnull String blockTypeId) {
        return blockTypeIdMatches(blockTypeId, AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID);
    }

    @Nonnull
    private static int[] resolveGaiaStatueLocalPos(@Nonnull ConstructionDefinition def) {
        for (BuildingPoisDefinition.PoiRow row : def.getPois()) {
            String typeId = row.getBlockTypeId();
            if (typeId != null && isGaiaStatueBlockTypeId(typeId)) {
                return new int[] {row.getLocalX(), row.getLocalY(), row.getLocalZ()};
            }
        }
        return new int[] {0, 2, 0};
    }

    @Nullable
    private static Vector3i findGaiaStatueBaseCellInFootprint(@Nonnull World world, @Nonnull PlotInstance plot) {
        PlotFootprintRecord fp = plot.toFootprint();
        Set<Long> seenBases = new HashSet<>();
        Vector3i first = null;
        for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
            for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                for (int y = fp.getMinY(); y <= fp.getMaxY(); y++) {
                    BlockType bt = world.getBlockType(x, y, z);
                    if (bt == null || !isGaiaStatueBlockTypeId(bt.getId())) {
                        continue;
                    }
                    BlockPosition base = gaiaStatueBaseBlock(world, x, y, z);
                    long key = packBlockPos(base.x, base.y, base.z);
                    if (!seenBases.add(key)) {
                        continue;
                    }
                    if (first == null) {
                        first = new Vector3i(base.x, base.y, base.z);
                    }
                }
            }
        }
        return first;
    }

    private static long packBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | (long) z & 0x3FFFFFFL;
    }

    @Nullable
    private static Ref<ChunkStore> blockRefAt(@Nonnull WorldChunk chunk, int wx, int y, int wz) {
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(wx, y, wz);
        if (blockRef != null) {
            return blockRef;
        }
        for (int dy : new int[] {-1, 1, -2, 2}) {
            int yy = y + dy;
            if (yy < 0 || yy >= 320) {
                continue;
            }
            Ref<ChunkStore> r = chunk.getBlockComponentEntity(wx, yy, wz);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    /** Gaia statue prefab cells include filler voxels; link data must live on the unpacked base block. */
    @Nonnull
    @SuppressWarnings({ "deprecation", "removal" })
    private static BlockPosition gaiaStatueBaseBlock(@Nonnull World world, int wx, int y, int wz) {
        return world.getBaseBlock(new BlockPosition(wx, y, wz));
    }

    @Nullable
    private static Ref<ChunkStore> gaiaStatueBlockRef(
        @Nonnull World world, @Nonnull WorldChunk chunk, int wx, int y, int wz
    ) {
        BlockPosition base = gaiaStatueBaseBlock(world, wx, y, wz);
        return chunk.getBlockComponentEntity(base.x, base.y, base.z);
    }

    @Nonnull
    private static StampOutcome stampGaiaStatueBlock(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        boolean force,
        boolean dryRun
    ) {
        if (!AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR.equals(def.getGameplayConstructionId())) {
            return StampOutcome.ALREADY_OK;
        }
        int[] local = resolveGaiaStatueLocalPos(def);
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;

        Integer statueY =
            findBlockY(world, wx, wy, wz, AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID);
        Vector3i baseCell = null;
        if (statueY != null) {
            BlockPosition base = gaiaStatueBaseBlock(world, wx, statueY, wz);
            baseCell = new Vector3i(base.x, base.y, base.z);
        } else {
            baseCell = findGaiaStatueBaseCellInFootprint(world, plot);
            if (baseCell == null) {
                LOGGER.atWarning().log(
                    "No Gaia statue block in plot footprint (expected near %s,*,%s y~=%s)",
                    wx,
                    wz,
                    wy
                );
                return StampOutcome.BLOCK_MISSING;
            }
        }

        int baseX = baseCell.x;
        int baseY = baseCell.y;
        int baseZ = baseCell.z;
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(baseX, baseZ));
        if (chunk == null) {
            LOGGER.atWarning().log("Gaia statue chunk not loaded at %s,%s,%s", baseX, baseY, baseZ);
            return StampOutcome.CHUNK_UNLOADED;
        }

        if (!force) {
            Ref<ChunkStore> existing = gaiaStatueBlockRef(world, chunk, baseX, baseY, baseZ);
            if (existing != null) {
                GaiaStatueBlock gs = existing.getStore().getComponent(existing, GaiaStatueBlock.getComponentType());
                if (gs != null && linksMatch(gs.getPlotId(), gs.getTownId(), plotId, town)) {
                    return StampOutcome.ALREADY_OK;
                }
            }
            if (dryRun) {
                return StampOutcome.STAMPED;
            }
        }

        Vector3i cell = new Vector3i(baseX, baseY, baseZ);
        BlockType placedType = world.getBlockType(baseX, baseY, baseZ);
        String placeBlockTypeId =
            placedType != null ? placedType.getId() : AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID;
        Rotation blockYaw = PlotBlockRotationUtil.readBlockYaw(world, cell);
        RotationTuple rt = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
        int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);
        ensureBlockPlaced(
            world,
            chunk,
            baseX,
            baseY,
            baseZ,
            placeBlockTypeId,
            rt,
            rotationIndex
        );

        Ref<ChunkStore> blockRef = gaiaStatueBlockRef(world, chunk, baseX, baseY, baseZ);
        if (blockRef == null) {
            LOGGER.atWarning().log("No block entity after re-placing Gaia statue at %s,%s,%s", baseX, baseY, baseZ);
            return StampOutcome.FAILED;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        cs.putComponent(
            blockRef,
            GaiaStatueBlock.getComponentType(),
            new GaiaStatueBlock(wantPlotId(plotId), wantTownId(town))
        );
        return StampOutcome.STAMPED;
    }

    @Nonnull
    private static StampOutcome stampManagementBlock(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        boolean force,
        boolean dryRun
    ) {
        int[] local = def.getManagementBlockLocalPos();
        if (local == null) {
            return StampOutcome.ALREADY_OK;
        }
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            LOGGER.atWarning().log("Management block chunk not loaded at %s,%s,%s", wx, wy, wz);
            return StampOutcome.CHUNK_UNLOADED;
        }

        Integer managementY = findBlockY(world, wx, wy, wz, AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID);
        if (managementY == null) {
            LOGGER.atWarning().log(
                "No %s in column %s,*,%s near y=%s",
                AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID,
                wx,
                wz,
                wy
            );
            return StampOutcome.BLOCK_MISSING;
        }

        if (!force) {
            Ref<ChunkStore> existing = blockRefAt(chunk, wx, managementY, wz);
            if (existing != null) {
                ManagementBlock mb = existing.getStore().getComponent(existing, ManagementBlock.getComponentType());
                if (mb != null && linksMatch(mb.getPlotId(), mb.getTownId(), plotId, town)) {
                    return StampOutcome.ALREADY_OK;
                }
            }
            if (dryRun) {
                return StampOutcome.STAMPED;
            }
        }

        Vector3i cell = new Vector3i(wx, managementY, wz);
        Rotation blockYaw = PlotBlockRotationUtil.readBlockYaw(world, cell);
        RotationTuple rt = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
        int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);
        ensureBlockPlaced(
            world,
            chunk,
            wx,
            managementY,
            wz,
            AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID,
            rt,
            rotationIndex
        );

        Ref<ChunkStore> blockRef = blockRefAt(chunk, wx, managementY, wz);
        if (blockRef == null) {
            LOGGER.atWarning().log("No block entity after re-placing management block at %s,%s,%s", wx, managementY, wz);
            return StampOutcome.FAILED;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        cs.putComponent(
            blockRef,
            ManagementBlock.getComponentType(),
            new ManagementBlock(wantPlotId(plotId), wantTownId(town))
        );
        return StampOutcome.STAMPED;
    }

    @Nonnull
    private static StampOutcome stampTreasuryBlock(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        boolean force,
        boolean dryRun
    ) {
        int[] local = def.getTreasuryLocalPos();
        if (local == null) {
            return StampOutcome.ALREADY_OK;
        }
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            LOGGER.atWarning().log("Treasury block chunk not loaded at %s,%s,%s", wx, wy, wz);
            return StampOutcome.CHUNK_UNLOADED;
        }

        Integer treasuryY = findBlockY(world, wx, wy, wz, AetherhavenConstants.TREASURY_BLOCK_TYPE_ID);
        if (treasuryY == null) {
            return StampOutcome.BLOCK_MISSING;
        }

        if (!force) {
            Ref<ChunkStore> existing = blockRefAt(chunk, wx, treasuryY, wz);
            if (existing != null) {
                TreasuryBlock tb = existing.getStore().getComponent(existing, TreasuryBlock.getComponentType());
                if (tb != null && linksMatch(tb.getPlotId(), tb.getTownId(), plotId, town)) {
                    return StampOutcome.ALREADY_OK;
                }
            }
            if (dryRun) {
                return StampOutcome.STAMPED;
            }
        }

        Vector3i cell = new Vector3i(wx, treasuryY, wz);
        Rotation blockYaw = PlotBlockRotationUtil.readBlockYaw(world, cell);
        RotationTuple rt = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
        int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);
        ensureBlockPlaced(
            world,
            chunk,
            wx,
            treasuryY,
            wz,
            AetherhavenConstants.TREASURY_BLOCK_TYPE_ID,
            rt,
            rotationIndex
        );

        Ref<ChunkStore> blockRef = blockRefAt(chunk, wx, treasuryY, wz);
        if (blockRef == null) {
            LOGGER.atWarning().log("No block entity after re-placing treasury at %s,%s,%s", wx, treasuryY, wz);
            return StampOutcome.FAILED;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        cs.putComponent(
            blockRef,
            TreasuryBlock.getComponentType(),
            new TreasuryBlock(wantPlotId(plotId), wantTownId(town))
        );
        return StampOutcome.STAMPED;
    }

    @Nonnull
    private static StampOutcome stampShopSafeBlock(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        boolean force,
        boolean dryRun
    ) {
        int[] local = def.getShopSafeLocalPos();
        if (local == null) {
            return StampOutcome.ALREADY_OK;
        }
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            LOGGER.atWarning().log("Shop safe block chunk not loaded at %s,%s,%s", wx, wy, wz);
            return StampOutcome.CHUNK_UNLOADED;
        }

        Integer safeY = findBlockY(world, wx, wy, wz, AetherhavenConstants.SHOP_SAFE_BLOCK_TYPE_ID);
        if (safeY == null) {
            return StampOutcome.BLOCK_MISSING;
        }

        if (!force) {
            Ref<ChunkStore> existing = blockRefAt(chunk, wx, safeY, wz);
            if (existing != null) {
                ShopSafeBlock sb = existing.getStore().getComponent(existing, ShopSafeBlock.getComponentType());
                if (sb != null && linksMatch(sb.getPlotId(), sb.getTownId(), plotId, town)) {
                    return StampOutcome.ALREADY_OK;
                }
            }
            if (dryRun) {
                return StampOutcome.STAMPED;
            }
        }

        Vector3i cell = new Vector3i(wx, safeY, wz);
        Rotation blockYaw = PlotBlockRotationUtil.readBlockYaw(world, cell);
        RotationTuple rt = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
        int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);
        ensureBlockPlaced(
            world,
            chunk,
            wx,
            safeY,
            wz,
            AetherhavenConstants.SHOP_SAFE_BLOCK_TYPE_ID,
            rt,
            rotationIndex
        );

        Ref<ChunkStore> blockRef = blockRefAt(chunk, wx, safeY, wz);
        if (blockRef == null) {
            LOGGER.atWarning().log("No block entity after re-placing shop safe at %s,%s,%s", wx, safeY, wz);
            return StampOutcome.FAILED;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        cs.putComponent(
            blockRef,
            ShopSafeBlock.getComponentType(),
            new ShopSafeBlock(wantPlotId(plotId), wantTownId(town))
        );
        return StampOutcome.STAMPED;
    }

    @Nullable
    private static Integer findBlockY(@Nonnull World world, int wx, int wy, int wz, @Nonnull String blockTypeId) {
        for (int dy = -4; dy <= 4; dy++) {
            int y = wy + dy;
            if (y < 0 || y >= 320) {
                continue;
            }
            BlockType bt = world.getBlockType(wx, y, wz);
            if (bt != null && blockTypeIdMatches(blockTypeId, bt.getId())) {
                return y;
            }
        }
        return null;
    }

    private static void ensureBlockPlaced(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int wx,
        int y,
        int wz,
        @Nonnull String blockTypeId,
        @Nonnull RotationTuple rt,
        int rotationIndex
    ) {
        boolean placed =
            chunk.placeBlock(wx, y, wz, blockTypeId, rt.yaw(), rt.pitch(), rt.roll(), PLACE_SETTINGS);
        if (!placed) {
            world.breakBlock(wx, y, wz, PLACE_SETTINGS);
            placed =
                chunk.placeBlock(wx, y, wz, blockTypeId, rt.yaw(), rt.pitch(), rt.roll(), PLACE_SETTINGS);
        }
        if (!placed) {
            BlockTypeAssetMap<String, BlockType> typeMap = BlockType.getAssetMap();
            int indexKey = typeMap.getIndex(blockTypeId);
            BlockType blockType = typeMap.getAsset(indexKey);
            chunk.setBlock(wx, y, wz, indexKey, blockType, rotationIndex, 0, PLACE_SETTINGS);
        }
    }
}
