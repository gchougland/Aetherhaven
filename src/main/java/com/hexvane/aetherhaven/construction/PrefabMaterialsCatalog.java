package com.hexvane.aetherhaven.construction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hexvane.aetherhaven.construction.prefabmaterials.PrefabMaterialItemIds;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsLoader;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Hard-mode material lists per construction id from {@code Buildings/PrefabMaterials/}. */
public final class PrefabMaterialsCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, List<MaterialRequirement>> byConstructionId;

    private PrefabMaterialsCatalog(@Nonnull Map<String, List<MaterialRequirement>> byConstructionId) {
        this.byConstructionId = byConstructionId;
    }

    @Nonnull
    public static PrefabMaterialsCatalog empty() {
        return new PrefabMaterialsCatalog(Collections.emptyMap());
    }

    @Nonnull
    public static PrefabMaterialsCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        return loadFromAssetPacksOrClasspath(classLoader, null);
    }

    @Nonnull
    public static PrefabMaterialsCatalog loadFromAssetPacksOrClasspath(
        @Nonnull ClassLoader classLoader,
        @Nullable Path dataDirectory
    ) {
        Gson gson = new GsonBuilder().create();
        Map<String, List<MaterialRequirement>> map = new LinkedHashMap<>();
        List<PackJsonFile> packFiles =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.PREFAB_MATERIALS);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                loadFile(gson, f.absolutePath(), map);
            }
        } else {
            for (String path : ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.prefabMaterialsPrefix())) {
                try (InputStream in = classLoader.getResourceAsStream(path)) {
                    if (in == null) {
                        continue;
                    }
                    loadStream(gson, in, path, map);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load prefab materials %s", path);
                }
            }
        }
        if (dataDirectory != null) {
            CustomBuildingsLoader.overlayPrefabMaterialsFromDataDirectory(gson, dataDirectory, map);
        }
        LOGGER.atInfo().log("Loaded prefab materials for %s construction(s)", map.size());
        return new PrefabMaterialsCatalog(Collections.unmodifiableMap(map));
    }

    private static void loadFile(@Nonnull Gson gson, @Nonnull Path path, @Nonnull Map<String, List<MaterialRequirement>> map) {
        try (InputStream in = Files.newInputStream(path)) {
            loadStream(gson, in, path.toString(), map);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load prefab materials file %s", path);
        }
    }

    private static void loadStream(
        @Nonnull Gson gson,
        @Nonnull InputStream in,
        @Nonnull String label,
        @Nonnull Map<String, List<MaterialRequirement>> map
    ) {
        PrefabMaterialsFile file = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), PrefabMaterialsFile.class);
        if (file == null || file.constructionId == null || file.constructionId.isBlank()) {
            LOGGER.atWarning().log("Prefab materials file missing constructionId: %s", label);
            return;
        }
        List<MaterialRequirement> mats = file.materials != null ? file.materials : List.of();
        map.put(file.constructionId.trim(), PrefabMaterialItemIds.mergeNormalized(mats));
    }

    @Nonnull
    public List<MaterialRequirement> getMaterials(@Nonnull String constructionId) {
        List<MaterialRequirement> list = byConstructionId.get(constructionId);
        return list != null ? list : List.of();
    }

    public boolean has(@Nonnull String constructionId) {
        return byConstructionId.containsKey(constructionId);
    }

    private static final class PrefabMaterialsFile {
        @SerializedName("constructionId")
        @Nullable
        String constructionId;

        @SerializedName("materials")
        @Nullable
        List<MaterialRequirement> materials;
    }
}
