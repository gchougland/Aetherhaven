package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Generates and writes PrefabMaterials JSON files from prefab block lists. */
public final class PrefabMaterialsService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PrefabMaterialsGenerator generator;
    private final SuggestedResourceMaterialsGenerator suggestedGenerator;

    public PrefabMaterialsService(
        @Nonnull PrefabMaterialsGenerator generator,
        @Nonnull SuggestedResourceMaterialsGenerator suggestedGenerator
    ) {
        this.generator = generator;
        this.suggestedGenerator = suggestedGenerator;
    }

    @Nonnull
    public static PrefabMaterialsService fromClassLoader(@Nonnull ClassLoader classLoader) {
        PrefabMaterialConversionTable conversions = PrefabMaterialConversionTable.loadFromClasspath(classLoader);
        return new PrefabMaterialsService(
            new PrefabMaterialsGenerator(conversions),
            new SuggestedResourceMaterialsGenerator(conversions)
        );
    }

    /**
     * Writes PrefabMaterials only for constructions that do not already have pack/classpath materials or a data-dir
     * materials file. Explicit {@link #generateOne} still regenerates on building save.
     */
    public int generateAllForCatalog(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull Path dataDirectory,
        @Nonnull ClassLoader classLoader
    ) {
        Set<String> known = knownPackOrClasspathConstructionIds(classLoader);
        int written = 0;
        int skipped = 0;
        for (String id : catalog.ids()) {
            ConstructionDefinition def = catalog.get(id);
            if (def == null) {
                continue;
            }
            String prefabPath = def.getPrefabPath();
            if (prefabPath == null || prefabPath.isBlank()) {
                continue;
            }
            if (known.contains(id) || Files.isRegularFile(PrefabMaterialsWriter.outputFile(dataDirectory, id))) {
                skipped++;
                continue;
            }
            if (generateOne(id, prefabPath, dataDirectory)) {
                written++;
            }
        }
        LOGGER.atInfo().log(
            "Generated prefab materials for %s construction(s) (skipped %s already present)",
            written,
            skipped
        );
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

    @Nonnull
    public List<MaterialRequirement> generateSuggestedResourcesFromSessionPrefab(
        @Nonnull String prefabPathKey,
        @Nonnull Path dataDirectory
    ) {
        Path prefabFile = resolvePrefabFile(prefabPathKey, dataDirectory);
        if (prefabFile == null || !Files.isRegularFile(prefabFile)) {
            return List.of();
        }
        try {
            return suggestedGenerator.generateFromPrefabPath(prefabFile);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read prefab for suggested resources: %s", prefabPathKey);
            return List.of();
        }
    }

    @Nonnull
    private static Set<String> knownPackOrClasspathConstructionIds(@Nonnull ClassLoader classLoader) {
        Set<String> ids = new HashSet<>();
        List<PackJsonFile> packFiles =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.PREFAB_MATERIALS);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                String fileName = f.absolutePath().getFileName().toString();
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    ids.add(fileName.substring(0, fileName.length() - 5));
                }
            }
            return ids;
        }
        for (String path : ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.prefabMaterialsPrefix())) {
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            String fileName = slash >= 0 ? path.substring(slash + 1) : path;
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
                ids.add(fileName.substring(0, fileName.length() - 5));
            }
        }
        return ids;
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
