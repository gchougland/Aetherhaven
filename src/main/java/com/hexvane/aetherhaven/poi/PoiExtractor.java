package com.hexvane.aetherhaven.poi;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PoiExtractor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
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
        ClassLoader cl = plugin.getClass().getClassLoader();
        String path = "Server/Buildings/" + constructionId + ".json";
        InputStream in = cl.getResourceAsStream(path);
        if (in == null) {
            LOGGER.atInfo().log("No building POI file at classpath %s", path);
            return;
        }
        BuildingPoisDefinition def;
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            def = GSON.fromJson(r, BuildingPoisDefinition.class);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read %s", path);
            return;
        }
        if (def == null || def.getPois().isEmpty()) {
            return;
        }

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        reg.unregisterByPlotId(plotId);

        List<PoiEntry> batch = new ArrayList<>();
        for (BuildingPoisDefinition.PoiRow row : def.getPois()) {
            Vector3i d = PrefabLocalOffset.rotate(prefabYaw, row.getLocalX(), row.getLocalY(), row.getLocalZ());
            int wx = prefabAnchorWorld.x + d.x;
            int wy = prefabAnchorWorld.y + d.y;
            int wz = prefabAnchorWorld.z + d.z;
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
                        return new Vector3i(x, y, z);
                    }
                }
            }
        }
        return null;
    }
}
