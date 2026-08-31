package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotPlacementSessions {
    private static final ConcurrentHashMap<UUID, PlotPlacementSession> BY_PLAYER = new ConcurrentHashMap<>();

    private PlotPlacementSessions() {}

    @Nullable
    public static PlotPlacementSession get(@Nonnull UUID playerUuid) {
        return BY_PLAYER.get(playerUuid);
    }

    /**
     * Replaces any existing session. Prefer {@link #replaceClearingWorldPreview} when a store is available
     * so prior world holograms are not orphaned.
     */
    public static void put(@Nonnull UUID playerUuid, @Nonnull PlotPlacementSession session) {
        BY_PLAYER.put(playerUuid, session);
    }

    /** Puts {@code session}, clearing world holograms from any previous session for this player. */
    public static void replaceClearingWorldPreview(
        @Nonnull UUID playerUuid,
        @Nonnull PlotPlacementSession session,
        @Nonnull Store<EntityStore> store
    ) {
        PlotPlacementSession previous = BY_PLAYER.put(playerUuid, session);
        if (previous != null && previous != session) {
            PlotPlacementClientPrefabPreview.clearWorldPreview(store, previous);
            PlotPlacementClientPrefabPreview.clearSessionCache(previous);
        }
    }

    public static void remove(@Nonnull UUID playerUuid) {
        BY_PLAYER.remove(playerUuid);
    }

    public static void forEachActive(@Nonnull BiConsumer<UUID, PlotPlacementSession> consumer) {
        BY_PLAYER.forEach(consumer);
    }
}
