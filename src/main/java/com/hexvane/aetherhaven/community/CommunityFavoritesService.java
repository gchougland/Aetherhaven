package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.plot.ConstructionFavoritesService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Syncs community building favorites with the marketplace API. */
public final class CommunityFavoritesService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private CommunityFavoritesService() {}

    @Nonnull
    public static List<String> fetchFavorites(@Nonnull AetherhavenPlugin plugin, @Nonnull UUID playerUuid) {
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        if (!cfg.isEnabled() || cfg.getApiBaseUrl().isBlank()) {
            return List.of();
        }
        String json = CommunityHttpClient.getString(cfg.getApiBaseUrl() + "/api/me/favorites", playerHeaders(playerUuid));
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            FavoritesResponse response = GSON.fromJson(json, FavoritesResponse.class);
            if (response == null || response.buildingIds == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (String id : response.buildingIds) {
                if (id != null && !id.isBlank()) {
                    out.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
            return out;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to parse favorites response");
            return List.of();
        }
    }

    public static void syncToPlayerState(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid
    ) {
        List<String> remote = fetchFavorites(plugin, playerUuid);
        if (!remote.isEmpty()) {
            ConstructionFavoritesService.mergeCommunityFavorites(ref, store, remote);
        }
    }

    /**
     * Toggles a community favorite on the API.
     *
     * @return {@code null} on success, or an error key
     */
    @Nullable
    public static ToggleResult toggleRemote(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String buildingId
    ) {
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        if (!cfg.isEnabled() || cfg.getApiBaseUrl().isBlank()) {
            return null;
        }
        String id = buildingId.trim().toLowerCase(Locale.ROOT);
        String url = cfg.getApiBaseUrl() + "/api/v1/buildings/" + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8) + "/favorite";
        String json = CommunityHttpClient.postJson(url, playerHeaders(playerUuid), "{}");
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            ToggleResponse response = GSON.fromJson(json, ToggleResponse.class);
            if (response == null) {
                return null;
            }
            return new ToggleResult(response.userHasFavorited, response.buildingIds != null ? response.buildingIds : List.of());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to parse favorite toggle response");
            return null;
        }
    }

    @Nonnull
    private static Map<String, String> playerHeaders(@Nonnull UUID playerUuid) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Player-Uuid", playerUuid.toString().trim().toLowerCase(Locale.ROOT));
        return headers;
    }

    public record ToggleResult(boolean favorited, @Nonnull List<String> buildingIds) {}

    private static final class FavoritesResponse {
        @SerializedName("buildingIds")
        private List<String> buildingIds;
    }

    private static final class ToggleResponse {
        @SerializedName("userHasFavorited")
        private boolean userHasFavorited;

        @SerializedName("buildingIds")
        private List<String> buildingIds;
    }
}
