package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.plotcreator.PlotTokenIconPng;
import com.hexvane.aetherhaven.prop.PropPaths;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Fetches owned marketplace submissions and prepares local files for building editor sessions. */
public final class CommunityMySubmissionsService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private CommunityMySubmissionsService() {}

    @Nonnull
    public static List<CommunityMySubmissionEntry> fetchOwnedSubmissions(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName
    ) {
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        if (!cfg.isEnabled() || cfg.getApiBaseUrl().isBlank()) {
            return List.of();
        }
        Map<String, String> headers = playerHeaders(playerUuid, playerName);
        String json = CommunityHttpClient.getString(cfg.getApiBaseUrl() + "/api/v1/my-submissions", headers);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            MySubmissionsResponse response = GSON.fromJson(json, MySubmissionsResponse.class);
            if (response == null || response.submissions == null) {
                return List.of();
            }
            return dedupeForPicker(response.submissions);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to parse my-submissions response");
            return List.of();
        }
    }

    /**
     * Ensures definition JSON and prefab exist locally for editing (building or prop).
     *
     * @return {@code null} on success, or an error key
     */
    @Nullable
    public static String ensureLocalFilesForEdit(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityMySubmissionEntry entry,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName
    ) {
        String catalogId = entry.catalogId();
        if (catalogId.isBlank()) {
            return "building_missing";
        }
        if (entry.isProp()) {
            return ensureLocalPropFilesForEdit(plugin, entry, playerUuid, playerName);
        }
        if (hasEditableLocalBuildingFiles(plugin.getDataDirectory(), catalogId)) {
            return null;
        }
        if (entry.isApproved()) {
            CommunityManifestEntry manifestEntry = resolveManifestEntry(plugin.getCommunityCatalogService(), catalogId);
            if (manifestEntry == null) {
                return "download_failed";
            }
            CommunityDownloadService.InstallResult result = CommunityDownloadService.install(
                plugin,
                manifestEntry,
                false,
                playerUuid
            );
            return installResultToError(result);
        }
        return downloadPendingBuildingSubmissionFiles(plugin, entry, playerUuid, playerName);
    }

    @Nullable
    private static String ensureLocalPropFilesForEdit(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityMySubmissionEntry entry,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName
    ) {
        String catalogId = entry.catalogId();
        if (hasEditableLocalPropFiles(plugin.getDataDirectory(), catalogId)) {
            return null;
        }
        if (entry.isApproved()) {
            CommunityManifestEntry manifestEntry = resolveManifestEntry(plugin.getCommunityCatalogService(), catalogId);
            if (manifestEntry == null) {
                return "download_failed";
            }
            CommunityDownloadService.InstallResult result = CommunityDownloadService.install(
                plugin,
                manifestEntry,
                false,
                playerUuid
            );
            return installResultToError(result);
        }
        return downloadPendingPropSubmissionFiles(plugin, entry, playerUuid, playerName);
    }

    @Nullable
    private static CommunityManifestEntry resolveManifestEntry(
        @Nonnull CommunityCatalogService catalog,
        @Nonnull String catalogId
    ) {
        CommunityManifestEntry manifestEntry = catalog.findEntry(catalogId);
        if (manifestEntry != null) {
            return manifestEntry;
        }
        catalog.refreshFromApi();
        return catalog.findEntry(catalogId);
    }

    @Nonnull
    private static List<CommunityMySubmissionEntry> dedupeForPicker(@Nonnull List<CommunityMySubmissionEntry> raw) {
        Map<String, CommunityMySubmissionEntry> byCatalogId = new LinkedHashMap<>();
        for (CommunityMySubmissionEntry entry : raw) {
            String catalogId = entry.catalogId().trim().toLowerCase(Locale.ROOT);
            if (catalogId.isEmpty()) {
                continue;
            }
            CommunityMySubmissionEntry existing = byCatalogId.get(catalogId);
            if (existing == null) {
                byCatalogId.put(catalogId, entry);
                continue;
            }
            if (entry.isPending() && existing.isApproved()) {
                entry.setLiveVersionExists(true);
                byCatalogId.put(catalogId, entry);
            } else if (entry.isPending() && existing.isPending()) {
                if (compareVersion(entry.getVersion(), existing.getVersion()) > 0) {
                    byCatalogId.put(catalogId, entry);
                }
            }
        }
        List<CommunityMySubmissionEntry> out = new ArrayList<>(byCatalogId.values());
        out.sort(Comparator.comparing(e -> e.getDisplayName().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static int compareVersion(@Nonnull String left, @Nonnull String right) {
        try {
            return Integer.compare(Integer.parseInt(left.trim()), Integer.parseInt(right.trim()));
        } catch (NumberFormatException e) {
            return left.compareToIgnoreCase(right);
        }
    }

    private static boolean hasEditableLocalBuildingFiles(@Nonnull Path dataDir, @Nonnull String catalogId) {
        if (Files.isRegularFile(CustomBuildingsPaths.buildingFile(dataDir, catalogId))) {
            Path prefab = CustomBuildingsPaths.resolvePrefabFile(dataDir, catalogId + ".prefab.json");
            if (prefab != null && Files.isRegularFile(prefab)) {
                return true;
            }
        }
        if (Files.isRegularFile(CommunityPaths.buildingFile(dataDir, catalogId))) {
            Path prefab = CommunityPaths.installedPrefabFile(dataDir, catalogId);
            if (Files.isRegularFile(prefab)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEditableLocalPropFiles(@Nonnull Path dataDir, @Nonnull String catalogId) {
        if (!Files.isRegularFile(PropPaths.propFileUnderDataDir(dataDir, catalogId))) {
            return false;
        }
        String prefabKey = PropPaths.prefabPathKeyFromPropId(catalogId);
        Path prefab = PropPaths.propPrefabFile(dataDir, PropPaths.prefabFileNameFromKey(prefabKey));
        return Files.isRegularFile(prefab);
    }

    @Nullable
    private static String downloadPendingBuildingSubmissionFiles(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityMySubmissionEntry entry,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName
    ) {
        String submissionId = entry.ownerDownloadSubmissionId();
        if (submissionId == null || submissionId.isBlank()) {
            return "download_failed";
        }
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        Map<String, String> headers = playerHeaders(playerUuid, playerName);
        String base = cfg.getApiBaseUrl() + "/api/v1/my-submissions/" + submissionId;
        byte[] buildingBytes = CommunityHttpClient.getBytes(base + "/building.json", headers);
        byte[] prefabBytes = CommunityHttpClient.getBytes(base + "/prefab.json", headers);
        if (buildingBytes == null || buildingBytes.length == 0 || prefabBytes == null || prefabBytes.length == 0) {
            return "download_failed";
        }
        CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(prefabBytes);
        if (!safety.isSafe()) {
            LOGGER.atWarning().log("Refused unsafe owned submission prefab %s: %s", submissionId, safety.detail());
            return "unsafe_prefab: " + safety.detail();
        }
        String catalogId = entry.catalogId();
        Path dataDir = plugin.getDataDirectory();
        try {
            Files.createDirectories(CustomBuildingsPaths.buildingsDirectory(dataDir));
            Files.createDirectories(CustomBuildingsPaths.prefabsDirectory(dataDir));
            Files.createDirectories(CustomBuildingsPaths.iconsDirectory(dataDir));
            Files.write(CustomBuildingsPaths.buildingFile(dataDir, catalogId), buildingBytes);
            Files.write(CustomBuildingsPaths.prefabsDirectory(dataDir).resolve(catalogId + ".prefab.json"), prefabBytes);
            byte[] iconBytes = CommunityHttpClient.getBytes(base + "/icon.png", headers);
            if (iconBytes != null && PlotTokenIconPng.isValid(iconBytes)) {
                Path iconFile = CustomBuildingsPaths.iconFile(dataDir, catalogId);
                PlotTokenIconPng.writeAtomically(iconFile, iconBytes);
                CustomBuildingIconAssetRegistry.registerIconFile(plugin, iconFile);
            }
            plugin.reloadConfigsAndAssetCatalogs();
            return null;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to download owned submission %s", submissionId);
            return "io_error";
        }
    }

    @Nullable
    private static String downloadPendingPropSubmissionFiles(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityMySubmissionEntry entry,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName
    ) {
        String submissionId = entry.ownerDownloadSubmissionId();
        if (submissionId == null || submissionId.isBlank()) {
            return "download_failed";
        }
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        Map<String, String> headers = playerHeaders(playerUuid, playerName);
        String base = cfg.getApiBaseUrl() + "/api/v1/my-submissions/" + submissionId;
        byte[] propBytes = CommunityHttpClient.getBytes(base + "/prop.json", headers);
        byte[] prefabBytes = CommunityHttpClient.getBytes(base + "/prefab.json", headers);
        if (propBytes == null || propBytes.length == 0 || prefabBytes == null || prefabBytes.length == 0) {
            return "download_failed";
        }
        CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(prefabBytes);
        if (!safety.isSafe()) {
            LOGGER.atWarning().log("Refused unsafe owned prop submission prefab %s: %s", submissionId, safety.detail());
            return "unsafe_prefab: " + safety.detail();
        }
        String catalogId = entry.catalogId();
        Path dataDir = plugin.getDataDirectory();
        String prefabKey = PropPaths.prefabPathKeyFromPropId(catalogId);
        try {
            Files.createDirectories(PropPaths.propsDirectory(dataDir));
            Files.createDirectories(PropPaths.propPrefabsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.iconsDirectory(dataDir));
            Files.createDirectories(CustomBuildingsPaths.iconsDirectory(dataDir));

            JsonObject root;
            try {
                root = GSON.fromJson(new String(propBytes, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
            } catch (Exception e) {
                root = null;
            }
            if (root == null) {
                root = new JsonObject();
            }
            root.addProperty("id", catalogId);
            root.addProperty("prefabPath", prefabKey);
            if (!root.has("displayName") || root.get("displayName").isJsonNull()) {
                root.addProperty("displayName", entry.getDisplayName());
            }
            String iconAsset = PropPaths.iconAssetPath(catalogId);
            root.addProperty("iconPath", iconAsset);
            Files.writeString(PropPaths.propFileUnderDataDir(dataDir, catalogId), GSON.toJson(root));
            Files.write(
                PropPaths.propPrefabFile(dataDir, PropPaths.prefabFileNameFromKey(prefabKey)),
                prefabBytes
            );

            byte[] iconBytes = CommunityHttpClient.getBytes(base + "/icon.png", headers);
            if (iconBytes != null && PlotTokenIconPng.isValid(iconBytes)) {
                Path iconFile = PropPaths.iconFile(dataDir, catalogId);
                PlotTokenIconPng.writeAtomically(iconFile, iconBytes);
                Path communityIcon = CommunityPaths.iconsDirectory(dataDir).resolve(PropPaths.iconFileName(catalogId));
                if (!communityIcon.equals(iconFile)) {
                    Files.createDirectories(communityIcon.getParent());
                    Files.write(communityIcon, iconBytes);
                }
                CustomBuildingIconAssetRegistry.registerIconFile(plugin, iconFile);
                com.hexvane.aetherhaven.prop.PropIconSync.afterIconRegistered(plugin, catalogId);
            }
            plugin.reloadConfigsAndAssetCatalogs();
            return null;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to download owned prop submission %s", submissionId);
            return "io_error";
        }
    }

    @Nullable
    private static String installResultToError(@Nonnull CommunityDownloadService.InstallResult result) {
        return switch (result) {
            case SUCCESS -> null;
            case NOT_FOUND -> "download_failed";
            case DOWNLOAD_FAILED -> "download_failed";
            case IO_ERROR -> "io_error";
            case MISSING_MODS -> "missing_mods";
            case UNSAFE_PREFAB -> "unsafe_prefab";
            case ICON_FAILED -> "icon_failed";
        };
    }

    @Nonnull
    private static Map<String, String> playerHeaders(@Nonnull UUID playerUuid, @Nonnull String playerName) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Player-Uuid", playerUuid.toString().trim().toLowerCase(Locale.ROOT));
        headers.put("X-Player-Name", playerName);
        return headers;
    }

    private static final class MySubmissionsResponse {
        @SerializedName("submissions")
        private List<CommunityMySubmissionEntry> submissions;
    }
}
