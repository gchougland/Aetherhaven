package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/**
 * Keeps community building JSON aligned with on-disk install layout.
 *
 * <p>Installed prefabs are always stored as {@code {constructionId}.prefab.json}. Submitted building
 * JSON often still carries the creator's local {@code prefabPath} (e.g. {@code plot_my_house.prefab.json}),
 * which breaks placement preview and assembly for downloaders.
 */
public final class CommunityBuildingJsonNormalizer {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CommunityBuildingJsonNormalizer() {}

    @Nonnull
    public static String expectedPrefabPathKey(@Nonnull String constructionId) {
        return constructionId.trim() + ".prefab.json";
    }

    /** Rewrites {@code id} / {@code prefabPath} on disk when they do not match the community install id. */
    public static void normalizeInstalledBuildingFile(@Nonnull Path buildingFile, @Nonnull String constructionId) {
        if (!Files.isRegularFile(buildingFile)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(buildingFile, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) {
                return;
            }
            if (!normalizeJson(root, constructionId)) {
                return;
            }
            Files.writeString(buildingFile, GSON.toJson(root), StandardCharsets.UTF_8);
            LOGGER.atInfo().log(
                "Normalized community building %s prefabPath to %s",
                constructionId,
                expectedPrefabPathKey(constructionId)
            );
        } catch (IOException | RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Failed to normalize community building JSON %s", buildingFile);
        }
    }

    /** Ensures an in-memory definition points at the installed community prefab filename. */
    public static void normalizeDefinition(@Nonnull ConstructionDefinition def, @Nonnull String constructionId) {
        String expected = expectedPrefabPathKey(constructionId);
        String current = def.getPrefabPath();
        if (current == null || !expected.equals(current.trim())) {
            def.setPrefabPath(expected);
        }
    }

    /**
     * @return true when {@code root} was modified
     */
    public static boolean normalizeJson(@Nonnull JsonObject root, @Nonnull String constructionId) {
        String id = constructionId.trim();
        String expectedPrefab = expectedPrefabPathKey(id);
        boolean changed = false;

        if (!root.has("id") || root.get("id").isJsonNull() || !id.equals(root.get("id").getAsString())) {
            root.addProperty("id", id);
            changed = true;
        }

        String currentPrefab = null;
        if (root.has("prefabPath") && !root.get("prefabPath").isJsonNull()) {
            currentPrefab = root.get("prefabPath").getAsString();
        }
        if (currentPrefab == null || !expectedPrefab.equals(currentPrefab.trim())) {
            root.addProperty("prefabPath", expectedPrefab);
            changed = true;
        }
        return changed;
    }
}
