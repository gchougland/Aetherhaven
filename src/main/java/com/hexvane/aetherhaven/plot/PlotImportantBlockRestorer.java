package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPrefabSequence;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.tourist.TownPortalTravelColor;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Replaces missing town linked blocks and POI voxels for one completed plot without full prefab reconstruct. */
public final class PlotImportantBlockRestorer {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PlotImportantBlockRestorer() {}

    /**
     * @return count of important prefab cells restored (correct position and rotation from the building prefab)
     */
    public static int restoreMissingImportantBlocks(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        if (FestivalService.isLiveFestivalSquare(town, plot.getPlotId())) {
            return 0;
        }
        return restoreImportantCellsFromPrefab(world, plot, def, anchor, yaw);
    }

    private static boolean isPlotCreatorEditorMarkerBlock(@Nullable String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isBlank()) {
            return false;
        }
        return switch (blockTypeId.trim()) {
            case "Editor_Empty", "Editor_Block", "Editor_Anchor" -> true;
            default -> false;
        };
    }

    private static int restoreImportantCellsFromPrefab(
        @Nonnull World world,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        String prefabPath = def.getPrefabPath();
        if (prefabPath == null || prefabPath.isBlank()) {
            return 0;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(prefabPath.trim());
        if (buffer == null) {
            return 0;
        }
        Set<String> importantIds = importantBlockTypeIds(def);
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        ConstructionPrefabSequence seq =
            ConstructionPasteOps.buildSequence(buffer, yaw, plot.getBlockPaletteSelections());
        List<ConstructionPasteOps.PendingBlock> cells =
            ConstructionPasteOps.withoutPureAirCells(seq.pendingBlocks());
        LocalCachedChunkAccessor accessor = ConstructionPasteOps.createAccessor(world, anchor, buffer);
        int placed = 0;
        for (ConstructionPasteOps.PendingBlock pb : cells) {
            if (pb.filler() != FillerBlockUtil.NO_FILLER) {
                continue;
            }
            if (pb.blockId() == 0) {
                continue;
            }
            BlockType block = blockTypeMap.getAsset(pb.blockId());
            if (block == null || block.getId() == null) {
                continue;
            }
            if (isPlotCreatorEditorMarkerBlock(block.getId())) {
                continue;
            }
            if (!matchesImportantType(block.getId(), importantIds, blockTypeMap)) {
                continue;
            }
            int wx = anchor.x + pb.x();
            int wy = anchor.y + pb.y();
            int wz = anchor.z + pb.z();
            if (!prefabOriginCellNeedsRestore(world, wx, wy, wz, block)) {
                continue;
            }
            if (ConstructionPasteOps.restoreSinglePrefabCell(world, anchor, pb, accessor)) {
                placed++;
            } else {
                LOGGER.atWarning().log(
                    "Could not restore prefab cell %s at %s,%s,%s for construction %s",
                    block.getId(),
                    wx,
                    wy,
                    wz,
                    def.getId()
                );
            }
        }
        return placed;
    }

    @Nonnull
    private static Set<String> importantBlockTypeIds(@Nonnull ConstructionDefinition def) {
        Set<String> ids = new HashSet<>();
        ids.add(AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID);
        ids.add(AetherhavenConstants.TREASURY_BLOCK_TYPE_ID);
        ids.add(AetherhavenConstants.SHOP_SAFE_BLOCK_TYPE_ID);
        ids.add(AetherhavenConstants.SHOP_SPOT_BLOCK_TYPE_ID);
        ids.add(AetherhavenConstants.TOURIST_PORTAL_BLOCK_TYPE_ID);
        for (int i = 0; i < TownPortalTravelColor.PRESET_COUNT; i++) {
            ids.add(TownPortalTravelColor.blockTypeIdForPresetIndex(i));
        }
        ids.add(AetherhavenConstants.BLOCK_PRODUCTION_STORAGE);
        ids.add(AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID);
        for (BuildingPoisDefinition.PoiRow row : def.getPois()) {
            if (row.getBlockTypeId() != null && !row.getBlockTypeId().isBlank()) {
                String id = row.getBlockTypeId().trim();
                if (!isPlotCreatorEditorMarkerBlock(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static boolean matchesImportantType(
        @Nonnull String actualId,
        @Nonnull Set<String> importantIds,
        @Nonnull BlockTypeAssetMap<String, BlockType> map
    ) {
        for (String want : importantIds) {
            if (blockTypeIdMatches(want, actualId, map)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blockTypeIdMatches(
        @Nonnull String expectedId,
        @Nonnull String actualId,
        @Nonnull BlockTypeAssetMap<String, BlockType> map
    ) {
        if (expectedId.equals(actualId) || expectedId.equalsIgnoreCase(actualId)) {
            return true;
        }
        int expectedIndex = map.getIndex(expectedId);
        if (expectedIndex < 0) {
            return false;
        }
        return expectedIndex == map.getIndex(actualId);
    }

    /** True when the prefab origin cell is missing or the wrong block type (not a nearby column match). */
    private static boolean prefabOriginCellNeedsRestore(
        @Nonnull World world, int wx, int wy, int wz, @Nonnull BlockType expected
    ) {
        BlockType at = world.getBlockType(wx, wy, wz);
        if (at == null || at == BlockType.EMPTY) {
            return true;
        }
        String expectedId = expected.getId();
        if (expectedId == null) {
            return false;
        }
        if (TownPortalTravelColor.isTouristPortalBlockTypeId(expectedId)
            && TownPortalTravelColor.isTouristPortalBlockTypeId(at.getId())) {
            return false;
        }
        return !blockTypeIdMatches(expectedId, at.getId(), BlockType.getAssetMap());
    }
}
