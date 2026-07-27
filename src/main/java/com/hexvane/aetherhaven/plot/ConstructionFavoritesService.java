package com.hexvane.aetherhaven.plot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;

public final class ConstructionFavoritesService {
    private ConstructionFavoritesService() {}

    @Nonnull
    public static Set<String> listFavorites(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerConstructionFavoritesState state = ensureState(ref, store);
        return state.favorites();
    }

    /** Read-only favorites union for UI while Store writes must be deferred (e.g. during page build). */
    @Nonnull
    public static Set<String> listFavoritesIncluding(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Collection<String> additionalIds
    ) {
        Set<String> favIds = new LinkedHashSet<>();
        PlayerConstructionFavoritesState state = store.getComponent(ref, PlayerConstructionFavoritesState.getComponentType());
        if (state != null) {
            favIds.addAll(state.favorites());
        }
        for (String id : additionalIds) {
            if (id != null && !id.isBlank()) {
                favIds.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        return favIds;
    }

    public static boolean isFavorite(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String constructionId) {
        PlayerConstructionFavoritesState state = store.getComponent(ref, PlayerConstructionFavoritesState.getComponentType());
        return state != null && state.isFavorite(constructionId);
    }

    /** Read-only favorite check that also considers ids not yet merged into player state. */
    public static boolean isFavoriteIncluding(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String constructionId,
        @Nonnull Collection<String> additionalIds
    ) {
        if (isFavorite(ref, store, constructionId)) {
            return true;
        }
        String id = constructionId.trim().toLowerCase(Locale.ROOT);
        for (String extra : additionalIds) {
            if (extra != null && id.equals(extra.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** @return true when favorited after toggle, false when unfavorited */
    public static boolean toggleLocal(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String constructionId) {
        PlayerConstructionFavoritesState state = ensureState(ref, store);
        boolean added = state.toggle(constructionId);
        store.putComponent(ref, PlayerConstructionFavoritesState.getComponentType(), state);
        return added;
    }

    public static void setFavorite(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String constructionId,
        boolean favorited
    ) {
        PlayerConstructionFavoritesState state = ensureState(ref, store);
        String id = constructionId.trim().toLowerCase(Locale.ROOT);
        if (favorited) {
            state.add(id);
        } else {
            state.remove(id);
        }
        store.putComponent(ref, PlayerConstructionFavoritesState.getComponentType(), state);
    }

    public static void mergeCommunityFavorites(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<String> communityIds
    ) {
        if (communityIds.isEmpty()) {
            return;
        }
        PlayerConstructionFavoritesState state = ensureState(ref, store);
        state.mergeFrom(communityIds);
        store.putComponent(ref, PlayerConstructionFavoritesState.getComponentType(), state);
    }

    public static void retainKnown(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Set<String> knownIds) {
        PlayerConstructionFavoritesState state = store.getComponent(ref, PlayerConstructionFavoritesState.getComponentType());
        if (state == null) {
            return;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String id : knownIds) {
            normalized.add(id.trim().toLowerCase(Locale.ROOT));
        }
        state.retainKnown(normalized);
        store.putComponent(ref, PlayerConstructionFavoritesState.getComponentType(), state);
    }

    @Nonnull
    private static PlayerConstructionFavoritesState ensureState(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerConstructionFavoritesState state = store.getComponent(ref, PlayerConstructionFavoritesState.getComponentType());
        if (state == null) {
            state = new PlayerConstructionFavoritesState();
            store.addComponent(ref, PlayerConstructionFavoritesState.getComponentType(), state);
        }
        return state;
    }

    public static boolean isCommunityBuildingId(@Nonnull String constructionId) {
        return constructionId.trim().toLowerCase(Locale.ROOT).startsWith("plot_community_");
    }
}
