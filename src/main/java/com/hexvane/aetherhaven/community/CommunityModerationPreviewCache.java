package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Temp prefab staging for in-game moderation previews; cleaned up when the crafting GUI closes. */
public final class CommunityModerationPreviewCache {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Set<String> sessionSubmissionIds = new HashSet<>();

    private CommunityModerationPreviewCache() {}

    @Nonnull
    public static CommunityModerationPreviewCache get() {
        return Holder.INSTANCE;
    }

    public void trackSessionSubmission(@Nonnull String submissionId) {
        sessionSubmissionIds.add(submissionId.trim().toLowerCase());
    }

    /**
     * Downloads a pending submission prefab to {@code Community/.moderation-preview/} for 3D preview.
     *
     * @return prefab path key on success
     */
    @Nullable
    public String loadPreview(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CommunityPendingEntry entry,
        @Nonnull String playerUuid
    ) {
        String submissionId = entry.getSubmissionId();
        Path dataDir = plugin.getDataDirectory();
        Path previewFile = CommunityPaths.moderationPreviewPrefabFile(dataDir, submissionId);
        if (Files.isRegularFile(previewFile)) {
            trackSessionSubmission(submissionId);
            return entry.prefabPathKey();
        }
        byte[] prefab =
            CommunityHttpClient.getBytes(entry.moderationPrefabUrl(plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl()), moderatorHeaders(playerUuid));
        if (prefab == null || prefab.length == 0) {
            return null;
        }
        try {
            Files.createDirectories(previewFile.getParent());
            Files.write(previewFile, prefab);
            trackSessionSubmission(submissionId);
            return entry.prefabPathKey();
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to write moderation preview prefab for %s", submissionId);
            return null;
        }
    }

    public void clearSession(@Nonnull AetherhavenPlugin plugin) {
        Path previewDir = CommunityPaths.moderationPreviewDirectory(plugin.getDataDirectory());
        if (!Files.isDirectory(previewDir)) {
            sessionSubmissionIds.clear();
            return;
        }
        try (Stream<Path> files = Files.list(previewDir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    LOGGER.atWarning().withCause(e).log("Failed to delete moderation preview file %s", p);
                }
            });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to sweep moderation preview directory");
        }
        sessionSubmissionIds.clear();
    }

    @Nonnull
    private static Map<String, String> moderatorHeaders(@Nonnull String playerUuid) {
        return Map.of("X-Player-Uuid", playerUuid.trim().toLowerCase());
    }

    private static final class Holder {
        private static final CommunityModerationPreviewCache INSTANCE = new CommunityModerationPreviewCache();
    }
}
