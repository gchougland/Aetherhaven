package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.plot.PlotBuildingStyles;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Fetches and caches the remote community manifest (metadata + icon thumbnails only). */
public final class CommunityCatalogService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().create();
    private static final int ICON_DOWNLOAD_PARALLELISM = 4;
    private static final long FETCH_FAILURE_BACKOFF_MS = 90_000L;

    private final AetherhavenPlugin plugin;
    private final AtomicReference<List<CommunityManifestEntry>> cachedEntries = new AtomicReference<>(List.of());
    private volatile long lastFetchEpochMs;
    private volatile long lastFetchAttemptMs;
    private volatile boolean lastFetchIncludedPlayerContext;
    private final AtomicInteger iconFetchSerial = new AtomicInteger();
    private final ExecutorService iconExecutor =
        Executors.newFixedThreadPool(
            ICON_DOWNLOAD_PARALLELISM,
            r -> {
                Thread t = new Thread(r, "aetherhaven-community-icons");
                t.setDaemon(true);
                return t;
            }
        );

    public CommunityCatalogService(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        return cfg.isEnabled() && !cfg.getApiBaseUrl().isBlank();
    }

    @Nonnull
    public List<CommunityManifestEntry> getEntries() {
        return cachedEntries.get();
    }

    public boolean isCacheEmpty() {
        return cachedEntries.get().isEmpty();
    }

    public boolean isCacheStale() {
        if (!isEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (cachedEntries.get().isEmpty()) {
            return now - lastFetchAttemptMs >= FETCH_FAILURE_BACKOFF_MS;
        }
        long refreshMs = plugin.getConfig().get().getCommunityMarketplace().getManifestRefreshMinutes() * 60_000L;
        return now - lastFetchEpochMs >= refreshMs;
    }

    public boolean lastFetchIncludedPlayerContext() {
        return lastFetchIncludedPlayerContext;
    }

    @Nonnull
    public List<String> favoritedIdsFromCachedManifest() {
        ObjectArrayList<String> ids = new ObjectArrayList<>();
        for (CommunityManifestEntry entry : cachedEntries.get()) {
            if (entry.isUserHasFavorited()) {
                ids.add(entry.getId().trim().toLowerCase(Locale.ROOT));
            }
        }
        return ids;
    }

    @Nullable
    public CommunityManifestEntry findEntry(@Nonnull String constructionId) {
        String id = constructionId.trim().toLowerCase(Locale.ROOT);
        for (CommunityManifestEntry e : cachedEntries.get()) {
            if (e.getId().equalsIgnoreCase(id)) {
                return e;
            }
        }
        return null;
    }

    public boolean isInstalled(@Nonnull String constructionId) {
        return CommunityPaths.isInstalled(plugin.getDataDirectory(), constructionId);
    }

    /** Refreshes manifest if stale; does not download icons (call {@link #ensureIconsForIds} for the visible page). */
    public void refreshIfStale() {
        if (!isCacheStale()) {
            return;
        }
        fetchManifest();
    }

    /** Fetches manifest metadata from the API (plot crafting refresh). Icons are loaded per visible page. */
    public boolean refreshFromApi() {
        return fetchManifest(null);
    }

    public boolean refreshFromApi(@Nullable UUID playerUuid) {
        return fetchManifest(playerUuid);
    }

    public boolean fetchManifest() {
        return fetchManifest(null);
    }

    public boolean fetchManifest(@Nullable UUID playerUuid) {
        lastFetchAttemptMs = System.currentTimeMillis();
        if (!isEnabled()) {
            cachedEntries.set(List.of());
            lastFetchIncludedPlayerContext = false;
            return false;
        }
        String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
        String json =
            playerUuid != null
                ? CommunityHttpClient.getString(base + "/api/v1/manifest", playerHeaders(playerUuid))
                : CommunityHttpClient.getString(base + "/api/v1/manifest");
        if (json == null || json.isBlank()) {
            LOGGER.atWarning().log("Community manifest fetch failed from %s", base);
            return false;
        }
        try {
            ManifestResponse response = GSON.fromJson(json, ManifestResponse.class);
            List<CommunityManifestEntry> entries = response != null && response.entries != null ? response.entries : List.of();
            cachedEntries.set(List.copyOf(entries));
            lastFetchEpochMs = System.currentTimeMillis();
            lastFetchIncludedPlayerContext = playerUuid != null;
            LOGGER.atInfo().log("Community manifest loaded: %s entries", entries.size());
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to parse community manifest");
            return false;
        }
    }

    @Nonnull
    private static Map<String, String> playerHeaders(@Nonnull UUID playerUuid) {
        return Map.of("X-Player-Uuid", playerUuid.toString().trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Downloads and registers missing icons for the given construction ids (typically one UI page).
     * Newly registered assets are broadcast in a single atlas rebuild.
     *
     * @return true when at least one icon was newly written or registered
     */
    public boolean ensureIconsForIds(@Nonnull Collection<String> constructionIds) {
        if (constructionIds.isEmpty()) {
            return false;
        }
        List<CommunityManifestEntry> needed = new ArrayList<>();
        for (String id : constructionIds) {
            CommunityManifestEntry entry = findEntry(id);
            if (entry != null) {
                needed.add(entry);
            }
        }
        if (needed.isEmpty()) {
            return false;
        }

        List<CommunityManifestEntry> toDownload = new ArrayList<>();
        List<Path> alreadyOnDisk = new ArrayList<>();
        for (CommunityManifestEntry entry : needed) {
            Path iconFile = CommunityPaths.iconFile(plugin.getDataDirectory(), entry.getId());
            if (Files.isRegularFile(iconFile) && !isCachedIconStale(iconFile)) {
                alreadyOnDisk.add(iconFile);
            } else {
                toDownload.add(entry);
            }
        }

        List<Path> downloaded = Collections.synchronizedList(new ArrayList<>());
        if (!toDownload.isEmpty()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>(toDownload.size());
            for (CommunityManifestEntry entry : toDownload) {
                futures.add(
                    CompletableFuture.runAsync(
                        () -> {
                            Path written = downloadIconToDisk(entry);
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
            CustomBuildingIconAssetRegistry.registerIconFileNoSend(plugin, iconFile, false);
            if (asset != null) {
                toBroadcast.add(asset);
                notifyIconRegistered(iconFile);
            }
        }
        for (Path iconFile : downloaded) {
            CommonAsset asset = CommunityIconRegistry.registerIconFileNoSend(plugin, iconFile, true);
            CustomBuildingIconAssetRegistry.registerIconFileNoSend(plugin, iconFile, true);
            if (asset != null) {
                toBroadcast.add(asset);
                notifyIconRegistered(iconFile);
            }
        }
        CommunityIconRegistry.broadcastAssets(toBroadcast);
        return !downloaded.isEmpty() || !toBroadcast.isEmpty();
    }

    /**
     * Starts an async icon ensure for the given ids. Invokes {@code onIconsChanged} on a worker thread only when
     * at least one icon was newly written or registered. Returns a serial used to ignore stale completions.
     */
    public int ensureIconsForIdsAsync(@Nonnull Collection<String> constructionIds, @Nonnull Runnable onIconsChanged) {
        int serial = iconFetchSerial.incrementAndGet();
        List<String> ids = List.copyOf(constructionIds);
        CompletableFuture.runAsync(
            () -> {
                boolean changed = false;
                try {
                    changed = ensureIconsForIds(ids);
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

    public int currentIconFetchSerial() {
        return iconFetchSerial.get();
    }

    @Nullable
    private Path downloadIconToDisk(@Nonnull CommunityManifestEntry entry) {
        Path iconFile = CommunityPaths.iconFile(plugin.getDataDirectory(), entry.getId());
        String iconUrl = entry.getIconUrl();
        if (iconUrl == null || iconUrl.isBlank()) {
            LOGGER.atWarning().log("Community entry %s has no icon URL in manifest", entry.getId());
            return null;
        }
        String absolute = resolveUrl(iconUrl);
        byte[] png = CommunityHttpClient.getBytes(absolute);
        if (png == null || png.length == 0) {
            LOGGER.atWarning().log("Community icon download failed for %s from %s", entry.getId(), absolute);
            return null;
        }
        try {
            Files.createDirectories(iconFile.getParent());
            Files.write(iconFile, png);
            return iconFile;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to cache community icon for %s", entry.getId());
            return null;
        }
    }

    /** True when the cached PNG is older than the last successful manifest fetch (e.g. was a cover screenshot). */
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

    private void notifyIconRegistered(@Nonnull Path iconFile) {
        String constructionId = CustomBuildingsPaths.constructionIdFromIconFileName(iconFile.getFileName().toString());
        if (constructionId != null) {
            PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
        }
    }

    @Nonnull
    public String resolveUrl(@Nonnull String relativeOrAbsolute) {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute;
        }
        String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
        if (relativeOrAbsolute.startsWith("/")) {
            return base + relativeOrAbsolute;
        }
        return base + "/" + relativeOrAbsolute;
    }

    @Nonnull
    public List<PlotCraftingCatalog.GroupEntry> buildGroupEntries() {
        return buildGroupEntries(Collections.emptySet(), CommunityCatalogSort.UPVOTES);
    }

    @Nonnull
    public List<PlotCraftingCatalog.GroupEntry> buildGroupEntries(@Nonnull Set<String> activeStyleFilters) {
        return buildGroupEntries(activeStyleFilters, CommunityCatalogSort.UPVOTES);
    }

    @Nonnull
    public List<PlotCraftingCatalog.GroupEntry> buildGroupEntries(
        @Nonnull Set<String> activeStyleFilters,
        @Nonnull CommunityCatalogSort sort
    ) {
        return buildGroupEntries(activeStyleFilters, sort, false);
    }

    @Nonnull
    public List<PlotCraftingCatalog.GroupEntry> buildGroupEntries(
        @Nonnull Set<String> activeStyleFilters,
        @Nonnull CommunityCatalogSort sort,
        boolean includeMissingMods
    ) {
        ObjectArrayList<PlotCraftingCatalog.GroupEntry> groups = new ObjectArrayList<>();
        ObjectArrayList<CommunityManifestEntry> entries = new ObjectArrayList<>(cachedEntries.get());
        entries.sort(comparatorFor(sort));
        for (CommunityManifestEntry entry : entries) {
            if (!PlotBuildingStyles.matchesFilter(entry.getStyleId(), activeStyleFilters)) {
                continue;
            }
            if (!includeMissingMods && !CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
                continue;
            }
            groups.add(
                new PlotCraftingCatalog.GroupEntry(
                    entry.getId(),
                    entry.getDisplayName(),
                    List.of(
                        new PlotCraftingCatalog.VariantEntry(entry.getId(), entry.getDisplayName(), entry.prefabPathKey())
                    )
                )
            );
        }
        return groups;
    }

    @Nonnull
    public List<PlotCraftingCatalog.GroupEntry> buildFavoritesGroupEntries(
        @Nonnull Set<String> favoriteIds,
        @Nonnull Set<String> activeStyleFilters,
        @Nonnull Set<String> catalogGroupKeys
    ) {
        if (favoriteIds.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new java.util.HashSet<>();
        for (String id : favoriteIds) {
            if (id != null && !id.isBlank()) {
                normalized.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        ObjectArrayList<PlotCraftingCatalog.GroupEntry> groups = new ObjectArrayList<>();
        for (CommunityManifestEntry entry : cachedEntries.get()) {
            String id = entry.getId().trim().toLowerCase(Locale.ROOT);
            if (!normalized.contains(id) || catalogGroupKeys.contains(id)) {
                continue;
            }
            if (!PlotBuildingStyles.matchesFilter(entry.getStyleId(), activeStyleFilters)) {
                continue;
            }
            groups.add(
                new PlotCraftingCatalog.GroupEntry(
                    entry.getId(),
                    entry.getDisplayName(),
                    List.of(
                        new PlotCraftingCatalog.VariantEntry(entry.getId(), entry.getDisplayName(), entry.prefabPathKey())
                    )
                )
            );
        }
        groups.sort(Comparator.comparing(g -> g.displayName().toLowerCase(Locale.ROOT)));
        return groups;
    }

    @Nonnull
    private static Comparator<CommunityManifestEntry> comparatorFor(@Nonnull CommunityCatalogSort sort) {
        Comparator<CommunityManifestEntry> byName =
            Comparator.comparing(e -> e.getDisplayName().toLowerCase(Locale.ROOT));
        return switch (sort) {
            case DOWNLOADS -> Comparator.comparingInt(CommunityManifestEntry::getDownloadCount).reversed().thenComparing(byName);
            case LATEST -> Comparator
                .comparing(
                    (CommunityManifestEntry e) -> e.getApprovedAt().isBlank() ? "" : e.getApprovedAt(),
                    Comparator.reverseOrder()
                )
                .thenComparing(byName);
            case NAME -> byName;
            case UPVOTES -> Comparator.comparingInt(CommunityManifestEntry::getUpvoteCount).reversed().thenComparing(byName);
        };
    }

    /** Distinct style ids present in the cached community manifest (for craft-bench filters). */
    @Nonnull
    public List<String> listStyleIds() {
        TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (CommunityManifestEntry entry : cachedEntries.get()) {
            String styleId = PlotBuildingStyles.normalize(entry.getStyleId());
            if (styleId != null) {
                ids.add(styleId);
            }
        }
        return new ArrayList<>(ids);
    }

    private static final class ManifestResponse {
        @SerializedName("entries")
        @Nullable
        private List<CommunityManifestEntry> entries;
    }
}
