package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
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
        reg.unregisterByPlotId(plotId);

        List<PoiMarkerLocator.LocalMarkerRow> markers = PoiMarkerLocator.collectInPlot(store, plot, cdef);
        Set<String> markerLocalKeys = PoiMarkerLocator.markerLocalKeys(markers);

        List<PoiEntry> batch = new ArrayList<>();
        for (BuildingPoisDefinition.PoiRow row : cdef.getPois()) {
            String localKey = PoiMarkerLocator.localKey(row.getLocalX(), row.getLocalY(), row.getLocalZ());
            if (markerLocalKeys.contains(localKey)) {
                continue;
            }
            PoiEntry fromJson = buildFromJsonRow(world, town, plotId, row, prefabAnchorWorld, prefabYaw);
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
        if (!batch.isEmpty()) {
            reg.registerAll(batch);
        }
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
        @Nonnull Rotation prefabYaw
    ) {
        Vector3i d = PrefabLocalOffset.rotate(prefabYaw, row.getLocalX(), row.getLocalY(), row.getLocalZ());
        int baseWx = prefabAnchorWorld.x + d.x;
        int baseWy = prefabAnchorWorld.y + d.y;
        int baseWz = prefabAnchorWorld.z + d.z;
        int wx = baseWx;
        int wy = baseWy;
        int wz = baseWz;
        String expectedType = row.getBlockTypeId();
        if (expectedType != null) {
            Vector3i anchor = resolveAnchorForExpectedBlock(world, wx, wy, wz, expectedType);
            if (anchor == null) {
                BlockType at = world.getBlockType(wx, wy, wz);
                String actual = at != null ? at.getId() : null;
                // Community guild halls may list a quest board POI without placing the block — skip quietly.
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
                        "Skipping POI near %s,%s,%s: no blockTypeId %s in search volume (center was %s)",
                        wx,
                        wy,
                        wz,
                        expectedType,
                        actual
                    );
                }
                return null;
            }
            wx = anchor.x;
            wy = anchor.y;
            wz = anchor.z;
        }
        int deltaX = wx - baseWx;
        int deltaY = wy - baseWy;
        int deltaZ = wz - baseWz;

        Double itx = null;
        Double ity = null;
        Double itz = null;
        if (row.hasInteractionTargetLocal()) {
            Vector3i td =
                PrefabLocalOffset.rotate(
                    prefabYaw,
                    row.getInteractionTargetLocalX(),
                    row.getInteractionTargetLocalY(),
                    row.getInteractionTargetLocalZ()
                );
            int twx = prefabAnchorWorld.x + td.x + deltaX;
            int twy = prefabAnchorWorld.y + td.y + deltaY;
            int twz = prefabAnchorWorld.z + td.z + deltaZ;
            int standY = VillagerBlockUtil.findStandY(world, twx, twz, twy + 3);
            itx = twx + 0.5;
            itz = twz + 0.5;
            ity = standY != Integer.MIN_VALUE ? standY + 0.02 : twy + 0.5;
        }

        if (itx != null && ity != null && itz != null) {
            return new PoiEntry(
                UUID.randomUUID(),
                town.getTownId(),
                wx,
                wy,
                wz,
                row.getTags(),
                row.getCapacity(),
                plotId,
                expectedType,
                row.getInteractionKind(),
                row.getInteractionKind() == PoiInteractionKind.SIT
                    || row.getInteractionKind() == PoiInteractionKind.SLEEP,
                null,
                itx,
                ity,
                itz,
                null,
                row.getWorkResidentKind()
            );
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
            expectedType,
            row.getInteractionKind(),
            row.getInteractionKind() == PoiInteractionKind.SIT
                || row.getInteractionKind() == PoiInteractionKind.SLEEP,
            null,
            null,
            null,
            null,
            null,
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
        BlockType center = world.getBlockType(cx, cy, cz);
        if (center != null && blockTypeIdMatches(expectedType, center.getId())) {
            return new Vector3i(cx, cy, cz);
        }
        int bestX = 0;
        int bestY = 0;
        int bestZ = 0;
        long bestD2 = Long.MAX_VALUE;
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
                    BlockType bt = world.getBlockType(x, y, z);
                    if (bt != null && blockTypeIdMatches(expectedType, bt.getId())) {
                        long d2 = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                        if (!found || d2 < bestD2) {
                            found = true;
                            bestD2 = d2;
                            bestX = x;
                            bestY = y;
                            bestZ = z;
                        }
                    }
                }
            }
        }
        return found ? new Vector3i(bestX, bestY, bestZ) : null;
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
}
