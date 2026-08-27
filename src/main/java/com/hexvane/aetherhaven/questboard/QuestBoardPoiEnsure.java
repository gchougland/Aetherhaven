package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import com.hexvane.aetherhaven.poi.PoiDualCellNormalize;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Finds or registers the guild hall quest board POI so existing towns (pois.json without the JSON row) still work.
 * Returns null quietly when no quest board block exists (community guild halls may omit it).
 */
public final class QuestBoardPoiEnsure {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private QuestBoardPoiEnsure() {}

    @Nullable
    public static PoiEntry findOrRegister(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull TownRecord town
    ) {
        try {
            return findOrRegisterInternal(plugin, world, town);
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).atMostEvery(1, TimeUnit.MINUTES).withCause(ex).log(
                "Quest board POI lookup failed for town %s (missing board is ok)",
                town.getTownId()
            );
            return null;
        }
    }

    @Nullable
    private static PoiEntry findOrRegisterInternal(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull TownRecord town
    ) {
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        List<PoiEntry> existing = reg.listByTownAndTag(town.getTownId(), AetherhavenConstants.POI_TAG_QUEST_BOARD);
        if (!existing.isEmpty()) {
            PoiEntry first = existing.get(0);
            long chunkIndex =
                com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(first.getX(), first.getZ());
            if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, chunkIndex) == null) {
                // Trust the registered POI while the board chunk is unloaded (do not delete staff placements).
                return first;
            }
            if (questBoardBlockPresent(world, first.getX(), first.getY(), first.getZ())) {
                return first;
            }
            // Stale POI from a community build that no longer has a board (or was rebuilt without one).
            reg.unregister(first.getId());
        }

        List<PlotInstance> halls =
            town.listCompletePlotsWithGameplayConstruction(
                plugin.getConstructionCatalog(),
                AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL
            );
        for (PlotInstance plot : halls) {
            ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
            if (def == null) {
                continue;
            }
            Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
            Rotation yaw = plot.resolvePrefabYaw();
            UUID plotId = plot.getPlotId();

            PoiEntry built = null;
            BuildingPoisDefinition.PoiRow row = findQuestBoardRow(def);
            if (row != null) {
                // Only use authored coords when a board is actually present there (or nearby).
                built = buildFromRow(world, town, plotId, row, anchor, yaw);
            }
            if (built == null) {
                // Scan only when looking for a real world block — never invent a POI for builds without a board.
                built = scanPlotForQuestBoard(world, town, plot, plotId);
            }
            if (built != null) {
                reg.register(built);
                return built;
            }
        }
        return null;
    }

    @Nullable
    private static BuildingPoisDefinition.PoiRow findQuestBoardRow(@Nonnull ConstructionDefinition def) {
        for (BuildingPoisDefinition.PoiRow row : def.getPois()) {
            if (row.getTags().contains(AetherhavenConstants.POI_TAG_QUEST_BOARD)) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private static PoiEntry buildFromRow(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull BuildingPoisDefinition.PoiRow row,
        @Nonnull Vector3i prefabAnchorWorld,
        @Nonnull Rotation prefabYaw
    ) {
        PoiDualCellNormalize.normalize(row);
        return buildFromLocal(
            world,
            town,
            plotId,
            prefabAnchorWorld,
            prefabYaw,
            row.getLocalX(),
            row.getLocalY(),
            row.getLocalZ()
        );
    }

    @Nullable
    private static PoiEntry buildFromLocal(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull Vector3i prefabAnchorWorld,
        @Nonnull Rotation prefabYaw,
        int localX,
        int localY,
        int localZ
    ) {
        Vector3i d = PrefabLocalOffset.rotate(prefabYaw, localX, localY, localZ);
        int wx = prefabAnchorWorld.x + d.x;
        int wy = prefabAnchorWorld.y + d.y;
        int wz = prefabAnchorWorld.z + d.z;
        Vector3i resolved = resolveQuestBoardBlock(world, wx, wy, wz);
        if (resolved == null) {
            return null;
        }
        return new PoiEntry(
            UUID.randomUUID(),
            town.getTownId(),
            resolved.x,
            resolved.y,
            resolved.z,
            Set.of(AetherhavenConstants.POI_TAG_QUEST_BOARD),
            1,
            plotId,
            AetherhavenConstants.QUEST_BOARD_ITEM_ID,
            PoiInteractionKind.NONE
        );
    }

    @Nullable
    private static PoiEntry scanPlotForQuestBoard(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull UUID plotId
    ) {
        var fp = plot.toFootprint();
        for (int y = fp.getMinY(); y <= fp.getMaxY(); y++) {
            for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    if (!questBoardBlockPresent(world, x, y, z)) {
                        continue;
                    }
                    return new PoiEntry(
                        UUID.randomUUID(),
                        town.getTownId(),
                        x,
                        y,
                        z,
                        Set.of(AetherhavenConstants.POI_TAG_QUEST_BOARD),
                        1,
                        plotId,
                        AetherhavenConstants.QUEST_BOARD_ITEM_ID,
                        PoiInteractionKind.NONE
                    );
                }
            }
        }
        return null;
    }

    private static boolean questBoardBlockPresent(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return false;
        }
        BlockType bt = ChunkSectionBlockUtil.blockType(world, x, y, z);
        return bt != null && AetherhavenConstants.QUEST_BOARD_ITEM_ID.equals(bt.getId());
    }

    @Nullable
    private static Vector3i resolveQuestBoardBlock(@Nonnull World world, int cx, int cy, int cz) {
        if (questBoardBlockPresent(world, cx, cy, cz)) {
            return new Vector3i(cx, cy, cz);
        }
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (questBoardBlockPresent(world, x, y, z)) {
                        return new Vector3i(x, y, z);
                    }
                }
            }
        }
        return null;
    }
}
