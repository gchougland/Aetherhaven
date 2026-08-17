package com.hexvane.aetherhaven.festival.wintertide;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live Wintertide sessions keyed by town id. Not persisted. */
public final class WintertideSessionIndex {
    private static final Map<UUID, WintertideSession> BY_TOWN = new ConcurrentHashMap<>();

    private WintertideSessionIndex() {}

    @Nonnull
    public static WintertideSession getOrCreate(@Nonnull UUID townId) {
        return BY_TOWN.computeIfAbsent(townId, id -> new WintertideSession());
    }

    @Nullable
    public static WintertideSession get(@Nonnull UUID townId) {
        return BY_TOWN.get(townId);
    }

    public static void remove(@Nonnull UUID townId) {
        WintertideSession session = BY_TOWN.remove(townId);
        if (session != null) {
            session.clearAll();
        }
    }
}
