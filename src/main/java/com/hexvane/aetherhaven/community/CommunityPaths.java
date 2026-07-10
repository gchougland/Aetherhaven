package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** On-disk layout for installed and preview community buildings under the plugin data directory. */
public final class CommunityPaths {
    public static final String ROOT = "Community";
    public static final String PREVIEW_DIR = ".preview";
    public static final String MODERATION_PREVIEW_DIR = ".moderation-preview";

    private CommunityPaths() {}

    @Nonnull
    public static Path communityRoot(@Nonnull Path dataDirectory) {
        return dataDirectory.resolve(ROOT);
    }

    @Nonnull
    public static Path buildingsDirectory(@Nonnull Path dataDirectory) {
        return communityRoot(dataDirectory).resolve(AetherhavenAssetPaths.BUILDINGS);
    }

    @Nonnull
    public static Path prefabsDirectory(@Nonnull Path dataDirectory) {
        return communityRoot(dataDirectory).resolve(CustomBuildingsPaths.PREFABS_RELATIVE);
    }

    @Nonnull
    public static Path iconsDirectory(@Nonnull Path dataDirectory) {
        return communityRoot(dataDirectory).resolve(CustomBuildingsPaths.ICONS_RELATIVE);
    }

    @Nonnull
    public static Path previewDirectory(@Nonnull Path dataDirectory) {
        return communityRoot(dataDirectory).resolve(PREVIEW_DIR);
    }

    @Nonnull
    public static Path moderationPreviewDirectory(@Nonnull Path dataDirectory) {
        return communityRoot(dataDirectory).resolve(MODERATION_PREVIEW_DIR);
    }

    @Nonnull
    public static Path buildingFile(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        return buildingsDirectory(dataDirectory).resolve(constructionId.trim() + ".json");
    }

    @Nonnull
    public static Path iconFile(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        return iconsDirectory(dataDirectory).resolve(CustomBuildingsPaths.iconFileName(constructionId));
    }

    @Nonnull
    public static Path previewPrefabFile(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        return previewDirectory(dataDirectory).resolve(constructionId.trim() + ".prefab.json");
    }

    @Nonnull
    public static Path moderationPreviewPrefabFile(@Nonnull Path dataDirectory, @Nonnull String submissionId) {
        return moderationPreviewDirectory(dataDirectory).resolve(submissionId.trim() + ".prefab.json");
    }

    @Nonnull
    public static Path installedPrefabFile(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        return prefabsDirectory(dataDirectory).resolve(constructionId.trim() + ".prefab.json");
    }

    @Nullable
    public static Path resolveInstalledPrefab(@Nonnull Path dataDirectory, @Nullable String prefabPathKey) {
        if (prefabPathKey == null || prefabPathKey.isBlank()) {
            return null;
        }
        String key = prefabPathKey.trim().replace('\\', '/');
        String fileName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        if (!fileName.endsWith(".prefab.json")) {
            fileName = fileName + ".prefab.json";
        }
        Path candidate = prefabsDirectory(dataDirectory).resolve(fileName);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    @Nullable
    public static Path resolvePreviewPrefab(@Nonnull Path dataDirectory, @Nullable String constructionId) {
        if (constructionId == null || constructionId.isBlank()) {
            return null;
        }
        Path candidate = previewPrefabFile(dataDirectory, constructionId);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    @Nullable
    public static Path resolveModerationPreviewPrefab(@Nonnull Path dataDirectory, @Nullable String submissionId) {
        if (submissionId == null || submissionId.isBlank()) {
            return null;
        }
        Path candidate = moderationPreviewPrefabFile(dataDirectory, submissionId);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    public static boolean isInstalled(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        return Files.isRegularFile(buildingFile(dataDirectory, constructionId));
    }

    @Nonnull
    public static String iconAssetPath(@Nonnull String constructionId) {
        return CustomBuildingsPaths.iconAssetPath(constructionId);
    }
}
