package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Temp prefab staging for Load Preview; cleaned up when the crafting GUI closes. */
public final class CommunityPreviewCache {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Set<String> sessionPreviewIds = new HashSet<>();

    private CommunityPreviewCache() {}

    @Nonnull
    public static CommunityPreviewCache get() {
        return Holder.INSTANCE;
    }

    public void trackSessionPreview(@Nonnull String constructionId) {
        sessionPreviewIds.add(constructionId.trim().toLowerCase());
    }

    /**
     * Downloads prefab JSON to {@code Community/.preview/} for 3D preview.
     *
     * @return prefab path key on success
     */
    @Nullable
    public String loadPreview(@Nonnull AetherhavenPlugin plugin, @Nonnull CommunityManifestEntry entry) {
        String id = entry.getId();
        Path dataDir = plugin.getDataDirectory();
        Path previewFile = CommunityPaths.previewPrefabFile(dataDir, id);
        if (Files.isRegularFile(previewFile)) {
            if (!isSafe(previewFile, id)) {
                return null;
            }
            trackSessionPreview(id);
            return entry.prefabPathKey();
        }
        String prefabUrl = entry.getPrefabUrl();
        if (prefabUrl == null || prefabUrl.isBlank()) {
            return null;
        }
        CommunityCatalogService catalog = plugin.getCommunityCatalogService();
        byte[] prefab = CommunityHttpClient.getBytes(catalog.resolveUrl(prefabUrl));
        if (prefab == null || prefab.length == 0) {
            return null;
        }
        CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(prefab);
        if (!safety.isSafe()) {
            LOGGER.atWarning().log("Refused unsafe community preview %s: %s", id, safety.detail());
            return null;
        }
        try {
            Files.createDirectories(previewFile.getParent());
            Files.write(previewFile, prefab);
            trackSessionPreview(id);
            return entry.prefabPathKey();
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to write preview prefab for %s", id);
            return null;
        }
    }

    private boolean isSafe(@Nonnull Path prefabFile, @Nonnull String constructionId) {
        CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(prefabFile);
        if (safety.isSafe()) {
            return true;
        }
        LOGGER.atWarning().log("Refused unsafe cached community preview %s: %s", constructionId, safety.detail());
        try {
            Files.deleteIfExists(prefabFile);
        } catch (IOException ignored) {
            // Best effort; the file will never be handed to Hytale after this failure.
        }
        return false;
    }

    public void clearEntryPreview(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        Path preview = CommunityPaths.previewPrefabFile(plugin.getDataDirectory(), constructionId);
        try {
            Files.deleteIfExists(preview);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to delete preview prefab %s", constructionId);
        }
        sessionPreviewIds.remove(constructionId.trim().toLowerCase());
    }

    /** Clears all session preview temp files not promoted to install. */
    public void clearSession(@Nonnull AetherhavenPlugin plugin) {
        Path previewDir = CommunityPaths.previewDirectory(plugin.getDataDirectory());
        if (!Files.isDirectory(previewDir)) {
            sessionPreviewIds.clear();
            return;
        }
        try (Stream<Path> files = Files.list(previewDir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    LOGGER.atWarning().withCause(e).log("Failed to delete preview file %s", p);
                }
            });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to sweep preview directory");
        }
        sessionPreviewIds.clear();
    }

    /**
     * Moves an existing preview prefab into the installed prefab directory.
     *
     * @return true if a preview file was promoted
     */
    public boolean promotePreviewPrefab(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        Path preview = CommunityPaths.previewPrefabFile(dataDirectory, constructionId);
        Path installed = CommunityPaths.installedPrefabFile(dataDirectory, constructionId);
        if (!Files.isRegularFile(preview)) {
            return false;
        }
        try {
            Files.createDirectories(installed.getParent());
            Files.move(preview, installed, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to promote preview prefab for %s", constructionId);
            return false;
        }
    }

    private static final class Holder {
        private static final CommunityPreviewCache INSTANCE = new CommunityPreviewCache();
    }
}
