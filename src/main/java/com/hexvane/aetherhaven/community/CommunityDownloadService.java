package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
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
        IO_ERROR,
        MISSING_MODS,
        UNSAFE_PREFAB,
        ICON_FAILED
    }

    @Nonnull
    public static InstallResult install(@Nonnull AetherhavenPlugin plugin, @Nonnull CommunityManifestEntry entry) {
        return install(plugin, entry, false);
    }

    @Nonnull
    public static InstallResult install(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityManifestEntry entry,
        boolean forceRefresh
    ) {
        if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
            return InstallResult.MISSING_MODS;
        }
        String id = entry.getId();
        Path dataDir = plugin.getDataDirectory();
        CommunityCatalogService catalog = plugin.getCommunityCatalogService();

        boolean wrotePrefabThisAttempt = false;
        boolean wroteBuildingThisAttempt = false;
        boolean wroteIconThisAttempt = false;

        try {
            Files.createDirectories(CommunityPaths.buildingsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.prefabsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.iconsDirectory(dataDir));

            Path installedPrefab = CommunityPaths.installedPrefabFile(dataDir, id);
            boolean havePrefab = Files.isRegularFile(installedPrefab);
            if (forceRefresh) {
                CommunityPreviewCache.get().clearEntryPreview(plugin, id);
                havePrefab = false;
            } else if (!havePrefab && CommunityPreviewCache.get().promotePreviewPrefab(dataDir, id)) {
                havePrefab = true;
            }
            if (!havePrefab) {
                String prefabUrl = entry.getPrefabUrl();
                if (prefabUrl == null || prefabUrl.isBlank()) {
                    return InstallResult.NOT_FOUND;
                }
                byte[] prefab = CommunityHttpClient.getBytes(catalog.resolveUrl(prefabUrl));
                if (prefab == null || prefab.length == 0) {
                    return InstallResult.DOWNLOAD_FAILED;
                }
                CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(prefab);
                if (!safety.isSafe()) {
                    LOGGER.atWarning().log("Refused unsafe community download %s: %s", id, safety.detail());
                    return InstallResult.UNSAFE_PREFAB;
                }
                Files.write(installedPrefab, prefab);
                wrotePrefabThisAttempt = true;
            }
            CommunityPrefabSafety.Result installedSafety =
                CommunityPrefabSafety.validate(Files.readAllBytes(installedPrefab));
            if (!installedSafety.isSafe()) {
                if (wrotePrefabThisAttempt) {
                    Files.deleteIfExists(installedPrefab);
                }
                LOGGER.atWarning().log("Refused unsafe installed community prefab %s: %s", id, installedSafety.detail());
                return InstallResult.UNSAFE_PREFAB;
            }

            Path buildingFile = CommunityPaths.buildingFile(dataDir, id);
            if (forceRefresh || !Files.isRegularFile(buildingFile)) {
                String buildingUrl = entry.getBuildingUrl();
                if (buildingUrl == null || buildingUrl.isBlank()) {
                    rollbackFreshInstall(dataDir, id, wrotePrefabThisAttempt, false, false);
                    return InstallResult.NOT_FOUND;
                }
                String buildingJson = CommunityHttpClient.getString(catalog.resolveUrl(buildingUrl));
                if (buildingJson == null || buildingJson.isBlank()) {
                    rollbackFreshInstall(dataDir, id, wrotePrefabThisAttempt, false, false);
                    return InstallResult.DOWNLOAD_FAILED;
                }
                Files.writeString(buildingFile, buildingJson);
                wroteBuildingThisAttempt = true;
            }
            CommunityBuildingJsonNormalizer.normalizeInstalledBuildingFile(buildingFile, id);

            boolean iconRequired = CommunityIconDownload.iconRequired(entry);
            if (iconRequired) {
                Path iconFile = CommunityPaths.iconFile(dataDir, id);
                boolean hadIconBefore = Files.isRegularFile(iconFile);
                CommunityIconDownload.Result iconResult =
                    CommunityIconDownload.downloadRegisterAndValidate(plugin, entry, forceRefresh || !hadIconBefore);
                wroteIconThisAttempt =
                    iconResult == CommunityIconDownload.Result.SUCCESS
                        && Files.isRegularFile(iconFile)
                        && (forceRefresh || !hadIconBefore);
                if (iconResult != CommunityIconDownload.Result.SUCCESS
                    && iconResult != CommunityIconDownload.Result.NOT_REQUIRED) {
                    LOGGER.atWarning().log(
                        "Community icon install failed for %s: %s",
                        id,
                        iconResult
                    );
                    if (!forceRefresh) {
                        rollbackFreshInstall(
                            dataDir,
                            id,
                            wrotePrefabThisAttempt,
                            wroteBuildingThisAttempt,
                            wroteIconThisAttempt
                        );
                    }
                    return InstallResult.ICON_FAILED;
                }
            }

            CommunityInstallVersion.writeInstalledVersion(dataDir, id, entry.getVersion());
            plugin.getCommunityCatalogService().markIconComplete(id);
            plugin.reloadConfigsAndAssetCatalogs();
            if (!forceRefresh) {
                reportInstall(plugin, id);
            }
            LOGGER.atInfo().log(forceRefresh ? "Updated community building %s" : "Installed community building %s", id);
            return InstallResult.SUCCESS;
        } catch (IOException e) {
            rollbackFreshInstall(
                dataDir,
                id,
                wrotePrefabThisAttempt,
                wroteBuildingThisAttempt,
                wroteIconThisAttempt
            );
            LOGGER.atWarning().withCause(e).log("Failed to install community building %s", id);
            return InstallResult.IO_ERROR;
        }
    }

    private static void rollbackFreshInstall(
        @Nonnull Path dataDir,
        @Nonnull String constructionId,
        boolean wrotePrefab,
        boolean wroteBuilding,
        boolean wroteIcon
    ) {
        try {
            if (wroteIcon) {
                Files.deleteIfExists(CommunityPaths.iconFile(dataDir, constructionId));
            }
            if (wroteBuilding) {
                Files.deleteIfExists(CommunityPaths.buildingFile(dataDir, constructionId));
            }
            if (wrotePrefab) {
                Files.deleteIfExists(CommunityPaths.installedPrefabFile(dataDir, constructionId));
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to roll back partial community install for %s", constructionId);
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
            CommunityInstallVersion.deleteInstalledVersion(dataDir, constructionId);
            CommunityPreviewCache.get().clearEntryPreview(plugin, constructionId);
            plugin.getCommunityCatalogService().markIconComplete(constructionId);
            plugin.reloadConfigsAndAssetCatalogs();
            return InstallResult.SUCCESS;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove community building %s", constructionId);
            return InstallResult.IO_ERROR;
        }
    }
}
