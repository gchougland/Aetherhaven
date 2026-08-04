package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.plotcreator.RuntimeCommonIconBroadcast;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Downloads, validates, and registers community building plot-token icons. */
public final class CommunityIconDownload {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {0L, 500L, 1500L};
    private static final int MIN_PNG_BYTES = 100;

    public enum Result {
        /** Manifest has no icon URL — fallback icon is acceptable. */
        NOT_REQUIRED,
        SUCCESS,
        DOWNLOAD_FAILED,
        INVALID_PNG,
        REGISTRATION_FAILED
    }

    private CommunityIconDownload() {}

    public static boolean iconRequired(@Nonnull CommunityManifestEntry entry) {
        String iconUrl = entry.getIconUrl();
        return iconUrl != null && !iconUrl.isBlank();
    }

    @Nonnull
    public static Result downloadRegisterAndValidate(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityManifestEntry entry,
        boolean forceRefresh
    ) {
        if (!iconRequired(entry)) {
            return Result.NOT_REQUIRED;
        }
        Path dataDir = plugin.getDataDirectory();
        Path iconFile = CommunityPaths.iconFile(dataDir, entry.getId());
        String assetPath = CustomBuildingsPaths.iconAssetPath(entry.getId());
        if (!forceRefresh && Files.isRegularFile(iconFile) && isRegistered(assetPath)) {
            return Result.SUCCESS;
        }
        byte[] png = downloadWithRetry(plugin, entry);
        if (png == null) {
            return Result.DOWNLOAD_FAILED;
        }
        if (!isValidPng(png)) {
            return Result.INVALID_PNG;
        }
        try {
            Files.createDirectories(iconFile.getParent());
            Files.write(iconFile, png);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to write community icon for %s", entry.getId());
            return Result.DOWNLOAD_FAILED;
        }
        return registerAndVerify(plugin, iconFile, entry.getId(), assetPath, forceRefresh);
    }

    /**
     * Ensures an on-disk icon is registered and client-safe. Re-downloads when missing or not registered.
     *
     * @return true when the icon is present and registered (or not required)
     */
    public static boolean ensureIconComplete(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityManifestEntry entry
    ) {
        Result result = downloadRegisterAndValidate(plugin, entry, false);
        return result == Result.NOT_REQUIRED || result == Result.SUCCESS;
    }

    public static boolean isIconCompleteOnDisk(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String constructionId,
        @Nullable CommunityManifestEntry manifestEntry
    ) {
        if (manifestEntry != null && !iconRequired(manifestEntry)) {
            return true;
        }
        Path iconFile = CommunityPaths.iconFile(plugin.getDataDirectory(), constructionId);
        if (!Files.isRegularFile(iconFile)) {
            return false;
        }
        return isRegistered(CustomBuildingsPaths.iconAssetPath(constructionId));
    }

    @Nullable
    private static byte[] downloadWithRetry(@Nonnull AetherhavenPlugin plugin, @Nonnull CommunityManifestEntry entry) {
        CommunityCatalogService catalog = plugin.getCommunityCatalogService();
        String iconUrl = entry.getIconUrl();
        if (iconUrl == null || iconUrl.isBlank()) {
            return null;
        }
        String absolute = catalog.resolveUrl(iconUrl);
        byte[] last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(BACKOFF_MS[attempt]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            last = CommunityHttpClient.getBytes(absolute);
            if (last != null && last.length > 0) {
                return last;
            }
            LOGGER.atWarning().log(
                "Community icon download attempt %s failed for %s from %s",
                attempt + 1,
                entry.getId(),
                absolute
            );
        }
        return last;
    }

    private static boolean isValidPng(@Nonnull byte[] png) {
        if (png.length < MIN_PNG_BYTES) {
            return false;
        }
        return png[0] == (byte) 0x89
            && png[1] == 0x50
            && png[2] == 0x4E
            && png[3] == 0x47;
    }

    @Nonnull
    private static Result registerAndVerify(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Path iconFile,
        @Nonnull String constructionId,
        @Nonnull String assetPath,
        boolean forceRefresh
    ) {
        CommonAsset asset = CommunityIconRegistry.registerIconFileNoSend(plugin, iconFile, forceRefresh);
        CustomBuildingIconAssetRegistry.registerIconFileNoSend(plugin, iconFile, forceRefresh);
        if (asset != null) {
            CommunityIconRegistry.broadcastAssets(List.of(asset));
            PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
        } else if (!isRegistered(assetPath)) {
            // File exists but registration was skipped (unchanged mtime) — still verify registry.
            CommonAsset existing = CommunityIconRegistry.registerIconFileNoSend(plugin, iconFile, true);
            CustomBuildingIconAssetRegistry.registerIconFileNoSend(plugin, iconFile, true);
            if (existing != null) {
                CommunityIconRegistry.broadcastAssets(List.of(existing));
                PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
            }
        }
        if (!isRegistered(assetPath)) {
            LOGGER.atWarning().log("Community icon for %s is on disk but not in CommonAssetRegistry", constructionId);
            return Result.REGISTRATION_FAILED;
        }
        return Result.SUCCESS;
    }

    public static boolean isRegistered(@Nonnull String assetPath) {
        return CommonAssetRegistry.hasCommonAsset(assetPath);
    }
}
