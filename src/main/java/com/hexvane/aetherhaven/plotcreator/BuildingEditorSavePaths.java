package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves where the building editor writes building JSON and prefabs: config root, writable pack root that
 * already contains the file, then data-dir overlay.
 */
public final class BuildingEditorSavePaths {
    private BuildingEditorSavePaths() {}

    @Nonnull
    public static Path resolveWriteRoot(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        String configured = plugin.getConfig().get().getBuildingEditorWriteRoot();
        if (configured != null && !configured.isBlank()) {
            Path root = Path.of(configured.trim()).toAbsolutePath().normalize();
            if (Files.isDirectory(root) && Files.isWritable(root)) {
                return root;
            }
        }
        Path packRoot = findWritablePackRootContainingBuilding(constructionId);
        if (packRoot != null) {
            return packRoot;
        }
        return plugin.getDataDirectory().toAbsolutePath().normalize();
    }

    @Nonnull
    public static Path buildingFile(@Nonnull Path writeRoot, @Nonnull String constructionId) {
        return writeRoot.resolve(AetherhavenAssetPaths.BUILDINGS).resolve(constructionId.trim() + ".json");
    }

    @Nonnull
    public static Path prefabFile(@Nonnull Path writeRoot, @Nullable String prefabPathKey) {
        String fileName = prefabFileName(prefabPathKey);
        return writeRoot.resolve(CustomBuildingsPaths.PREFABS_RELATIVE).resolve(fileName);
    }

    @Nonnull
    public static String prefabFileName(@Nullable String prefabPathKey) {
        if (prefabPathKey == null || prefabPathKey.isBlank()) {
            return "missing.prefab.json";
        }
        String key = prefabPathKey.trim().replace('\\', '/');
        String fileName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        if (!fileName.endsWith(".prefab.json")) {
            fileName = fileName + ".prefab.json";
        }
        return fileName;
    }

    /**
     * Best existing source file for a building JSON (for merge snapshot), or null.
     */
    @Nullable
    public static Path findExistingBuildingFile(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        String id = constructionId.trim();
        Path configured = null;
        String root = plugin.getConfig().get().getBuildingEditorWriteRoot();
        if (root != null && !root.isBlank()) {
            configured = buildingFile(Path.of(root.trim()).toAbsolutePath().normalize(), id);
            if (Files.isRegularFile(configured)) {
                return configured;
            }
        }
        AssetModule module = AssetModule.get();
        if (module != null) {
            for (AssetPack pack : module.getAssetPacks()) {
                Path candidate = pack.getRoot().resolve(AetherhavenAssetPaths.BUILDINGS).resolve(id + ".json");
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
        }
        Path dataFile = CustomBuildingsPaths.buildingFile(plugin.getDataDirectory(), id);
        if (Files.isRegularFile(dataFile)) {
            return dataFile;
        }
        return configured != null && Files.isRegularFile(configured) ? configured : null;
    }

    /** Prefab must exist at the write target or be resolvable from the asset store. */
    public static boolean prefabExistsForEditor(@Nonnull AetherhavenPlugin plugin, @Nonnull PlotCreatorDraft draft) {
        String key = draft.getPrefabPath();
        if (key == null || key.isBlank()) {
            return false;
        }
        Path writeRoot = resolveWriteRoot(plugin, draft.getConstructionId() != null ? draft.getConstructionId() : "");
        Path underRoot = prefabFile(writeRoot, key);
        if (Files.isRegularFile(underRoot)) {
            return true;
        }
        Path dataFile = CustomBuildingsPaths.resolvePrefabFile(plugin.getDataDirectory(), key);
        if (dataFile != null && Files.isRegularFile(dataFile)) {
            return true;
        }
        return PrefabResolveUtil.resolvePrefabPath(key) != null;
    }

    @Nullable
    private static Path findWritablePackRootContainingBuilding(@Nonnull String constructionId) {
        AssetModule module = AssetModule.get();
        if (module == null) {
            return null;
        }
        String id = constructionId.trim().toLowerCase(Locale.ROOT);
        for (AssetPack pack : module.getAssetPacks()) {
            Path root = pack.getRoot().toAbsolutePath().normalize();
            if (!Files.isDirectory(root) || !Files.isWritable(root)) {
                continue;
            }
            Path building = root.resolve(AetherhavenAssetPaths.BUILDINGS).resolve(id + ".json");
            if (Files.isRegularFile(building)) {
                return root;
            }
        }
        return null;
    }
}
