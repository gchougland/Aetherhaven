package com.hexvane.aetherhaven.plot;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per player favorited construction ids for the plot crafting bench. */
public final class PlayerConstructionFavoritesState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<PlayerConstructionFavoritesState> CODEC =
        BuilderCodec.builder(PlayerConstructionFavoritesState.class, PlayerConstructionFavoritesState::new)
            .append(
                new KeyedCodec<>("FavoriteConstructionIds", Codec.STRING_ARRAY),
                (c, v) -> c.favoriteConstructionIds = v != null ? new LinkedHashSet<>(Arrays.asList(v)) : new LinkedHashSet<>(),
                c -> c.favoriteConstructionIds.toArray(String[]::new))
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PlayerConstructionFavoritesState> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                PlayerConstructionFavoritesState.class,
                "AetherhavenPlayerConstructionFavoritesState",
                CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, PlayerConstructionFavoritesState> getComponentType() {
        ComponentType<EntityStore, PlayerConstructionFavoritesState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlayerConstructionFavoritesState not registered");
        }
        return t;
    }

    @Nonnull
    private LinkedHashSet<String> favoriteConstructionIds = new LinkedHashSet<>();

    public PlayerConstructionFavoritesState() {}

    public boolean isFavorite(@Nonnull String constructionId) {
        return favoriteConstructionIds.contains(normalize(constructionId));
    }

    /** @return true when added, false when removed */
    public boolean toggle(@Nonnull String constructionId) {
        String id = normalize(constructionId);
        if (favoriteConstructionIds.contains(id)) {
            favoriteConstructionIds.remove(id);
            return false;
        }
        favoriteConstructionIds.add(id);
        return true;
    }

    public void add(@Nonnull String constructionId) {
        favoriteConstructionIds.add(normalize(constructionId));
    }

    public void remove(@Nonnull String constructionId) {
        favoriteConstructionIds.remove(normalize(constructionId));
    }

    @Nonnull
    public Set<String> favorites() {
        return Set.copyOf(favoriteConstructionIds);
    }

    @Nonnull
    public List<String> favoritesOrdered() {
        return List.copyOf(favoriteConstructionIds);
    }

    public void retainKnown(@Nonnull Set<String> knownIds) {
        favoriteConstructionIds.removeIf(id -> !knownIds.contains(id));
    }

    public void mergeFrom(@Nonnull Iterable<String> ids) {
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                favoriteConstructionIds.add(normalize(id));
            }
        }
    }

    /** Replaces community building favorites while keeping non-community favorites. */
    public void syncCommunityFavorites(@Nonnull Iterable<String> communityIds) {
        Set<String> remote = new LinkedHashSet<>();
        for (String id : communityIds) {
            if (id != null && !id.isBlank()) {
                remote.add(normalize(id));
            }
        }
        favoriteConstructionIds.removeIf(id -> isCommunityBuildingId(id) && !remote.contains(id));
        favoriteConstructionIds.addAll(remote);
    }

    @Nonnull
    private static String normalize(@Nonnull String constructionId) {
        return constructionId.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isCommunityBuildingId(@Nonnull String constructionId) {
        return normalize(constructionId).startsWith("plot_community_");
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PlayerConstructionFavoritesState c = new PlayerConstructionFavoritesState();
        c.favoriteConstructionIds = new LinkedHashSet<>(favoriteConstructionIds);
        return c;
    }
}
