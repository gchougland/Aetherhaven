package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Fetches and caches the remote community manifest (metadata + icon thumbnails only). */
public final class CommunityCatalogService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().create();

    private final AetherhavenPlugin plugin;
    private final AtomicReference<List<CommunityManifestEntry>> cachedEntries = new AtomicReference<>(List.of());
    private volatile long lastFetchEpochMs;

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

    /** Refreshes manifest if stale; downloads missing icon thumbnails. */
    public void refreshIfStale() {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long refreshMs = plugin.getConfig().get().getCommunityMarketplace().getManifestRefreshMinutes() * 60_000L;
        if (now - lastFetchEpochMs < refreshMs && !cachedEntries.get().isEmpty()) {
            return;
        }
        fetchManifest();
    }

    /** Fetches manifest and icons from the API (plot crafting refresh button). */
    public boolean refreshFromApi() {
        if (!fetchManifest()) {
            return false;
        }
        ensureIconsReady(true);
        return true;
    }

    public boolean fetchManifest() {
        if (!isEnabled()) {
            cachedEntries.set(List.of());
            return false;
        }
        String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
        String json = CommunityHttpClient.getString(base + "/api/v1/manifest");
        if (json == null || json.isBlank()) {
            LOGGER.atWarning().log("Community manifest fetch failed from %s", base);
            return false;
        }
        try {
            ManifestResponse response = GSON.fromJson(json, ManifestResponse.class);
            List<CommunityManifestEntry> entries = response != null && response.entries != null ? response.entries : List.of();
            cachedEntries.set(List.copyOf(entries));
            lastFetchEpochMs = System.currentTimeMillis();
            LOGGER.atInfo().log("Community manifest loaded: %s entries", entries.size());
            for (CommunityManifestEntry entry : entries) {
                ensureIconCached(entry, false);
            }
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to parse community manifest");
            return false;
        }
    }

    /**
     * Downloads and registers any missing community icon PNGs before the crafting list renders.
     *
     * @return true when at least one icon was newly written and registered this call
     */
    public boolean ensureIconsReady() {
        return ensureIconsReady(false);
    }

    /**
     * Downloads and registers any missing community icon PNGs before the crafting list renders.
     *
     * @param forceRegister when true, re-sends icons to connected clients even if already cached locally
     * @return true when at least one icon was newly written and registered this call
     */
    public boolean ensureIconsReady(boolean forceRegister) {
        boolean anyNew = false;
        for (CommunityManifestEntry entry : cachedEntries.get()) {
            if (ensureIconCached(entry, forceRegister)) {
                anyNew = true;
            }
        }
        return anyNew;
    }

    private boolean ensureIconCached(@Nonnull CommunityManifestEntry entry) {
        return ensureIconCached(entry, false);
    }

    private boolean ensureIconCached(@Nonnull CommunityManifestEntry entry, boolean forceRegister) {
        Path iconFile = CommunityPaths.iconFile(plugin.getDataDirectory(), entry.getId());
        if (Files.isRegularFile(iconFile)) {
            registerCachedIcon(iconFile, forceRegister);
            return false;
        }
        String iconUrl = entry.getIconUrl();
        if (iconUrl == null || iconUrl.isBlank()) {
            LOGGER.atWarning().log("Community entry %s has no icon URL in manifest", entry.getId());
            return false;
        }
        String absolute = resolveUrl(iconUrl);
        byte[] png = CommunityHttpClient.getBytes(absolute);
        if (png == null || png.length == 0) {
            LOGGER.atWarning().log("Community icon download failed for %s from %s", entry.getId(), absolute);
            return false;
        }
        try {
            Files.createDirectories(iconFile.getParent());
            Files.write(iconFile, png);
            registerCachedIcon(iconFile, true);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to cache community icon for %s", entry.getId());
            return false;
        }
    }

    private void registerCachedIcon(@Nonnull Path iconFile, boolean forceRegister) {
        CommunityIconRegistry.registerIconFile(plugin, iconFile, forceRegister);
        CustomBuildingIconAssetRegistry.registerIconFile(plugin, iconFile, forceRegister);
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
        ObjectArrayList<PlotCraftingCatalog.GroupEntry> groups = new ObjectArrayList<>();
        for (CommunityManifestEntry entry : cachedEntries.get()) {
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
        groups.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return groups;
    }

    private static final class ManifestResponse {
        @SerializedName("entries")
        @Nullable
        private List<CommunityManifestEntry> entries;
    }
}
