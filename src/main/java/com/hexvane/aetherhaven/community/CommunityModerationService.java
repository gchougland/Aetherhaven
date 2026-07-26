package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Fetches the moderation queue and drives approve / deny from the plot crafting bench. */
public final class CommunityModerationService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String ICON_ID_PREFIX = "mod_";
    private static final int ICON_DOWNLOAD_PARALLELISM = 4;

    private final AetherhavenPlugin plugin;
    private final ConcurrentHashMap<UUID, Boolean> apiModeratorAccess = new ConcurrentHashMap<>();
    private volatile List<CommunityPendingEntry> cachedPending = List.of();
    private volatile long lastFetchEpochMs;
    private final AtomicInteger iconFetchSerial = new AtomicInteger();
    private final ExecutorService iconExecutor =
        Executors.newFixedThreadPool(
            ICON_DOWNLOAD_PARALLELISM,
            r -> {
                Thread t = new Thread(r, "aetherhaven-moderation-icons");
                t.setDaemon(true);
                return t;
            }
        );

    public CommunityModerationService(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isMarketplaceEnabled() {
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        return cfg.isEnabled() && !cfg.getApiBaseUrl().isBlank();
    }

    /**
     * Checks Railway {@code ADMIN_HYTALE_UUIDS} via the moderation API using the opening player's in-game profile UUID.
     * Optional {@code ModeratorUuids} in mod config is a fast-path override.
     */
    public void refreshModeratorAccess(@Nonnull UUID playerUuid) {
        if (isConfigModerator(playerUuid)) {
            apiModeratorAccess.put(playerUuid, true);
            return;
        }
        if (!isMarketplaceEnabled()) {
            apiModeratorAccess.put(playerUuid, false);
            return;
        }
        String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
        int code = CommunityHttpClient.getResponseCode(base + "/api/v1/moderation/pending", moderatorHeaders(playerUuid));
        boolean allowed = code == 200;
        apiModeratorAccess.put(playerUuid, allowed);
        if (code == 403) {
            LOGGER.atInfo().log(
                "Player %s is not a marketplace moderator (add this in-game profile UUID to ADMIN_HYTALE_UUIDS on Railway)",
                playerUuid
            );
        } else if (code != 200) {
            LOGGER.atWarning().log("Moderation access probe for %s failed: HTTP %s", playerUuid, code);
        }
    }

    /** {@code true} when config grants access or a prior API probe has completed for this player. */
    public boolean hasModeratorAccessResult(@Nonnull UUID playerUuid) {
        return isConfigModerator(playerUuid) || apiModeratorAccess.containsKey(playerUuid);
    }

    public boolean isModerator(@Nonnull UUID playerUuid) {
        if (isConfigModerator(playerUuid)) {
            return true;
        }
        return Boolean.TRUE.equals(apiModeratorAccess.get(playerUuid));
    }

    public boolean isConfigModerator(@Nonnull UUID playerUuid) {
        return plugin.getConfig().get().getCommunityMarketplace().isModerator(playerUuid);
    }

    public boolean isQueueEmpty() {
        return cachedPending.isEmpty();
    }

    public boolean isPendingCacheStale() {
        if (!isMarketplaceEnabled()) {
            return false;
        }
        if (lastFetchEpochMs <= 0L) {
            return true;
        }
        long refreshMs = plugin.getConfig().get().getCommunityMarketplace().getManifestRefreshMinutes() * 60_000L;
        return System.currentTimeMillis() - lastFetchEpochMs >= refreshMs;
    }

    /** Refreshes the pending queue metadata only when the cached list is stale. */
    public void refreshPendingIfStale(@Nonnull UUID playerUuid) {
        if (!isPendingCacheStale()) {
            return;
        }
        refreshPending(playerUuid);
    }

    /** Refreshes the pending queue metadata only (icons load per visible page). */
    public void refreshPending(@Nonnull UUID playerUuid) {
        if (!isModerator(playerUuid)) {
            cachedPending = List.of();
            return;
        }
        String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
        String json = CommunityHttpClient.getString(base + "/api/v1/moderation/pending", moderatorHeaders(playerUuid));
        if (json == null || json.isBlank()) {
            LOGGER.atWarning().log("Moderation pending fetch failed from %s", base);
            return;
        }
        try {
            PendingResponse response = GSON.fromJson(json, PendingResponse.class);
            List<CommunityPendingEntry> entries =
                response != null && response.submissions != null ? response.submissions : List.of();
            cachedPending = List.copyOf(entries);
            lastFetchEpochMs = System.currentTimeMillis();
            LOGGER.atInfo().log("Moderation pending loaded: %s submissions", entries.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to parse moderation pending list");
        }
    }

    @Nonnull
    public List<CommunityPendingEntry> getPending() {
        return cachedPending;
    }

    @Nullable
    public CommunityPendingEntry findEntry(@Nonnull String submissionId) {
        String id = submissionId.trim();
        for (CommunityPendingEntry entry : cachedPending) {
            if (entry.getSubmissionId().equalsIgnoreCase(id)) {
                return entry;
            }
        }
        return null;
    }

    @Nonnull
    public List<PlotCraftingCatalog.GroupEntry> buildGroupEntries() {
        ObjectArrayList<PlotCraftingCatalog.GroupEntry> groups = new ObjectArrayList<>();
        for (CommunityPendingEntry entry : cachedPending) {
            groups.add(
                new PlotCraftingCatalog.GroupEntry(
                    entry.getSubmissionId(),
                    entry.getDisplayName(),
                    List.of(
                        new PlotCraftingCatalog.VariantEntry(
                            entry.getSubmissionId(),
                            entry.getDisplayName(),
                            entry.prefabPathKey()
                        )
                    )
                )
            );
        }
        groups.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return groups;
    }

    @Nullable
    public String loadPreview(@Nonnull CommunityPendingEntry entry, @Nonnull UUID playerUuid) {
        return CommunityModerationPreviewCache.get().loadPreview(plugin, entry, playerUuid.toString());
    }

    /**
     * Stages a pending submission under its proposed community id so a plot token can be crafted and placed for review.
     */
    @Nonnull
    public CommunityDownloadService.InstallResult installForReview(
        @Nonnull CommunityPendingEntry entry,
        @Nonnull UUID moderatorUuid
    ) {
        if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
            return CommunityDownloadService.InstallResult.MISSING_MODS;
        }
        String proposedId = entry.getProposedId().trim();
        if (!CommunityBuildingValidator.isValidCommunityId(proposedId)) {
            LOGGER.atWarning().log(
                "Moderation review install refused: invalid proposedId %s for %s",
                proposedId,
                entry.getSubmissionId()
            );
            return CommunityDownloadService.InstallResult.NOT_FOUND;
        }
        if (CommunityPaths.isInstalled(plugin.getDataDirectory(), proposedId)) {
            Path installed = CommunityPaths.installedPrefabFile(plugin.getDataDirectory(), proposedId);
            try {
                CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(Files.readAllBytes(installed));
                return safety.isSafe()
                    ? CommunityDownloadService.InstallResult.SUCCESS
                    : CommunityDownloadService.InstallResult.UNSAFE_PREFAB;
            } catch (Exception e) {
                return CommunityDownloadService.InstallResult.IO_ERROR;
            }
        }
        Path dataDir = plugin.getDataDirectory();
        String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
        Map<String, String> headers = moderatorHeaders(moderatorUuid);
        try {
            Files.createDirectories(CommunityPaths.buildingsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.prefabsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.iconsDirectory(dataDir));

            Path destPrefab = CommunityPaths.installedPrefabFile(dataDir, proposedId);
            Path previewPrefab = CommunityPaths.moderationPreviewPrefabFile(dataDir, entry.getSubmissionId());
            if (Files.isRegularFile(previewPrefab)) {
                CommunityPrefabSafety.Result safety =
                    CommunityPrefabSafety.validate(Files.readAllBytes(previewPrefab));
                if (!safety.isSafe()) {
                    LOGGER.atWarning().log(
                        "Moderation review install refused for %s: %s",
                        entry.getSubmissionId(),
                        safety.detail()
                    );
                    return CommunityDownloadService.InstallResult.UNSAFE_PREFAB;
                }
                Files.copy(previewPrefab, destPrefab, StandardCopyOption.REPLACE_EXISTING);
            } else {
                byte[] prefab = CommunityHttpClient.getBytes(entry.moderationPrefabUrl(base), headers);
                if (prefab == null || prefab.length == 0) {
                    return CommunityDownloadService.InstallResult.DOWNLOAD_FAILED;
                }
                CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(prefab);
                if (!safety.isSafe()) {
                    LOGGER.atWarning().log(
                        "Moderation review install refused for %s: %s",
                        entry.getSubmissionId(),
                        safety.detail()
                    );
                    return CommunityDownloadService.InstallResult.UNSAFE_PREFAB;
                }
                Files.write(destPrefab, prefab);
            }

            Path buildingFile = CommunityPaths.buildingFile(dataDir, proposedId);
            String buildingJson = CommunityHttpClient.getString(entry.moderationBuildingUrl(base), headers);
            if (buildingJson == null || buildingJson.isBlank()) {
                return CommunityDownloadService.InstallResult.DOWNLOAD_FAILED;
            }
            Files.writeString(buildingFile, buildingJson);
            CommunityBuildingJsonNormalizer.normalizeInstalledBuildingFile(buildingFile, proposedId);

            Path iconFile = CommunityPaths.iconFile(dataDir, proposedId);
            if (!Files.isRegularFile(iconFile)) {
                Path modIcon = CustomBuildingsPaths.iconFile(dataDir, entry.iconConstructionId());
                if (Files.isRegularFile(modIcon)) {
                    Files.copy(modIcon, iconFile, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    byte[] png = CommunityHttpClient.getBytes(entry.moderationIconUrl(base), headers);
                    if (png != null && png.length > 0) {
                        Files.write(iconFile, png);
                    }
                }
            }
            if (Files.isRegularFile(iconFile)) {
                CommonAsset asset = CommunityIconRegistry.registerIconFileNoSend(plugin, iconFile, true);
                CustomBuildingIconAssetRegistry.registerIconFileNoSend(plugin, iconFile, true);
                if (asset != null) {
                    CommunityIconRegistry.broadcastAssets(List.of(asset));
                    PlotTokenIconSync.afterIconRegistered(plugin, proposedId);
                }
            }

            LOGGER.atInfo().log(
                "Installed moderation review build %s (submission %s)",
                proposedId,
                entry.getSubmissionId()
            );
            return CommunityDownloadService.InstallResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to install moderation review build for %s",
                entry.getSubmissionId()
            );
            return CommunityDownloadService.InstallResult.IO_ERROR;
        }
    }

    public boolean approve(@Nonnull UUID playerUuid, @Nonnull String submissionId) {
        CommunityPendingEntry entry = findEntry(submissionId);
        if (entry == null) {
            return false;
        }
        if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
            LOGGER.atWarning().log("Moderation approval refused for %s: required mods are missing", submissionId);
            return false;
        }
        Path preview = CommunityPaths.moderationPreviewPrefabFile(plugin.getDataDirectory(), entry.getSubmissionId());
        try {
            if (!Files.isRegularFile(preview) || !CommunityPrefabSafety.validate(Files.readAllBytes(preview)).isSafe()) {
                LOGGER.atWarning().log("Moderation approval refused for %s: no validated preview", submissionId);
                return false;
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Moderation approval validation failed for %s", submissionId);
            return false;
        }
        String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
        JsonObject approval = new JsonObject();
        approval.addProperty("id", entry.getProposedId());
        approval.add("requiredMods", GSON.toJsonTree(entry.getRequiredMods()));
        String body = GSON.toJson(approval);
        String response =
            CommunityHttpClient.postJson(
                base + "/api/v1/moderation/approve/" + submissionId,
                moderatorHeaders(playerUuid),
                body
            );
        if (response == null) {
            return false;
        }
        refreshPending(playerUuid);
        plugin.getCommunityCatalogService().fetchManifest();
        return true;
    }

    public boolean reject(@Nonnull UUID playerUuid, @Nonnull String submissionId) {
        if (findEntry(submissionId) == null) {
            return false;
        }
        String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
        String response =
            CommunityHttpClient.postJson(
                base + "/api/v1/moderation/reject/" + submissionId,
                moderatorHeaders(playerUuid),
                "{}"
            );
        if (response == null) {
            return false;
        }
        refreshPending(playerUuid);
        return true;
    }

    @Nonnull
    public static String iconConstructionId(@Nonnull String submissionId) {
        return ICON_ID_PREFIX + submissionId.trim();
    }

    @Nonnull
    public static String iconAssetPath(@Nonnull String submissionId) {
        return CustomBuildingsPaths.iconAssetPath(iconConstructionId(submissionId));
    }

    /**
     * Downloads and registers list icons for the given submission ids (one UI page).
     *
     * @return true when at least one icon was newly written or registered
     */
    public boolean ensureIconsForSubmissionIds(@Nonnull Collection<String> submissionIds, @Nonnull UUID moderatorUuid) {
        if (submissionIds.isEmpty()) {
            return false;
        }
        List<CommunityPendingEntry> needed = new ArrayList<>();
        for (String id : submissionIds) {
            CommunityPendingEntry entry = findEntry(id);
            if (entry != null) {
                needed.add(entry);
            }
        }
        if (needed.isEmpty()) {
            return false;
        }

        List<CommunityPendingEntry> toDownload = new ArrayList<>();
        List<Path> alreadyOnDisk = new ArrayList<>();
        for (CommunityPendingEntry entry : needed) {
            Path iconFile = CustomBuildingsPaths.iconFile(plugin.getDataDirectory(), entry.iconConstructionId());
            if (Files.isRegularFile(iconFile) && !isCachedIconStale(iconFile)) {
                alreadyOnDisk.add(iconFile);
            } else {
                toDownload.add(entry);
            }
        }

        List<Path> downloaded = Collections.synchronizedList(new ArrayList<>());
        if (!toDownload.isEmpty()) {
            Map<String, String> headers = moderatorHeaders(moderatorUuid);
            String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
            List<CompletableFuture<Void>> futures = new ArrayList<>(toDownload.size());
            for (CommunityPendingEntry entry : toDownload) {
                futures.add(
                    CompletableFuture.runAsync(
                        () -> {
                            Path written = downloadListIconToDisk(entry, base, headers);
                            if (written != null) {
                                downloaded.add(written);
                            }
                        },
                        iconExecutor
                    )
                );
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        List<CommonAsset> toBroadcast = new ArrayList<>();
        for (Path iconFile : alreadyOnDisk) {
            CommonAsset asset = CommunityIconRegistry.registerIconFileNoSend(plugin, iconFile, false);
            if (asset != null) {
                toBroadcast.add(asset);
            }
        }
        for (Path iconFile : downloaded) {
            CommonAsset asset = CommunityIconRegistry.registerIconFileNoSend(plugin, iconFile, true);
            if (asset != null) {
                toBroadcast.add(asset);
            }
        }
        CommunityIconRegistry.broadcastAssets(toBroadcast);
        return !downloaded.isEmpty() || !toBroadcast.isEmpty();
    }

    public int ensureIconsForSubmissionIdsAsync(
        @Nonnull Collection<String> submissionIds,
        @Nonnull UUID moderatorUuid,
        @Nonnull Runnable onIconsChanged
    ) {
        int serial = iconFetchSerial.incrementAndGet();
        List<String> ids = List.copyOf(submissionIds);
        CompletableFuture.runAsync(
            () -> {
                boolean changed = false;
                try {
                    changed = ensureIconsForSubmissionIds(ids, moderatorUuid);
                } finally {
                    if (changed && serial == iconFetchSerial.get()) {
                        onIconsChanged.run();
                    }
                }
            },
            iconExecutor
        );
        return serial;
    }

    @Nullable
    private Path downloadListIconToDisk(
        @Nonnull CommunityPendingEntry entry,
        @Nonnull String apiBaseUrl,
        @Nonnull Map<String, String> headers
    ) {
        Path iconFile = CustomBuildingsPaths.iconFile(plugin.getDataDirectory(), entry.iconConstructionId());
        byte[] png = CommunityHttpClient.getBytes(entry.moderationIconUrl(apiBaseUrl), headers);
        if (png == null || png.length == 0) {
            return null;
        }
        try {
            Files.createDirectories(iconFile.getParent());
            Files.write(iconFile, png);
            return iconFile;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to cache moderation icon for %s", entry.getSubmissionId());
            return null;
        }
    }

    private boolean isCachedIconStale(@Nonnull Path iconFile) {
        long fetchMs = lastFetchEpochMs;
        if (fetchMs <= 0L) {
            return false;
        }
        try {
            return Files.getLastModifiedTime(iconFile).toMillis() < fetchMs;
        } catch (Exception e) {
            return true;
        }
    }

    @Nonnull
    private static Map<String, String> moderatorHeaders(@Nonnull UUID playerUuid) {
        return Map.of("X-Player-Uuid", playerUuid.toString().trim().toLowerCase(Locale.ROOT));
    }

    private static final class PendingResponse {
        @SerializedName("submissions")
        @Nullable
        private List<CommunityPendingEntry> submissions;
    }
}
