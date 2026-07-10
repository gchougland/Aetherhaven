package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Full install of a community building (building JSON + prefab + icon). */
public final class CommunityDownloadService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private CommunityDownloadService() {}

    public enum InstallResult {
        SUCCESS,
        NOT_FOUND,
        DOWNLOAD_FAILED,
        IO_ERROR
    }

    @Nonnull
    public static InstallResult install(@Nonnull AetherhavenPlugin plugin, @Nonnull CommunityManifestEntry entry) {
        String id = entry.getId();
        Path dataDir = plugin.getDataDirectory();
        CommunityCatalogService catalog = plugin.getCommunityCatalogService();

        try {
            Files.createDirectories(CommunityPaths.buildingsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.prefabsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.iconsDirectory(dataDir));

            if (!CommunityPreviewCache.get().promotePreviewPrefab(dataDir, id)) {
                String prefabUrl = entry.getPrefabUrl();
                if (prefabUrl == null || prefabUrl.isBlank()) {
                    return InstallResult.NOT_FOUND;
                }
                byte[] prefab = CommunityHttpClient.getBytes(catalog.resolveUrl(prefabUrl));
                if (prefab == null || prefab.length == 0) {
                    return InstallResult.DOWNLOAD_FAILED;
                }
                Files.write(CommunityPaths.installedPrefabFile(dataDir, id), prefab);
            }

            Path buildingFile = CommunityPaths.buildingFile(dataDir, id);
            if (!Files.isRegularFile(buildingFile)) {
                String buildingUrl = entry.getBuildingUrl();
                if (buildingUrl == null || buildingUrl.isBlank()) {
                    return InstallResult.NOT_FOUND;
                }
                String buildingJson = CommunityHttpClient.getString(catalog.resolveUrl(buildingUrl));
                if (buildingJson == null || buildingJson.isBlank()) {
                    return InstallResult.DOWNLOAD_FAILED;
                }
                Files.writeString(buildingFile, buildingJson);
            }
            // Prefab is stored as {id}.prefab.json; rewrite creator-local prefabPath to match.
            CommunityBuildingJsonNormalizer.normalizeInstalledBuildingFile(buildingFile, id);

            Path iconFile = CommunityPaths.iconFile(dataDir, id);
            if (!Files.isRegularFile(iconFile)) {
                String iconUrl = entry.getIconUrl();
                if (iconUrl != null && !iconUrl.isBlank()) {
                    byte[] icon = CommunityHttpClient.getBytes(catalog.resolveUrl(iconUrl));
                    if (icon != null && icon.length > 0) {
                        Files.write(iconFile, icon);
                    }
                }
            }
            if (Files.isRegularFile(iconFile)) {
                CustomBuildingIconAssetRegistry.registerIconFile(plugin, iconFile);
                CommunityIconRegistry.registerIconFile(plugin, iconFile);
            }

            plugin.reloadConfigsAndAssetCatalogs();
            reportInstall(plugin, id);
            LOGGER.atInfo().log("Installed community building %s", id);
            return InstallResult.SUCCESS;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to install community building %s", id);
            return InstallResult.IO_ERROR;
        }
    }

    /** Best-effort install counter for the marketplace website; never fails the local install. */
    private static void reportInstall(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        try {
            String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
            if (base == null || base.isBlank()) {
                return;
            }
            String url = base.replaceAll("/+$", "") + "/api/v1/buildings/" + constructionId.trim() + "/download";
            CommunityHttpClient.postJson(url, Map.of(), "{}");
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to report community download for %s", constructionId);
        }
    }

    @Nullable
    public static InstallResult remove(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        Path dataDir = plugin.getDataDirectory();
        try {
            Files.deleteIfExists(CommunityPaths.buildingFile(dataDir, constructionId));
            Files.deleteIfExists(CommunityPaths.installedPrefabFile(dataDir, constructionId));
            CommunityPreviewCache.get().clearEntryPreview(plugin, constructionId);
            plugin.reloadConfigsAndAssetCatalogs();
            return InstallResult.SUCCESS;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove community building %s", constructionId);
            return InstallResult.IO_ERROR;
        }
    }
}
