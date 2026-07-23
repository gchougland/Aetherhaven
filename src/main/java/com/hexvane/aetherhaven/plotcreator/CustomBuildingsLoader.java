package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.construction.prefabmaterials.PrefabMaterialItemIds;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Loads custom building JSON from the plugin data folder into a construction catalog map. */
public final class CustomBuildingsLoader {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private CustomBuildingsLoader() {}

    /**
     * @return construction ids loaded from the data directory (for custom marking)
     */
    @Nonnull
    public static Set<String> overlayBuildingsFromDataDirectory(
        @Nonnull Gson gson,
        @Nonnull Path dataDirectory,
        @Nonnull Map<String, ConstructionDefinition> map
    ) {
        Path dir = CustomBuildingsPaths.buildingsDirectory(dataDirectory);
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        Set<String> customIds = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> !p.toString().replace('\\', '/').contains("/PrefabMaterials/"))
                .sorted()
                .forEach(p -> {
                    String id = loadOne(gson, p, map);
                    if (id != null) {
                        customIds.add(id);
                    }
                });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to walk custom buildings at %s", dir);
        }
        if (!customIds.isEmpty()) {
            LOGGER.atInfo().log("Loaded %s custom construction(s) from %s", customIds.size(), dir);
        }
        return Set.copyOf(customIds);
    }

    @Nonnull
    public static Set<String> overlayPrefabMaterialsFromDataDirectory(
        @Nonnull Gson gson,
        @Nonnull Path dataDirectory,
        @Nonnull Map<String, List<MaterialRequirement>> map
    ) {
        Path dir = CustomBuildingsPaths.prefabMaterialsDirectory(dataDirectory);
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().forEach(p -> {
                try (InputStream in = Files.newInputStream(p)) {
                    PrefabMaterialsFile file =
                        gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), PrefabMaterialsFile.class);
                    if (file != null && file.constructionId != null && !file.constructionId.isBlank()) {
                        String cid = file.constructionId.trim();
                        map.put(cid, PrefabMaterialItemIds.mergeNormalized(file.materials != null ? file.materials : List.of()));
                        ids.add(cid);
                    }
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load prefab materials %s", p);
                }
            });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to walk prefab materials at %s", dir);
        }
        return Set.copyOf(ids);
    }

    @Nullable
    private static String loadOne(@Nonnull Gson gson, @Nonnull Path path, @Nonnull Map<String, ConstructionDefinition> map) {
        try (InputStream in = Files.newInputStream(path)) {
            ConstructionDefinition def = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ConstructionDefinition.class);
            if (def == null || def.getId() == null || def.getId().isBlank()) {
                LOGGER.atWarning().log("Skipping custom building with missing id: %s", path);
                return null;
            }
            if (def.getPrefabPath() == null || def.getPrefabPath().isBlank()) {
                LOGGER.atWarning().log("Skipping custom building %s: missing prefabPath (%s)", def.getId(), path);
                return null;
            }
            String id = def.getId().trim();
            map.put(id, def);
            return id;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load custom building %s", path);
            return null;
        }
    }

    private static final class PrefabMaterialsFile {
        @SerializedName("constructionId")
        @javax.annotation.Nullable
        String constructionId;

        @SerializedName("materials")
        @javax.annotation.Nullable
        List<MaterialRequirement> materials;
    }
}
