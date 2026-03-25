package com.hexvane.aetherhaven.poi;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PoiExtractor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

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
            batch.add(
                new PoiEntry(
                    UUID.randomUUID(),
                    town.getTownId(),
                    wx,
                    wy,
                    wz,
                    row.getTags(),
                    row.getCapacity(),
                    plotId
                )
            );
        }
        reg.registerAll(batch);
        LOGGER.atInfo().log("Registered %s POIs for construction %s plot %s", def.getPois().size(), constructionId, plotId);
    }
}
