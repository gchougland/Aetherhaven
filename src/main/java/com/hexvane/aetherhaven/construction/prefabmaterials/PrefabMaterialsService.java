package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Generates and writes PrefabMaterials JSON files from prefab block lists. */
public final class PrefabMaterialsService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PrefabMaterialsGenerator generator;

    public PrefabMaterialsService(@Nonnull PrefabMaterialsGenerator generator) {
        this.generator = generator;
    }

    @Nonnull
    public static PrefabMaterialsService fromClassLoader(@Nonnull ClassLoader classLoader) {
        return new PrefabMaterialsService(PrefabMaterialsGenerator.fromClasspath(classLoader));
    }

    /** Writes PrefabMaterials for every construction in the catalog that has a resolvable prefab. */
    public int generateAllForCatalog(@Nonnull ConstructionCatalog catalog, @Nonnull Path dataDirectory) {
        int written = 0;
        for (String id : catalog.ids()) {
            ConstructionDefinition def = catalog.get(id);
            if (def == null) {
                continue;
            }
            String prefabPath = def.getPrefabPath();
            if (prefabPath == null || prefabPath.isBlank()) {
                continue;
            }
            if (generateOne(id, prefabPath, dataDirectory)) {
                written++;
            }
        }
        LOGGER.atInfo().log("Generated prefab materials for %s construction(s)", written);
        return written;
    }

    /** @return true if a file was written */
    public boolean generateOne(@Nonnull String constructionId, @Nonnull String prefabPathKey, @Nonnull Path dataDirectory) {
        Path prefabFile = resolvePrefabFile(prefabPathKey, dataDirectory);
        if (prefabFile == null || !Files.isRegularFile(prefabFile)) {
            LOGGER.atWarning().log("Skip prefab materials for %s: prefab not found (%s)", constructionId, prefabPathKey);
            return false;
        }
        try {
            List<MaterialRequirement> materials = generator.generateFromPrefabPath(prefabFile);
            Path out = PrefabMaterialsWriter.outputFile(dataDirectory, constructionId);
            PrefabMaterialsWriter.write(out, constructionId, prefabPathKey.trim(), materials);
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to generate prefab materials for %s", constructionId);
            return false;
        }
    }

    @Nonnull
    public List<MaterialRequirement> generateFromSessionPrefab(
        @Nonnull String prefabPathKey,
        @Nonnull Path dataDirectory
    ) {
        Path prefabFile = resolvePrefabFile(prefabPathKey, dataDirectory);
        if (prefabFile == null || !Files.isRegularFile(prefabFile)) {
            return List.of();
        }
        try {
            return generator.generateFromPrefabPath(prefabFile);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read prefab for materials: %s", prefabPathKey);
            return List.of();
        }
    }

    @Nullable
    private static Path resolvePrefabFile(@Nonnull String prefabPathKey, @Nonnull Path dataDirectory) {
        Path data = CustomBuildingsPaths.resolvePrefabFile(dataDirectory, prefabPathKey);
        if (data != null) {
            return data;
        }
        return PrefabResolveUtil.resolvePrefabPath(prefabPathKey);
    }
}
