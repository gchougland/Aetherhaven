package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PoiExtractor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int ANCHOR_SEARCH_XY = 2;
    private static final int ANCHOR_SEARCH_Y = 3;

    private PoiExtractor() {}

    public static void registerForCompletedBuild(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull String constructionId,
        @Nonnull Vector3i prefabAnchorWorld,
        @Nonnull Rotation prefabYaw
    ) {
        ConstructionDefinition cdef = plugin.getConstructionCatalog().get(constructionId);
        if (cdef == null || cdef.getPois().isEmpty()) {
            if (cdef == null) {
                LOGGER.atInfo().log("No construction definition for id %s (POIs skipped)", constructionId);
            }
            return;
        }

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        reg.unregisterByPlotId(plotId);

        List<PoiEntry> batch = new ArrayList<>();
        for (BuildingPoisDefinition.PoiRow row : cdef.getPois()) {
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
                    LOGGER.atWarning().log(
                        "Skipping POI near %s,%s,%s: no blockTypeId %s in search volume (center was %s)",
                        wx,
                        wy,
                        wz,
                        expectedType,
                        actual
                    );
                    continue;
                }
                if (anchor.x != wx || anchor.y != wy || anchor.z != wz) {
                    LOGGER.atInfo().log(
                        "POI anchor shifted %s,%s,%s -> %s,%s,%s for %s",
                        wx,
                        wy,
                        wz,
                        anchor.x,
                        anchor.y,
                        anchor.z,
                        expectedType
                    );
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
                batch.add(
                    new PoiEntry(
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
                        itx,
                        ity,
                        itz
                    )
                );
            } else {
                batch.add(
                    new PoiEntry(
                        UUID.randomUUID(),
                        town.getTownId(),
                        wx,
                        wy,
                        wz,
                        row.getTags(),
                        row.getCapacity(),
                        plotId,
                        expectedType,
                        row.getInteractionKind()
                    )
                );
            }
        }
        reg.registerAll(batch);
        LOGGER.atInfo().log("Registered %s POIs for construction %s plot %s", batch.size(), constructionId, plotId);
    }

    /**
     * Furniture and multi-block props may not register {@code getBlockType} on the exact prefab-local cell; search a
     * small box (wider vertically) before skipping the POI.
     */
    @Nullable
    private static Vector3i resolveAnchorForExpectedBlock(
        @Nonnull World world,
        int cx,
        int cy,
        int cz,
        @Nonnull String expectedType
    ) {
        BlockType center = world.getBlockType(cx, cy, cz);
        if (center != null && expectedType.equals(center.getId())) {
            return new Vector3i(cx, cy, cz);
        }
        /*
         * Pick the *closest* matching block to the prefab hint cell. Nested-loop order used to return the first
         * match, which could snap two nearby bed POIs to the same multi-block bed.
         */
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
                    if (bt != null && expectedType.equals(bt.getId())) {
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
}
