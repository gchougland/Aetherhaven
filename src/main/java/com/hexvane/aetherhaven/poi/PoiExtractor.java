package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteRemapper;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerDataComponent;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerLocator;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PoiExtractor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int ANCHOR_SEARCH_XY = 2;
    private static final int ANCHOR_SEARCH_Y = 3;

    private PoiExtractor() {}

    public static void registerForCompletedBuild(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull PlotInstance plot,
        @Nonnull String constructionId,
        @Nonnull Vector3i prefabAnchorWorld,
        @Nonnull Rotation prefabYaw
    ) {
        ConstructionDefinition cdef = plugin.getConstructionCatalog().get(constructionId);
        if (cdef == null) {
            LOGGER.atInfo().log("No construction definition for id %s (POIs skipped)", constructionId);
            return;
        }

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);

        List<PoiMarkerLocator.LocalMarkerRow> markers = PoiMarkerLocator.collectInPlot(store, plot, cdef);
        Set<String> markerLocalKeys = PoiMarkerLocator.markerLocalKeys(markers);
        Map<String, String> palette = plot.getBlockPaletteSelections();

        List<PoiEntry> batch = new ArrayList<>();
        for (BuildingPoisDefinition.PoiRow row : cdef.getPois()) {
            String localKey = PoiMarkerLocator.localKey(row.getLocalX(), row.getLocalY(), row.getLocalZ());
            if (markerLocalKeys.contains(localKey)) {
                continue;
            }
            PoiEntry fromJson = buildFromJsonRow(world, town, plotId, row, prefabAnchorWorld, prefabYaw, palette);
            if (fromJson != null) {
                batch.add(fromJson);
            }
        }
        for (PoiMarkerLocator.LocalMarkerRow marker : markers) {
            Vector3i d = PrefabLocalOffset.rotate(prefabYaw, marker.localX(), marker.localY(), marker.localZ());
            int wx = prefabAnchorWorld.x + d.x;
            int wy = prefabAnchorWorld.y + d.y;
            int wz = prefabAnchorWorld.z + d.z;
            PoiMarkerDataComponent data = marker.data();
            UUID poiId = data.getPoiRegistryId() != null ? data.getPoiRegistryId() : UUID.randomUUID();
            batch.add(PoiMarkerLocator.toRegistryEntry(poiId, town, plotId, wx, wy, wz, data));
        }
        // Only replace existing POIs when extract found something — empty batch after a failed palette match
        // used to wipe the plot and leave desk workers Idle at the footprint center.
        if (batch.isEmpty()) {
            LOGGER.atWarning().log(
                "POI extract yielded 0 for construction %s plot %s; leaving existing POIs in place",
                constructionId,
                plotId
            );
            return;
        }
        reg.unregisterByPlotId(plotId);
        reg.registerAll(ShopBrowsePoiMigration.migrate(batch));
        LOGGER.atInfo().log(
            "Registered %s POIs for construction %s plot %s (%s from JSON, %s from markers)",
            batch.size(),
            constructionId,
            plotId,
            cdef.getPois().size() - markerLocalKeys.size(),
            markers.size()
        );
    }

    @Nullable
    private static PoiEntry buildFromJsonRow(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull BuildingPoisDefinition.PoiRow row,
        @Nonnull Vector3i prefabAnchorWorld,
        @Nonnull Rotation prefabYaw,
        @Nullable Map<String, String> blockPaletteSelections
    ) {
        PoiDualCellNormalize.normalize(row);
        Vector3i d = PrefabLocalOffset.rotate(prefabYaw, row.getLocalX(), row.getLocalY(), row.getLocalZ());
        int baseWx = prefabAnchorWorld.x + d.x;
        int baseWy = prefabAnchorWorld.y + d.y;
        int baseWz = prefabAnchorWorld.z + d.z;
        int wx = baseWx;
        int wy = baseWy;
        int wz = baseWz;
        String expectedType = row.getBlockTypeId();
        if (expectedType != null && isPlotCreatorEditorMarkerBlock(expectedType)) {
            expectedType = null;
        }
        String resolvedExpectedType = expectedType;
        boolean furnitureMount =
            PoiDualCellNormalize.keepsFurnitureLocal(row.getInteractionKind(), row.getBlockTypeId());
        if (furnitureMount && expectedType != null) {
            String remapped =
                BlockPaletteRemapper.remapBlockTypeId(expectedType, blockPaletteSelections);
            Vector3i anchor = resolveAnchorForExpectedBlock(world, wx, wy, wz, remapped);
            String matchedType = remapped;
            if (anchor == null && !remapped.equals(expectedType)) {
                anchor = resolveAnchorForExpectedBlock(world, wx, wy, wz, expectedType);
                matchedType = expectedType;
            }
            if (anchor == null) {
                BlockType at = ChunkSectionBlockUtil.blockType(world, wx, wy, wz);
                String actual = at != null ? at.getId() : null;
                boolean optionalQuestBoard =
                    AetherhavenConstants.QUEST_BOARD_ITEM_ID.equals(expectedType)
                        || row.getTags().contains(AetherhavenConstants.POI_TAG_QUEST_BOARD);
                if (optionalQuestBoard) {
                    LOGGER.atFine().log(
                        "Skipping optional quest board POI near %s,%s,%s (no board block; center was %s)",
                        wx,
                        wy,
                        wz,
                        actual
                    );
                } else {
                    LOGGER.atWarning().log(
                        "Skipping POI near %s,%s,%s: no blockTypeId %s (palette tried %s; center was %s)",
                        wx,
                        wy,
                        wz,
                        expectedType,
                        remapped,
                        actual
                    );
                }
                return null;
            }
            resolvedExpectedType = matchedType;
            wx = anchor.x;
            wy = anchor.y;
            wz = anchor.z;
        } else if (!furnitureMount) {
            if (expectedType != null) {
                String remapped =
                    BlockPaletteRemapper.remapBlockTypeId(expectedType, blockPaletteSelections);
                BlockType atCell = ChunkSectionBlockUtil.blockType(world, wx, wy, wz);
                String actual = atCell != null ? atCell.getId() : null;
                boolean optionalQuestBoard =
                    AetherhavenConstants.QUEST_BOARD_ITEM_ID.equals(expectedType)
                        || row.getTags().contains(AetherhavenConstants.POI_TAG_QUEST_BOARD);
                boolean matches =
                    actual != null
                        && (blockTypeIdMatches(remapped, actual) || blockTypeIdMatches(expectedType, actual));
                if (optionalQuestBoard && !matches) {
                    LOGGER.atFine().log(
                        "Skipping optional quest board POI near %s,%s,%s (no board block; center was %s)",
                        wx,
                        wy,
                        wz,
                        actual
                    );
                    return null;
                }
            }
            int standY = VillagerBlockUtil.resolveClearStandFeetY(world, wx, wy, wz);
            if (standY != Integer.MIN_VALUE) {
                wy = standY;
            }
            BlockType at = ChunkSectionBlockUtil.blockType(world, wx, wy, wz);
            if (at == null || at.getId() == null || "Empty".equalsIgnoreCase(at.getId())) {
                Vector3i support = VillagerBlockUtil.resolveSupportBlockFromClick(world, new Vector3i(wx, wy, wz));
                BlockType supportType = ChunkSectionBlockUtil.blockType(world, support.x, support.y, support.z);
                if (supportType != null && supportType.getId() != null) {
                    resolvedExpectedType = supportType.getId();
                }
            } else {
                resolvedExpectedType = at.getId();
            }
        } else if (expectedType != null) {
            resolvedExpectedType = expectedType;
        }

        Float yawRadians = null;
        Float localYawDeg = row.getInteractionTargetYawDegrees();
        if (localYawDeg != null) {
            double worldDeg = localYawDeg + prefabYaw.getDegrees();
            worldDeg = ((worldDeg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            yawRadians = (float) Math.toRadians(worldDeg);
        }

        return new PoiEntry(
            UUID.randomUUID(),
            town.getTownId(),
            wx,
            wy,
            wz,
            row.getTags(),
            row.getCapacity(),
            plotId,
            resolvedExpectedType,
            row.getInteractionKind(),
            row.getInteractionKind() == PoiInteractionKind.SIT
                || row.getInteractionKind() == PoiInteractionKind.SLEEP,
            row.getEquipmentProfileId(),
            null,
            null,
            null,
            yawRadians,
            row.getWorkResidentKind()
        );
    }

    @Nullable
    private static Vector3i resolveAnchorForExpectedBlock(
        @Nonnull World world,
        int cx,
        int cy,
        int cz,
        @Nonnull String expectedType
    ) {
        BlockType center = ChunkSectionBlockUtil.blockType(world, cx, cy, cz);
        if (center != null && blockTypeIdMatches(expectedType, center.getId())) {
            return VillagerBlockUtil.resolveMountBaseBlock(world, cx, cy, cz);
        }
        int bestX = 0;
        int bestY = 0;
        int bestZ = 0;
        long bestD2 = Long.MAX_VALUE;
        int bestFillerPenalty = Integer.MAX_VALUE;
        boolean found = false;
        for (int dy = -ANCHOR_SEARCH_Y; dy <= ANCHOR_SEARCH_Y; dy++) {
            for (int dx = -ANCHOR_SEARCH_XY; dx <= ANCHOR_SEARCH_XY; dx++) {
                for (int dz = -ANCHOR_SEARCH_XY; dz <= ANCHOR_SEARCH_XY; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    BlockType bt = ChunkSectionBlockUtil.blockType(world, x, y, z);
                    if (bt == null || !blockTypeIdMatches(expectedType, bt.getId())) {
                        continue;
                    }
                    Vector3i base = VillagerBlockUtil.resolveMountBaseBlock(world, x, y, z);
                    int bdx = base.x - cx;
                    int bdy = base.y - cy;
                    int bdz = base.z - cz;
                    long d2 = (long) bdx * bdx + (long) bdy * bdy + (long) bdz * bdz;
                    int fillerPenalty = isFillerVoxel(world, x, y, z) ? 1 : 0;
                    if (!found
                        || fillerPenalty < bestFillerPenalty
                        || (fillerPenalty == bestFillerPenalty && d2 < bestD2)) {
                        found = true;
                        bestFillerPenalty = fillerPenalty;
                        bestD2 = d2;
                        bestX = base.x;
                        bestY = base.y;
                        bestZ = base.z;
                    }
                }
            }
        }
        return found ? new Vector3i(bestX, bestY, bestZ) : null;
    }

    private static boolean isFillerVoxel(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, 
            com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z)
        );
        if (chunk == null) {
            return false;
        }
        return ChunkSectionBlockUtil.filler(world, x, y, z) != FillerBlockUtil.NO_FILLER;
    }

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

    private static boolean isPlotCreatorEditorMarkerBlock(@Nonnull String blockTypeId) {
        return switch (blockTypeId.trim()) {
            case "Editor_Empty", "Editor_Block", "Editor_Anchor" -> true;
            default -> false;
        };
    }
}
