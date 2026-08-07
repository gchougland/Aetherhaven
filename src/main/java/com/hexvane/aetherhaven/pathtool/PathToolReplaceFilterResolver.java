package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves replace-filter allowlist from persisted player state and in-progress chest edits. */
public final class PathToolReplaceFilterResolver {
    private PathToolReplaceFilterResolver() {}

    @Nonnull
    public static Set<String> effectiveBlockIds(
        @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PathToolPlayerComponent st
    ) {
        @Nullable
        UUID playerId = playerUuid(ref, store);
        if (playerId != null) {
            @Nullable
            PathToolReplaceFilterSessions.Session session = PathToolReplaceFilterSessions.get(playerId);
            if (session != null && session.editingActive) {
                return PathToolReplaceFilterEditorHelper.snapshotContainer(session.container);
            }
        }
        return st.getReplaceFilterBlockIds();
    }

    /** {@code null} = use config defaults / soil heuristic in {@link PathToolReplacePredicate}. */
    @Nullable
    public static Set<String> nullableAllowlistForPredicate(
        @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PathToolPlayerComponent st
    ) {
        Set<String> ids = effectiveBlockIds(ref, store, st);
        return ids.isEmpty() ? null : ids;
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }
}
