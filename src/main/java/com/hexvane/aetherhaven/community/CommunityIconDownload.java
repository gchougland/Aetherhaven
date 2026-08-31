package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.plotcreator.PlotTokenIconPng;
import com.hexvane.aetherhaven.prop.PropIconSync;
import com.hexvane.aetherhaven.prop.PropPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Downloads, validates, and registers community building plot-token icons. */
public final class CommunityIconDownload {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {0L, 500L, 1500L};

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

    /**
     * Downloads and atomically writes the icon PNG without registering it. Caller batches registration.
     *
     * @return the icon path on success; {@code null} when not required or download/validation failed
     */
    @Nullable
    public static Path downloadToDiskOnly(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityManifestEntry entry,
        boolean forceRefresh
    ) {
        if (!iconRequired(entry)) {
            return null;
        }
        Path iconFile = iconFileFor(plugin, entry);
        synchronized (PlotTokenIconPng.lockFor(entry.getId())) {
            if (!forceRefresh && PlotTokenIconPng.isValidFile(iconFile)) {
                return iconFile;
            }
            byte[] png = downloadWithRetry(plugin, entry);
            if (png == null) {
                return null;
            }
            if (!PlotTokenIconPng.isValid(png)) {
                LOGGER.atWarning().log("Community icon download returned invalid PNG for %s", entry.getId());
                return null;
            }
            try {
                PlotTokenIconPng.writeAtomically(iconFile, png);
                return iconFile;
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to write community icon for %s", entry.getId());
                return null;
            }
        }
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
        Path iconFile = iconFileFor(plugin, entry);
        String assetPath = iconAssetPathFor(entry);
        synchronized (PlotTokenIconPng.lockFor(entry.getId())) {
            if (!forceRefresh && PlotTokenIconPng.isValidFile(iconFile) && isRegistered(assetPath)) {
                return Result.SUCCESS;
            }
            byte[] png = downloadWithRetry(plugin, entry);
            if (png == null) {
                return Result.DOWNLOAD_FAILED;
            }
            if (!PlotTokenIconPng.isValid(png)) {
                return Result.INVALID_PNG;
            }
            try {
                PlotTokenIconPng.writeAtomically(iconFile, png);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to write community icon for %s", entry.getId());
                return Result.DOWNLOAD_FAILED;
            }
            return registerAndVerify(plugin, iconFile, entry.getId(), assetPath, true, entry.isProp());
        }
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
        boolean prop = manifestEntry != null && manifestEntry.isProp();
        Path iconFile =
            prop
                ? CommunityPaths.iconsDirectory(plugin.getDataDirectory()).resolve(PropPaths.iconFileName(constructionId))
                : CommunityPaths.iconFile(plugin.getDataDirectory(), constructionId);
        if (!PlotTokenIconPng.isValidFile(iconFile)) {
            return false;
        }
        String assetPath = prop ? PropPaths.iconAssetPath(constructionId) : CustomBuildingsPaths.iconAssetPath(constructionId);
        return isRegistered(assetPath);
    }

    @Nonnull
    private static Path iconFileFor(@Nonnull AetherhavenPlugin plugin, @Nonnull CommunityManifestEntry entry) {
        if (entry.isProp()) {
            return CommunityPaths.iconsDirectory(plugin.getDataDirectory()).resolve(PropPaths.iconFileName(entry.getId()));
        }
        return CommunityPaths.iconFile(plugin.getDataDirectory(), entry.getId());
    }

    @Nonnull
    private static String iconAssetPathFor(@Nonnull CommunityManifestEntry entry) {
        return entry.isProp() ? PropPaths.iconAssetPath(entry.getId()) : CustomBuildingsPaths.iconAssetPath(entry.getId());
    }

    /**
     * Removes an invalid on-disk community icon so repair can re-fetch it.
     *
     * @return true when the file was missing or deleted as invalid
     */
    public static boolean deleteInvalidIconIfPresent(@Nonnull Path iconFile) {
        return PlotTokenIconPng.deleteIfInvalid(iconFile);
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
            if (last != null && last.length > 0 && PlotTokenIconPng.isValid(last)) {
                return last;
            }
            LOGGER.atWarning().log(
                "Community icon download attempt %s failed for %s from %s",
                attempt + 1,
                entry.getId(),
                absolute
            );
        }
        return last != null && PlotTokenIconPng.isValid(last) ? last : null;
    }

    @Nonnull
    private static Result registerAndVerify(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Path iconFile,
        @Nonnull String entryId,
        @Nonnull String assetPath,
        boolean forceRefresh,
        boolean prop
    ) {
        CommonAsset asset = CommunityIconRegistry.registerIconFileNoSend(plugin, iconFile, forceRefresh);
        CustomBuildingIconAssetRegistry.registerIconFileNoSend(plugin, iconFile, forceRefresh);
        if (asset != null) {
            CommunityIconRegistry.broadcastAssets(List.of(asset));
            notifyIconRegistered(plugin, entryId, prop);
        } else if (!isRegistered(assetPath)) {
            // File exists but registration was skipped (unchanged mtime) — still verify registry.
            CommonAsset existing = CommunityIconRegistry.registerIconFileNoSend(plugin, iconFile, true);
            CustomBuildingIconAssetRegistry.registerIconFileNoSend(plugin, iconFile, true);
            if (existing != null) {
                CommunityIconRegistry.broadcastAssets(List.of(existing));
                notifyIconRegistered(plugin, entryId, prop);
            }
        }
        if (!isRegistered(assetPath)) {
            LOGGER.atWarning().log("Community icon for %s is on disk but not in CommonAssetRegistry", entryId);
            return Result.REGISTRATION_FAILED;
        }
        return Result.SUCCESS;
    }

    private static void notifyIconRegistered(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String entryId,
        boolean prop
    ) {
        if (prop) {
            PropIconSync.afterIconRegistered(plugin, entryId);
        } else {
            PlotTokenIconSync.afterIconRegistered(plugin, entryId);
        }
    }

    public static boolean isRegistered(@Nonnull String assetPath) {
        return CommonAssetRegistry.hasCommonAsset(assetPath);
    }
}
