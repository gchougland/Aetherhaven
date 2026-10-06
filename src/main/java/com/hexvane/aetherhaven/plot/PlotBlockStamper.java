package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Stamps plot-linked block components (management shelf, treasury, shop safe, Gaia statue, production storage). */
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

    /** Stamp every linked block type for a completed build (always writes link components). */
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
        stampProductionStorageBlock(world, plot, def, anchor, yaw, true, false);
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
        recordOutcome(result, "productionStorage", stampProductionStorageBlock(world, plot, def, anchor, yaw, false, dryRun));
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
        if (GaiaStatueAppearance.sameStatueFamily(expectedId, actualId)
            || expectedId.equals(actualId) || expectedId.equalsIgnoreCase(actualId)) {
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
                    BlockType bt = ChunkSectionBlockUtil.blockType(world, x, y, z);
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

    /** Prefab cells can be fillers; link data belongs only to the unpacked base block. */
    @Nonnull
    private static BlockPosition gaiaStatueBaseBlock(@Nonnull World world, int wx, int y, int wz) {
        BlockPosition position = new BlockPosition(wx, y, wz);
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            return position;
        }
        int filler = ChunkSectionBlockUtil.filler(world, wx, y, wz);
        if (filler == FillerBlockUtil.NO_FILLER) {
            return position;
        }
        return new BlockPosition(
            wx - FillerBlockUtil.unpackX(filler),
            y - FillerBlockUtil.unpackY(filler),
            wz - FillerBlockUtil.unpackZ(filler)
        );
    }

    private static <T extends Component<ChunkStore>> StampOutcome stampBlockLink(
        @Nonnull World world, int x, int y, int z, @Nonnull String expectedBlockType,
        @Nonnull ComponentType<ChunkStore, T> componentType, @Nonnull T link,
        @Nonnull Predicate<T> matches, boolean force, boolean dryRun
    ) {
        BlockPosition base = gaiaStatueBaseBlock(world, x, y, z);
        Ref<ChunkStore> sectionRef = ChunkSectionBlockUtil.sectionRefAt(world, base.x, base.y, base.z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return StampOutcome.CHUNK_UNLOADED;
        }
        BlockType blockType = ChunkSectionBlockUtil.blockType(world, base.x, base.y, base.z);
        if (blockType == null || !blockTypeIdMatches(expectedBlockType, blockType.getId())
            || ChunkSectionBlockUtil.filler(world, base.x, base.y, base.z) != FillerBlockUtil.NO_FILLER) {
            return StampOutcome.FAILED;
        }
        Store<ChunkStore> store = sectionRef.getStore();
        BlockComponentSection section = store.getComponent(sectionRef, BlockComponentSection.getComponentType());
        if (section == null) {
            return StampOutcome.CHUNK_UNLOADED;
        }
        int rotation = PlotBlockRotationUtil.readBlockRotationIndex(world, new Vector3i(base.x, base.y, base.z));
        return stampLink(store, section, ChunkUtil.indexBlock(base.x, base.y, base.z), componentType, link,
            matches, blockType.getBlockEntity(), holder -> BlockEntity.setBlockEntity(
                store, sectionRef, section, base.x, base.y, base.z, blockType, rotation, holder), force, dryRun);
    }

    /**
     * Repair component state without changing the voxel. Non-ticking sections park their entities as holders;
     * treating those as missing caused repeated block replacement, while NO_UPDATE_STATE prevented attachment.
     */
    static <T extends Component<ChunkStore>> StampOutcome stampLink(
        @Nonnull Store<ChunkStore> store, @Nonnull BlockComponentSection section, int index,
        @Nonnull ComponentType<ChunkStore, T> componentType, @Nonnull T link, @Nonnull Predicate<T> matches,
        @Nullable Holder<ChunkStore> template, @Nonnull Consumer<Holder<ChunkStore>> attach,
        boolean force, boolean dryRun
    ) {
        Ref<ChunkStore> ref = section.getBlockReference(index);
        if (ref != null && !ref.isValid()) {
            return StampOutcome.FAILED;
        }
        Holder<ChunkStore> holder = ref == null ? section.getBlockHolder(index) : null;
        T existing = ref != null ? store.getComponent(ref, componentType)
            : holder != null ? holder.getComponent(componentType) : null;
        if (!force && existing != null && matches.test(existing)) {
            return StampOutcome.ALREADY_OK;
        }
        if (dryRun) {
            return StampOutcome.STAMPED;
        }
        if (ref != null) {
            store.putComponent(ref, componentType, link);
        } else if (holder != null) {
            holder.putComponent(componentType, link);
        } else {
            if (template == null) {
                return StampOutcome.FAILED;
            }
            Holder<ChunkStore> created = template.clone();
            created.putComponent(componentType, link);
            attach.accept(created);
            Ref<ChunkStore> attached = section.getBlockReference(index);
            Holder<ChunkStore> parked = section.getBlockHolder(index);
            T result = attached != null && attached.isValid() ? store.getComponent(attached, componentType)
                : parked != null ? parked.getComponent(componentType) : null;
            if (result == null || !matches.test(result)) {
                return StampOutcome.FAILED;
            }
        }
        section.markBlockNeedsSaving(index);
        return StampOutcome.STAMPED;
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
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(baseX, baseZ));
        if (chunk == null) {
            LOGGER.atWarning().log("Gaia statue chunk not loaded at %s,%s,%s", baseX, baseY, baseZ);
            return StampOutcome.CHUNK_UNLOADED;
        }

        return stampBlockLink(world, baseX, baseY, baseZ, AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID,
            GaiaStatueBlock.getComponentType(), new GaiaStatueBlock(wantPlotId(plotId), wantTownId(town)),
            link -> linksMatch(link.getPlotId(), link.getTownId(), plotId, town), force, dryRun);
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
        // Festival prefabs do not include the everyday square's town records shelf. Repair must not put it back.
        if (FestivalService.isLiveFestivalSquare(town, plotId)) {
            return StampOutcome.ALREADY_OK;
        }
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            LOGGER.atWarning().log("Management block chunk not loaded at %s,%s,%s", wx, wy, wz);
            return StampOutcome.CHUNK_UNLOADED;
        }

        Integer managementY = findBlockY(world, wx, wy, wz, AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID);
        if (managementY == null) {
            if (dryRun) {
                return StampOutcome.STAMPED;
            }
            Vector3i cell = new Vector3i(wx, wy, wz);
            Rotation blockYaw = PlotBlockRotationUtil.readBlockYaw(world, cell);
            if (blockYaw == Rotation.None) {
                blockYaw = yaw;
            }
            RotationTuple rt = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
            int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);
            ensureBlockPlaced(
                world,
                wx,
                wy,
                wz,
                AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID,
                rt,
                rotationIndex
            );
            managementY = findBlockY(world, wx, wy, wz, AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID);
            if (managementY == null) {
                LOGGER.atWarning().log(
                    "Could not place %s at %s,%s,%s",
                    AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID,
                    wx,
                    wy,
                    wz
                );
                return StampOutcome.FAILED;
            }
        }

        return stampBlockLink(world, wx, managementY, wz, AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID,
            ManagementBlock.getComponentType(), new ManagementBlock(wantPlotId(plotId), wantTownId(town)),
            link -> linksMatch(link.getPlotId(), link.getTownId(), plotId, town), force, dryRun);
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
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            LOGGER.atWarning().log("Treasury block chunk not loaded at %s,%s,%s", wx, wy, wz);
            return StampOutcome.CHUNK_UNLOADED;
        }

        Integer treasuryY = findBlockY(world, wx, wy, wz, AetherhavenConstants.TREASURY_BLOCK_TYPE_ID);
        if (treasuryY == null) {
            if (dryRun) {
                return StampOutcome.STAMPED;
            }
            Vector3i cell = new Vector3i(wx, wy, wz);
            Rotation blockYaw = PlotBlockRotationUtil.readBlockYaw(world, cell);
            if (blockYaw == Rotation.None) {
                blockYaw = yaw;
            }
            RotationTuple rt = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
            int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);
            ensureBlockPlaced(
                world,
                wx,
                wy,
                wz,
                AetherhavenConstants.TREASURY_BLOCK_TYPE_ID,
                rt,
                rotationIndex
            );
            treasuryY = findBlockY(world, wx, wy, wz, AetherhavenConstants.TREASURY_BLOCK_TYPE_ID);
            if (treasuryY == null) {
                return StampOutcome.FAILED;
            }
        }

        return stampBlockLink(world, wx, treasuryY, wz, AetherhavenConstants.TREASURY_BLOCK_TYPE_ID,
            TreasuryBlock.getComponentType(), new TreasuryBlock(wantPlotId(plotId), wantTownId(town)),
            link -> linksMatch(link.getPlotId(), link.getTownId(), plotId, town), force, dryRun);
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
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            LOGGER.atWarning().log("Shop safe block chunk not loaded at %s,%s,%s", wx, wy, wz);
            return StampOutcome.CHUNK_UNLOADED;
        }

        Integer safeY = findBlockY(world, wx, wy, wz, AetherhavenConstants.SHOP_SAFE_BLOCK_TYPE_ID);
        if (safeY == null) {
            if (dryRun) {
                return StampOutcome.STAMPED;
            }
            Vector3i cell = new Vector3i(wx, wy, wz);
            Rotation blockYaw = PlotBlockRotationUtil.readBlockYaw(world, cell);
            if (blockYaw == Rotation.None) {
                blockYaw = yaw;
            }
            RotationTuple rt = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
            int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);
            ensureBlockPlaced(
                world,
                wx,
                wy,
                wz,
                AetherhavenConstants.SHOP_SAFE_BLOCK_TYPE_ID,
                rt,
                rotationIndex
            );
            safeY = findBlockY(world, wx, wy, wz, AetherhavenConstants.SHOP_SAFE_BLOCK_TYPE_ID);
            if (safeY == null) {
                return StampOutcome.FAILED;
            }
        }

        return stampBlockLink(world, wx, safeY, wz, AetherhavenConstants.SHOP_SAFE_BLOCK_TYPE_ID,
            ShopSafeBlock.getComponentType(), new ShopSafeBlock(wantPlotId(plotId), wantTownId(town)),
            link -> linksMatch(link.getPlotId(), link.getTownId(), plotId, town), force, dryRun);
    }

    @Nonnull
    private static StampOutcome stampProductionStorageBlock(
        @Nonnull World world,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        boolean force,
        boolean dryRun
    ) {
        if (!ProductionCatalog.isProductionWorkplaceConstruction(def.getGameplayConstructionId())) {
            return StampOutcome.ALREADY_OK;
        }

        int wx;
        int wy;
        int wz;
        int[] local = def.getProductionStorageLocalPos();
        if (local != null) {
            Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
            wx = anchor.x + d.x;
            wy = anchor.y + d.y;
            wz = anchor.z + d.z;
        } else {
            Vector3i baseCell = findProductionStorageBaseCellInFootprint(world, plot);
            if (baseCell == null) {
                LOGGER.atWarning().log(
                    "No production storage block in plot footprint for construction %s",
                    def.getId()
                );
                return StampOutcome.BLOCK_MISSING;
            }
            wx = baseCell.x;
            wy = baseCell.y;
            wz = baseCell.z;
        }

        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            LOGGER.atWarning().log("Production storage chunk not loaded at %s,%s,%s", wx, wy, wz);
            return StampOutcome.CHUNK_UNLOADED;
        }

        Integer storageY = findBlockY(world, wx, wy, wz, AetherhavenConstants.BLOCK_PRODUCTION_STORAGE);
        Vector3i cell = new Vector3i(wx, storageY != null ? storageY : wy, wz);
        if (storageY != null) {
            BlockPosition base = gaiaStatueBaseBlock(world, cell.x, cell.y, cell.z);
            cell.set(base.x, base.y, base.z);
        }
        int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);
        // PLACE_SETTINGS disables native fillers. Clearing/re-stamping with it used to leave only one
        // interactive corner. Preserve the origin and restore only missing cells of its rotated hitbox.
        return switch (com.hexvane.aetherhaven.production.ProductionStorageBlocks.repair(world, cell, rotationIndex, dryRun)) {
            case READY -> StampOutcome.ALREADY_OK;
            case REPAIRED, NEEDS_REPAIR -> StampOutcome.STAMPED;
            case UNLOADED -> StampOutcome.CHUNK_UNLOADED;
            case BLOCKED, FAILED -> StampOutcome.FAILED;
        };
    }

    @Nullable
    private static Vector3i findProductionStorageBaseCellInFootprint(@Nonnull World world, @Nonnull PlotInstance plot) {
        PlotFootprintRecord fp = plot.toFootprint();
        Set<Long> seenBases = new HashSet<>();
        Vector3i first = null;
        int minY = Math.max(0, fp.getMinY() - 1);
        int maxY = Math.min(319, fp.getMaxY() + 1);
        for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
            for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockType bt = ChunkSectionBlockUtil.blockType(world, x, y, z);
                    if (bt == null || !blockTypeIdMatches(AetherhavenConstants.BLOCK_PRODUCTION_STORAGE, bt.getId())) {
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

    @Nullable
    private static Integer findBlockY(@Nonnull World world, int wx, int wy, int wz, @Nonnull String blockTypeId) {
        for (int dy = -4; dy <= 4; dy++) {
            int y = wy + dy;
            if (y < 0 || y >= 320) {
                continue;
            }
            BlockType bt = ChunkSectionBlockUtil.blockType(world, wx, y, wz);
            if (bt != null && blockTypeIdMatches(blockTypeId, bt.getId())) {
                return y;
            }
        }
        return null;
    }

    private static void ensureBlockPlaced(
        @Nonnull World world,
        int wx,
        int y,
        int wz,
        @Nonnull String blockTypeId,
        @Nonnull RotationTuple rt,
        int rotationIndex
    ) {
        BlockTypeAssetMap<String, BlockType> typeMap = BlockType.getAssetMap();
        int indexKey = typeMap.getIndex(blockTypeId);
        BlockType blockType = typeMap.getAsset(indexKey);
        if (blockType == null) {
            return;
        }
        boolean placed = tryPlaceBlock(world, wx, y, wz, blockType, rotationIndex, PLACE_SETTINGS);
        if (!placed) {
            ChunkSectionBlockUtil.breakBlock(world, wx, y, wz, PLACE_SETTINGS);
            placed = tryPlaceBlock(world, wx, y, wz, blockType, rotationIndex, PLACE_SETTINGS);
        }
        if (!placed) {
            ChunkSectionBlockUtil.setBlock(
                world,
                wx,
                y,
                wz,
                indexKey,
                blockType,
                rotationIndex,
                FillerBlockUtil.NO_FILLER,
                PLACE_SETTINGS
            );
        }
    }

    private static boolean tryPlaceBlock(
        @Nonnull World world,
        int wx,
        int y,
        int wz,
        @Nonnull BlockType blockType,
        int rotationIndex,
        int settings
    ) {
        BlockSection section = ChunkSectionBlockUtil.blockSectionAt(world, wx, y, wz);
        if (section == null) {
            return false;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        if (!BlockOperations.testPlaceBlock(store, section, wx, y, wz, blockType, rotationIndex)) {
            return false;
        }
        String key = blockType.getId();
        if (key == null) {
            return false;
        }
        int indexKey = BlockType.getAssetMap().getIndex(key);
        if (indexKey < 0) {
            return false;
        }
        return ChunkSectionBlockUtil.setBlock(
            world,
            wx,
            y,
            wz,
            indexKey,
            blockType,
            rotationIndex,
            FillerBlockUtil.NO_FILLER,
            settings
        );
    }
}
