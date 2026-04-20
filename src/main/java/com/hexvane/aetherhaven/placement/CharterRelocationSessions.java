package com.hexvane.aetherhaven.placement;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CharterRelocationSessions {
    private static final ConcurrentHashMap<UUID, CharterRelocationSession> BY_PLAYER = new ConcurrentHashMap<>();

    private CharterRelocationSessions() {}

    @Nullable
    public static CharterRelocationSession get(@Nonnull UUID playerUuid) {
        return BY_PLAYER.get(playerUuid);
    }

    public static void put(@Nonnull UUID playerUuid, @Nonnull CharterRelocationSession session) {
        BY_PLAYER.put(playerUuid, session);
    }

    public static void remove(@Nonnull UUID playerUuid) {
        BY_PLAYER.remove(playerUuid);
    }

    @Nullable
    public static CharterRelocationSession removeAndGet(@Nonnull UUID playerUuid) {
        return BY_PLAYER.remove(playerUuid);
    }
}
