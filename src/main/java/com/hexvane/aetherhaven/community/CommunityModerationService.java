package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Fetches the moderation queue and drives approve / deny from the plot crafting bench. */
public final class CommunityModerationService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String ICON_ID_PREFIX = "mod_";

    private final AetherhavenPlugin plugin;
    private final ConcurrentHashMap<UUID, Boolean> apiModeratorAccess = new ConcurrentHashMap<>();
    private volatile List<CommunityPendingEntry> cachedPending = List.of();

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

    public boolean isModerator(@Nonnull UUID playerUuid) {
        if (isConfigModerator(playerUuid)) {
            return true;
        }
        return Boolean.TRUE.equals(apiModeratorAccess.get(playerUuid));
    }

    public boolean isConfigModerator(@Nonnull UUID playerUuid) {
        return plugin.getConfig().get().getCommunityMarketplace().isModerator(playerUuid);
    }

    /** Refreshes the pending queue from the marketplace API (moderator auth). */
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
            for (CommunityPendingEntry entry : entries) {
                ensureIconCached(entry, playerUuid);
            }
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

    public boolean approve(@Nonnull UUID playerUuid, @Nonnull String submissionId) {
        CommunityPendingEntry entry = findEntry(submissionId);
        if (entry == null) {
            return false;
        }
        String base = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
        String body = GSON.toJson(Map.of("id", entry.getProposedId()));
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

    private void ensureIconCached(@Nonnull CommunityPendingEntry entry, @Nonnull UUID playerUuid) {
        Path iconFile = CustomBuildingsPaths.iconFile(plugin.getDataDirectory(), entry.iconConstructionId());
        if (Files.isRegularFile(iconFile)) {
            CommunityIconRegistry.registerIconFile(plugin, iconFile);
            return;
        }
        byte[] png =
            CommunityHttpClient.getBytes(
                entry.moderationIconUrl(plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl()),
                moderatorHeaders(playerUuid)
            );
        if (png == null || png.length == 0) {
            return;
        }
        try {
            Files.createDirectories(iconFile.getParent());
            Files.write(iconFile, png);
            CommunityIconRegistry.registerIconFile(plugin, iconFile);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to cache moderation icon for %s", entry.getSubmissionId());
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
