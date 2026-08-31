package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.CustomFestivalPaths;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.prop.PropIconSync;
import com.hexvane.aetherhaven.prop.PropPaths;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Full install of a community building (building JSON + prefab + icon). */
public final class CommunityDownloadService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int BATCH_PARALLELISM = 4;

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

    /** Summary of a multi-building install (fresh installs only). */
    public record BatchResult(int ok, int failed, int skipped) {}

    @Nonnull
    public static InstallResult install(@Nonnull AetherhavenPlugin plugin, @Nonnull CommunityManifestEntry entry) {
        return install(plugin, entry, false, null);
    }

    @Nonnull
    public static InstallResult install(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityManifestEntry entry,
        boolean forceRefresh
    ) {
        return install(plugin, entry, forceRefresh, null);
    }

    @Nonnull
    public static InstallResult install(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityManifestEntry entry,
        boolean forceRefresh,
        @Nullable UUID playerUuid
    ) {
        FileInstallOutcome outcome = installFiles(plugin, entry, forceRefresh, true);
        if (outcome.result() != InstallResult.SUCCESS) {
            return outcome.result();
        }
        plugin.reloadConfigsAndAssetCatalogs();
        if (!forceRefresh) {
            reportInstall(plugin, entry.getId(), playerUuid);
        }
        LOGGER.atInfo().log(forceRefresh ? "Updated community building %s" : "Installed community building %s", entry.getId());
        return InstallResult.SUCCESS;
    }

    /**
     * Fresh-installs many buildings: parallel file fetches (bounded), one icon registry broadcast, one catalog
     * reload. Skips already-installed entries and buildings with missing required mods. Does not force-update.
     *
     * @param onProgress invoked on worker threads with completed attempt count (ok+failed+skipped so far)
     */
    @Nonnull
    public static BatchResult installBatch(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull List<CommunityManifestEntry> entries,
        @Nullable IntConsumer onProgress
    ) {
        return installBatch(plugin, entries, onProgress, null);
    }

    @Nonnull
    public static BatchResult installBatch(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull List<CommunityManifestEntry> entries,
        @Nullable IntConsumer onProgress,
        @Nullable UUID playerUuid
    ) {
        if (entries.isEmpty()) {
            return new BatchResult(0, 0, 0);
        }
        CommunityCatalogService catalog = plugin.getCommunityCatalogService();
        List<CommunityManifestEntry> toInstall = new ArrayList<>();
        int skipped = 0;
        for (CommunityManifestEntry entry : entries) {
            if (catalog.isInstalled(entry.getId())) {
                skipped++;
                continue;
            }
            if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
                skipped++;
                continue;
            }
            toInstall.add(entry);
        }
        if (onProgress != null) {
            onProgress.accept(skipped);
        }
        if (toInstall.isEmpty()) {
            return new BatchResult(0, 0, skipped);
        }

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger done = new AtomicInteger(skipped);
        List<SuccessfulInstall> successes = java.util.Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool =
            Executors.newFixedThreadPool(
                Math.min(BATCH_PARALLELISM, toInstall.size()),
                r -> {
                    Thread t = new Thread(r, "aetherhaven-community-batch-install");
                    t.setDaemon(true);
                    return t;
                }
            );
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(toInstall.size());
            for (CommunityManifestEntry entry : toInstall) {
                futures.add(
                    CompletableFuture.runAsync(
                        () -> {
                            FileInstallOutcome outcome = installFiles(plugin, entry, false, false);
                            if (outcome.result() == InstallResult.SUCCESS) {
                                ok.incrementAndGet();
                                successes.add(new SuccessfulInstall(entry.getId(), outcome.iconFile(), entry.isProp()));
                                catalog.markIconComplete(entry.getId());
                            } else {
                                failed.incrementAndGet();
                                LOGGER.atWarning().log(
                                    "Batch community install failed for %s: %s",
                                    entry.getId(),
                                    outcome.result()
                                );
                            }
                            int progress = done.incrementAndGet();
                            if (onProgress != null) {
                                onProgress.accept(progress);
                            }
                        },
                        pool
                    )
                );
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            pool.shutdown();
        }

        if (!successes.isEmpty()) {
            List<CommonAsset> toBroadcast = new ArrayList<>();
            for (SuccessfulInstall success : successes) {
                if (success.iconFile() == null) {
                    continue;
                }
                CommonAsset asset = CommunityIconRegistry.registerIconFileNoSend(plugin, success.iconFile(), true);
                CustomBuildingIconAssetRegistry.registerIconFileNoSend(plugin, success.iconFile(), true);
                if (asset != null) {
                    toBroadcast.add(asset);
                }
                if (success.prop()) {
                    PropIconSync.afterIconRegistered(plugin, success.constructionId());
                } else {
                    PlotTokenIconSync.afterIconRegistered(plugin, success.constructionId());
                }
            }
            CommunityIconRegistry.broadcastAssets(toBroadcast);
        }

        if (ok.get() > 0) {
            plugin.reloadConfigsAndAssetCatalogs();
            for (SuccessfulInstall success : successes) {
                // Catalog reload can race icon atlas; refresh virtual defs again for online players.
                if (success.prop()) {
                    PropIconSync.afterIconRegistered(plugin, success.constructionId());
                } else if (success.iconFile() != null) {
                    PlotTokenIconSync.afterIconRegistered(plugin, success.constructionId());
                }
                reportInstall(plugin, success.constructionId(), playerUuid);
            }
            LOGGER.atInfo().log("Batch installed %s community buildings (%s failed)", ok.get(), failed.get());
        }
        return new BatchResult(ok.get(), failed.get(), skipped);
    }

    private record SuccessfulInstall(@Nonnull String constructionId, @Nullable Path iconFile, boolean prop) {}

    private record FileInstallOutcome(@Nonnull InstallResult result, @Nullable Path iconFile) {}

    /**
     * Writes prefab/building/icon files for one entry. When {@code registerIconImmediately} is true, registers the
     * icon right away (single-install path). When false, returns the icon path for batched registration.
     */
    @Nonnull
    private static FileInstallOutcome installFiles(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityManifestEntry entry,
        boolean forceRefresh,
        boolean registerIconImmediately
    ) {
        if (entry.isProp()) {
            return installPropFiles(plugin, entry, forceRefresh, registerIconImmediately);
        }
        if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
            return new FileInstallOutcome(InstallResult.MISSING_MODS, null);
        }
        String id = entry.getId();
        Path dataDir = plugin.getDataDirectory();
        CommunityCatalogService catalog = plugin.getCommunityCatalogService();

        boolean wrotePrefabThisAttempt = false;
        boolean wroteBuildingThisAttempt = false;
        boolean wroteIconThisAttempt = false;
        boolean festivalLook = entry.isFestivalVariant();
        Path writtenIcon = null;

        try {
            Files.createDirectories(CommunityPaths.buildingsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.prefabsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.iconsDirectory(dataDir));
            if (festivalLook) {
                Files.createDirectories(CustomFestivalPaths.festivalsDirectory(dataDir));
                Files.createDirectories(CustomFestivalPaths.prefabsDirectory(dataDir));
            }

            Path installedPrefab =
                festivalLook
                    ? CustomFestivalPaths.prefabFile(dataDir, id)
                    : CommunityPaths.installedPrefabFile(dataDir, id);
            boolean havePrefab = Files.isRegularFile(installedPrefab);
            if (forceRefresh) {
                CommunityPreviewCache.get().clearEntryPreview(plugin, id);
                havePrefab = false;
            } else if (!havePrefab && !festivalLook && CommunityPreviewCache.get().promotePreviewPrefab(dataDir, id)) {
                havePrefab = true;
            } else if (!havePrefab && festivalLook) {
                Path preview = CommunityPaths.previewPrefabFile(dataDir, id);
                if (Files.isRegularFile(preview)) {
                    Files.copy(preview, installedPrefab, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(preview);
                    havePrefab = true;
                }
            }
            if (!havePrefab) {
                String prefabUrl = entry.getPrefabUrl();
                if (prefabUrl == null || prefabUrl.isBlank()) {
                    return new FileInstallOutcome(InstallResult.NOT_FOUND, null);
                }
                byte[] prefab = CommunityHttpClient.getBytes(catalog.resolveUrl(prefabUrl));
                if (prefab == null || prefab.length == 0) {
                    return new FileInstallOutcome(InstallResult.DOWNLOAD_FAILED, null);
                }
                CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(prefab);
                if (!safety.isSafe()) {
                    LOGGER.atWarning().log("Refused unsafe community download %s: %s", id, safety.detail());
                    return new FileInstallOutcome(InstallResult.UNSAFE_PREFAB, null);
                }
                Files.write(installedPrefab, prefab);
                wrotePrefabThisAttempt = true;
            }
            CommunityPrefabSafety.Result installedSafety = CommunityPrefabSafety.validate(installedPrefab);
            if (!installedSafety.isSafe()) {
                if (wrotePrefabThisAttempt) {
                    Files.deleteIfExists(installedPrefab);
                }
                LOGGER.atWarning().log("Refused unsafe installed community prefab %s: %s", id, installedSafety.detail());
                return new FileInstallOutcome(InstallResult.UNSAFE_PREFAB, null);
            }

            Path buildingFile =
                festivalLook
                    ? CustomFestivalPaths.festivalFile(dataDir, id)
                    : CommunityPaths.buildingFile(dataDir, id);
            if (forceRefresh || !Files.isRegularFile(buildingFile)) {
                String buildingUrl = entry.getBuildingUrl();
                if (buildingUrl == null || buildingUrl.isBlank()) {
                    rollbackFreshInstall(dataDir, id, festivalLook, wrotePrefabThisAttempt, false, false);
                    return new FileInstallOutcome(InstallResult.NOT_FOUND, null);
                }
                String buildingJson = CommunityHttpClient.getString(catalog.resolveUrl(buildingUrl));
                if (buildingJson == null || buildingJson.isBlank()) {
                    rollbackFreshInstall(dataDir, id, festivalLook, wrotePrefabThisAttempt, false, false);
                    return new FileInstallOutcome(InstallResult.DOWNLOAD_FAILED, null);
                }
                Files.writeString(buildingFile, buildingJson);
                wroteBuildingThisAttempt = true;
            }
            if (festivalLook) {
                rewriteFestivalLookJson(buildingFile, id);
            } else {
                CommunityBuildingJsonNormalizer.normalizeInstalledBuildingFile(buildingFile, id);
            }

            boolean iconRequired = CommunityIconDownload.iconRequired(entry);
            if (iconRequired) {
                Path iconFile = CommunityPaths.iconFile(dataDir, id);
                boolean hadIconBefore = Files.isRegularFile(iconFile);
                boolean needIcon = forceRefresh || !hadIconBefore;
                if (registerIconImmediately) {
                    CommunityIconDownload.Result iconResult =
                        CommunityIconDownload.downloadRegisterAndValidate(plugin, entry, needIcon);
                    wroteIconThisAttempt =
                        iconResult == CommunityIconDownload.Result.SUCCESS
                            && Files.isRegularFile(iconFile)
                            && needIcon;
                    if (iconResult != CommunityIconDownload.Result.SUCCESS
                        && iconResult != CommunityIconDownload.Result.NOT_REQUIRED) {
                        LOGGER.atWarning().log("Community icon install failed for %s: %s", id, iconResult);
                        if (!forceRefresh) {
                            rollbackFreshInstall(
                                dataDir,
                                id,
                                festivalLook,
                                wrotePrefabThisAttempt,
                                wroteBuildingThisAttempt,
                                wroteIconThisAttempt
                            );
                        }
                        return new FileInstallOutcome(InstallResult.ICON_FAILED, null);
                    }
                } else {
                    Path diskIcon = CommunityIconDownload.downloadToDiskOnly(plugin, entry, needIcon);
                    if (diskIcon == null) {
                        LOGGER.atWarning().log("Community icon install failed for %s (disk write)", id);
                        if (!forceRefresh) {
                            rollbackFreshInstall(
                                dataDir,
                                id,
                                festivalLook,
                                wrotePrefabThisAttempt,
                                wroteBuildingThisAttempt,
                                false
                            );
                        }
                        return new FileInstallOutcome(InstallResult.ICON_FAILED, null);
                    }
                    wroteIconThisAttempt = needIcon;
                    writtenIcon = diskIcon;
                }
            }

            CommunityInstallVersion.writeInstalledVersion(dataDir, id, entry.getVersion());
            if (registerIconImmediately) {
                plugin.getCommunityCatalogService().markIconComplete(id);
            }
            return new FileInstallOutcome(InstallResult.SUCCESS, writtenIcon);
        } catch (IOException e) {
            rollbackFreshInstall(
                dataDir,
                id,
                festivalLook,
                wrotePrefabThisAttempt,
                wroteBuildingThisAttempt,
                wroteIconThisAttempt
            );
            LOGGER.atWarning().withCause(e).log("Failed to install community building %s", id);
            return new FileInstallOutcome(InstallResult.IO_ERROR, null);
        }
    }

    @Nonnull
    private static FileInstallOutcome installPropFiles(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityManifestEntry entry,
        boolean forceRefresh,
        boolean registerIconImmediately
    ) {
        if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
            return new FileInstallOutcome(InstallResult.MISSING_MODS, null);
        }
        String id = entry.getId();
        Path dataDir = plugin.getDataDirectory();
        CommunityCatalogService catalog = plugin.getCommunityCatalogService();
        boolean wrotePrefab = false;
        boolean wroteDef = false;
        boolean wroteIcon = false;
        Path writtenIcon = null;
        try {
            Files.createDirectories(PropPaths.propsDirectory(dataDir));
            Files.createDirectories(PropPaths.propPrefabsDirectory(dataDir));
            Files.createDirectories(CommunityPaths.iconsDirectory(dataDir));

            String prefabKey = PropPaths.prefabPathKeyFromPropId(id);
            String prefabFileName = PropPaths.prefabFileNameFromKey(prefabKey);
            Path installedPrefab = PropPaths.propPrefabFile(dataDir, prefabFileName);
            if (forceRefresh || !Files.isRegularFile(installedPrefab)) {
                String prefabUrl = entry.getPrefabUrl();
                if (prefabUrl == null || prefabUrl.isBlank()) {
                    return new FileInstallOutcome(InstallResult.NOT_FOUND, null);
                }
                byte[] prefab = CommunityHttpClient.getBytes(catalog.resolveUrl(prefabUrl));
                if (prefab == null || prefab.length == 0) {
                    return new FileInstallOutcome(InstallResult.DOWNLOAD_FAILED, null);
                }
                CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(prefab);
                if (!safety.isSafe()) {
                    LOGGER.atWarning().log("Refused unsafe community prop prefab %s: %s", id, safety.detail());
                    return new FileInstallOutcome(InstallResult.UNSAFE_PREFAB, null);
                }
                Files.write(installedPrefab, prefab);
                wrotePrefab = true;
            }

            Path propFile = PropPaths.propFileUnderDataDir(dataDir, id);
            if (forceRefresh || !Files.isRegularFile(propFile)) {
                String propUrl = entry.getPropUrl();
                if (propUrl == null || propUrl.isBlank()) {
                    propUrl = entry.getBuildingUrl();
                }
                if (propUrl == null || propUrl.isBlank()) {
                    if (wrotePrefab) {
                        Files.deleteIfExists(installedPrefab);
                    }
                    return new FileInstallOutcome(InstallResult.NOT_FOUND, null);
                }
                String propJson = CommunityHttpClient.getString(catalog.resolveUrl(propUrl));
                if (propJson == null || propJson.isBlank()) {
                    if (wrotePrefab) {
                        Files.deleteIfExists(installedPrefab);
                    }
                    return new FileInstallOutcome(InstallResult.DOWNLOAD_FAILED, null);
                }
                JsonObject root = GSON.fromJson(propJson, JsonObject.class);
                if (root == null) {
                    root = new JsonObject();
                }
                root.addProperty("id", id);
                root.addProperty("prefabPath", prefabKey);
                if (!root.has("displayName") || root.get("displayName").isJsonNull()) {
                    root.addProperty("displayName", entry.getDisplayName());
                }
                String iconAsset = PropPaths.iconAssetPath(id);
                root.addProperty("iconPath", iconAsset);
                Files.writeString(propFile, GSON.toJson(root));
                wroteDef = true;
            }

            if (CommunityIconDownload.iconRequired(entry)) {
                Path iconFile = CommunityPaths.iconsDirectory(dataDir).resolve(PropPaths.iconFileName(id));
                boolean needIcon = forceRefresh || !Files.isRegularFile(iconFile);
                if (registerIconImmediately) {
                    CommunityIconDownload.Result iconResult =
                        CommunityIconDownload.downloadRegisterAndValidate(plugin, entry, needIcon);
                    wroteIcon =
                        iconResult == CommunityIconDownload.Result.SUCCESS
                            && Files.isRegularFile(iconFile)
                            && needIcon;
                    if (iconResult != CommunityIconDownload.Result.SUCCESS
                        && iconResult != CommunityIconDownload.Result.NOT_REQUIRED) {
                        if (!forceRefresh) {
                            if (wroteDef) {
                                Files.deleteIfExists(propFile);
                            }
                            if (wrotePrefab) {
                                Files.deleteIfExists(installedPrefab);
                            }
                            return new FileInstallOutcome(InstallResult.ICON_FAILED, null);
                        }
                    }
                    writtenIcon = Files.isRegularFile(iconFile) ? iconFile : null;
                } else {
                    Path diskIcon = CommunityIconDownload.downloadToDiskOnly(plugin, entry, needIcon);
                    if (diskIcon == null && needIcon) {
                        if (!forceRefresh) {
                            if (wroteDef) {
                                Files.deleteIfExists(propFile);
                            }
                            if (wrotePrefab) {
                                Files.deleteIfExists(installedPrefab);
                            }
                            return new FileInstallOutcome(InstallResult.ICON_FAILED, null);
                        }
                    } else {
                        wroteIcon = needIcon && diskIcon != null;
                        writtenIcon = diskIcon;
                    }
                }
            }

            CommunityInstallVersion.writeInstalledVersion(dataDir, id, entry.getVersion());
            if (registerIconImmediately) {
                plugin.getCommunityCatalogService().markIconComplete(id);
            }
            LOGGER.atInfo().log("Installed community prop %s", id);
            return new FileInstallOutcome(InstallResult.SUCCESS, writtenIcon);
        } catch (IOException e) {
            try {
                if (wroteDef) {
                    Files.deleteIfExists(PropPaths.propFileUnderDataDir(dataDir, id));
                }
                if (wrotePrefab) {
                    Files.deleteIfExists(
                        PropPaths.propPrefabFile(dataDir, PropPaths.prefabFileNameFromKey(PropPaths.prefabPathKeyFromPropId(id)))
                    );
                }
                if (wroteIcon) {
                    Files.deleteIfExists(CommunityPaths.iconsDirectory(dataDir).resolve(PropPaths.iconFileName(id)));
                }
            } catch (IOException ignored) {
                // best-effort rollback
            }
            LOGGER.atWarning().withCause(e).log("Failed to install community prop %s", id);
            return new FileInstallOutcome(InstallResult.IO_ERROR, null);
        }
    }

    private static void rewriteFestivalLookJson(@Nonnull Path festivalFile, @Nonnull String festivalId) {
        try {
            JsonObject root = GSON.fromJson(Files.readString(festivalFile), JsonObject.class);
            if (root == null) {
                return;
            }
            boolean changed = false;
            if (!root.has("id") || root.get("id").isJsonNull() || !festivalId.equals(root.get("id").getAsString())) {
                root.addProperty("id", festivalId);
                changed = true;
            }
            String expectedPrefab = CustomFestivalPaths.prefabPathKey(festivalId);
            String currentPrefab =
                root.has("prefabPath") && !root.get("prefabPath").isJsonNull()
                    ? root.get("prefabPath").getAsString()
                    : null;
            if (currentPrefab == null || !expectedPrefab.equals(currentPrefab.trim())) {
                root.addProperty("prefabPath", expectedPrefab);
                changed = true;
            }
            if (!root.has("festivalVariant") || !root.get("festivalVariant").getAsBoolean()) {
                root.addProperty("festivalVariant", true);
                changed = true;
            }
            if (changed) {
                Files.writeString(festivalFile, GSON.toJson(root));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Failed to rewrite festival look JSON %s", festivalFile);
        }
    }

    private static void rollbackFreshInstall(
        @Nonnull Path dataDir,
        @Nonnull String constructionId,
        boolean festivalLook,
        boolean wrotePrefab,
        boolean wroteBuilding,
        boolean wroteIcon
    ) {
        try {
            if (wroteIcon) {
                Files.deleteIfExists(CommunityPaths.iconFile(dataDir, constructionId));
            }
            if (wroteBuilding) {
                Files.deleteIfExists(
                    festivalLook
                        ? CustomFestivalPaths.festivalFile(dataDir, constructionId)
                        : CommunityPaths.buildingFile(dataDir, constructionId)
                );
            }
            if (wrotePrefab) {
                Files.deleteIfExists(
                    festivalLook
                        ? CustomFestivalPaths.prefabFile(dataDir, constructionId)
                        : CommunityPaths.installedPrefabFile(dataDir, constructionId)
                );
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to roll back partial community install for %s", constructionId);
        }
    }

    /** Best-effort install counter for the marketplace website; never fails the local install. */
    private static void reportInstall(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String constructionId,
        @Nullable UUID playerUuid
    ) {
        try {
            String instanceId = CommunityInstallInstance.loadOrCreate(plugin.getDataDirectory());
            if (instanceId == null || instanceId.isBlank()) {
                return;
            }
            String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
            if (base == null || base.isBlank()) {
                return;
            }
            String url = base.replaceAll("/+$", "") + "/api/v1/buildings/" + constructionId.trim() + "/download";
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Install-Instance-Id", instanceId);
            if (playerUuid != null) {
                headers.put("X-Player-Uuid", playerUuid.toString().trim().toLowerCase(Locale.ROOT));
            }
            CommunityHttpClient.postJson(url, headers, "{}");
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
            Files.deleteIfExists(CustomFestivalPaths.festivalFile(dataDir, constructionId));
            Files.deleteIfExists(CustomFestivalPaths.prefabFile(dataDir, constructionId));
            CommunityInstallVersion.deleteInstalledVersion(dataDir, constructionId);
            CommunityPreviewCache.get().clearEntryPreview(plugin, constructionId);
            plugin.getCommunityCatalogService().markIconComplete(constructionId);
            clearTownLookSelections(plugin, constructionId);
            plugin.reloadConfigsAndAssetCatalogs();
            return InstallResult.SUCCESS;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove community building %s", constructionId);
            return InstallResult.IO_ERROR;
        }
    }

    private static void clearTownLookSelections(@Nonnull AetherhavenPlugin plugin, @Nonnull String lookId) {
        try {
            for (World world : Universe.get().getWorlds().values()) {
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                for (TownRecord town : tm.allTowns()) {
                    town.clearSelectedFestivalLookIfMatches(lookId);
                    tm.updateTown(town);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Failed to clear town festival look selections for %s", lookId);
        }
    }
}
