package com.hexvane.aetherhaven.placement;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotPlacementSessions {
    private static final ConcurrentHashMap<UUID, PlotPlacementSession> BY_PLAYER = new ConcurrentHashMap<>();

    private PlotPlacementSessions() {}

    @Nullable
    public static PlotPlacementSession get(@Nonnull UUID playerUuid) {
        return BY_PLAYER.get(playerUuid);
    }

    public static void put(@Nonnull UUID playerUuid, @Nonnull PlotPlacementSession session) {
        BY_PLAYER.put(playerUuid, session);
    }

    public static void remove(@Nonnull UUID playerUuid) {
        BY_PLAYER.remove(playerUuid);
    }
}
