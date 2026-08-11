package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.community.CommunityBuildingValidator;
import com.hexvane.aetherhaven.community.CommunityPaths;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves where the building editor writes building / festival JSON and prefabs: config root
 * ({@code BuildingEditorWriteRoot}), writable pack root that already contains the file, then data-dir overlay.
 */
public final class BuildingEditorSavePaths {
    private static final String SERVER_PREFABS_PREFIX = "Server/Prefabs/";

    private BuildingEditorSavePaths() {}

    @Nonnull
    public static Path resolveWriteRoot(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        String id = constructionId.trim();
        if (CommunityBuildingValidator.isValidCommunityId(id)) {
            Path dataDir = plugin.getDataDirectory().toAbsolutePath().normalize();
            if (Files.isRegularFile(CustomBuildingsPaths.buildingFile(dataDir, id))) {
                return dataDir;
            }
            if (Files.isRegularFile(CommunityPaths.buildingFile(dataDir, id))) {
                return CommunityPaths.communityRoot(dataDir);
            }
        }
        Path configured = configuredWriteRoot(plugin);
        if (configured != null) {
            return configured;
        }
        Path packRoot = findWritablePackRootContainingBuilding(constructionId);
        if (packRoot != null) {
            return packRoot;
        }
        return plugin.getDataDirectory().toAbsolutePath().normalize();
    }

    /** Same sync-assets root as buildings, preferring a pack that already has the festival JSON. */
    @Nonnull
    public static Path resolveWriteRootForFestival(@Nonnull AetherhavenPlugin plugin, @Nonnull String festivalId) {
        Path configured = configuredWriteRoot(plugin);
        if (configured != null) {
            return configured;
        }
        Path packRoot = findWritablePackRootContainingFestival(festivalId);
        if (packRoot != null) {
            return packRoot;
        }
        return plugin.getDataDirectory().toAbsolutePath().normalize();
    }

    @Nullable
    public static Path configuredWriteRoot(@Nonnull AetherhavenPlugin plugin) {
        String configured = plugin.getConfig().get().getBuildingEditorWriteRoot();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path root = Path.of(configured.trim()).toAbsolutePath().normalize();
        if (Files.isDirectory(root) && Files.isWritable(root)) {
            return root;
        }
        return null;
    }

    @Nonnull
    public static Path buildingFile(@Nonnull Path writeRoot, @Nonnull String constructionId) {
        return writeRoot.resolve(AetherhavenAssetPaths.BUILDINGS).resolve(constructionId.trim() + ".json");
    }

    @Nonnull
    public static Path festivalFile(@Nonnull Path writeRoot, @Nonnull String festivalId) {
        return writeRoot.resolve(AetherhavenAssetPaths.FESTIVALS).resolve(festivalId.trim() + ".json");
    }

    /**
     * Prefab on disk under {@code Server/Prefabs/}, preserving subfolders from the catalog key
     * (e.g. {@code Festivals/Festival_Square.prefab.json}).
     */
    @Nonnull
    public static Path prefabFile(@Nonnull Path writeRoot, @Nullable String prefabPathKey) {
        return writeRoot.resolve(CustomBuildingsPaths.PREFABS_RELATIVE).resolve(prefabRelativeUnderPrefabs(prefabPathKey));
    }

    /** Filename only, for player-facing messages. */
    @Nonnull
    public static String prefabFileName(@Nullable String prefabPathKey) {
        String relative = prefabRelativeUnderPrefabs(prefabPathKey).replace('\\', '/');
        int slash = relative.lastIndexOf('/');
        return slash >= 0 ? relative.substring(slash + 1) : relative;
    }

    /**
     * Path relative to {@code Server/Prefabs/}. Keeps {@code Festivals/...} so festival-square saves do not land in
     * the Prefabs root while the catalog still points at {@code Festivals/Festival_Square.prefab.json}.
     */
    @Nonnull
    public static String prefabRelativeUnderPrefabs(@Nullable String prefabPathKey) {
        if (prefabPathKey == null || prefabPathKey.isBlank()) {
            return "missing.prefab.json";
        }
        String key = prefabPathKey.trim().replace('\\', '/');
        while (key.startsWith("./")) {
            key = key.substring(2);
        }
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.regionMatches(true, 0, SERVER_PREFABS_PREFIX, 0, SERVER_PREFABS_PREFIX.length())) {
            key = key.substring(SERVER_PREFABS_PREFIX.length());
        }
        if (key.contains("..")) {
            String fileName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
            if (!fileName.endsWith(".prefab.json")) {
                fileName = fileName + ".prefab.json";
            }
            return fileName;
        }
        if (!key.endsWith(".prefab.json")) {
            key = key + ".prefab.json";
        }
        return key;
    }

    /**
     * Best existing source file for a building JSON (for merge snapshot), or null.
     */
    @Nullable
    public static Path findExistingBuildingFile(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        String id = constructionId.trim();
        Path configured = null;
        Path writeRoot = configuredWriteRoot(plugin);
        if (writeRoot != null) {
            configured = buildingFile(writeRoot, id);
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
        Path communityFile = CommunityPaths.buildingFile(plugin.getDataDirectory(), id);
        if (Files.isRegularFile(communityFile)) {
            return communityFile;
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

    @Nullable
    private static Path findWritablePackRootContainingFestival(@Nonnull String festivalId) {
        AssetModule module = AssetModule.get();
        if (module == null) {
            return null;
        }
        String id = festivalId.trim().toLowerCase(Locale.ROOT);
        for (AssetPack pack : module.getAssetPacks()) {
            Path root = pack.getRoot().toAbsolutePath().normalize();
            if (!Files.isDirectory(root) || !Files.isWritable(root)) {
                continue;
            }
            Path festival = root.resolve(AetherhavenAssetPaths.FESTIVALS).resolve(id + ".json");
            if (Files.isRegularFile(festival)) {
                return root;
            }
        }
        return null;
    }
}
