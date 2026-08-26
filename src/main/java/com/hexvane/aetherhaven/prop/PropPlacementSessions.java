package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.placement.PlotPlacementClientPrefabPreview;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Active {@link PropPlacementSession}s by player, one per player at a time. */
public final class PropPlacementSessions {
    private static final ConcurrentHashMap<UUID, PropPlacementSession> BY_PLAYER = new ConcurrentHashMap<>();

    private PropPlacementSessions() {}

    @Nullable
    public static PropPlacementSession get(@Nonnull UUID playerUuid) {
        return BY_PLAYER.get(playerUuid);
    }

    public static void put(@Nonnull UUID playerUuid, @Nonnull PropPlacementSession session) {
        BY_PLAYER.put(playerUuid, session);
    }

    public static void remove(@Nonnull UUID playerUuid) {
        PropPlacementSession session = BY_PLAYER.remove(playerUuid);
        if (session != null && session.getWorld().getEntityStore() != null) {
            PlotPlacementClientPrefabPreview.clearWorldPreview(
                session.getWorld().getEntityStore().getStore(),
                session.getPreviewEntityRefs()
            );
        }
    }

    public static void forEachActive(@Nonnull BiConsumer<UUID, PropPlacementSession> consumer) {
        BY_PLAYER.forEach(consumer);
    }
}
