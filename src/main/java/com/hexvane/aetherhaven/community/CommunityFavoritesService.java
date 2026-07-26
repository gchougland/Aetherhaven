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
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Syncs community building favorites with the marketplace API. */
public final class CommunityFavoritesService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final long CACHE_TTL_MS = 5 * 60_000L;

    private static final ConcurrentHashMap<UUID, CachedFavorites> FAVORITES_CACHE = new ConcurrentHashMap<>();

    private CommunityFavoritesService() {}

    @Nonnull
    public static List<String> fetchFavorites(@Nonnull AetherhavenPlugin plugin, @Nonnull UUID playerUuid) {
        CachedFavorites cached = FAVORITES_CACHE.get(playerUuid);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMs < CACHE_TTL_MS) {
            return cached.buildingIds;
        }
        FetchResult remote = fetchFavoritesFromApi(plugin, playerUuid);
        if (remote == null) {
            return cached != null ? cached.buildingIds : List.of();
        }
        FAVORITES_CACHE.put(playerUuid, new CachedFavorites(List.copyOf(remote.buildingIds), now));
        return remote.buildingIds;
    }

    public static void invalidateCache(@Nonnull UUID playerUuid) {
        FAVORITES_CACHE.remove(playerUuid);
    }

    @Nullable
    private static FetchResult fetchFavoritesFromApi(@Nonnull AetherhavenPlugin plugin, @Nonnull UUID playerUuid) {
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        if (!cfg.isEnabled() || cfg.getApiBaseUrl().isBlank()) {
            return new FetchResult(List.of());
        }
        String json = CommunityHttpClient.getString(cfg.getApiBaseUrl() + "/api/me/favorites", playerHeaders(playerUuid));
        if (json == null) {
            return null;
        }
        if (json.isBlank()) {
            return new FetchResult(List.of());
        }
        try {
            FavoritesResponse response = GSON.fromJson(json, FavoritesResponse.class);
            if (response == null || response.buildingIds == null) {
                return new FetchResult(List.of());
            }
            List<String> out = new ArrayList<>();
            for (String id : response.buildingIds) {
                if (id != null && !id.isBlank()) {
                    out.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
            return new FetchResult(out);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to parse favorites response");
            return null;
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
     * @return {@code null} on failure
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
            List<String> ids = response.buildingIds != null ? response.buildingIds : List.of();
            FAVORITES_CACHE.put(
                playerUuid,
                new CachedFavorites(
                    ids.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList(),
                    System.currentTimeMillis()
                )
            );
            return new ToggleResult(response.userHasFavorited, ids);
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

    private record CachedFavorites(@Nonnull List<String> buildingIds, long fetchedAtMs) {}

    private record FetchResult(@Nonnull List<String> buildingIds) {}

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
