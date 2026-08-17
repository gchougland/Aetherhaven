package com.hexvane.aetherhaven.festival.snowball;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live snowball sessions keyed by town id. Not persisted. */
public final class SnowballSessionIndex {
    private static final Map<UUID, SnowballSession> BY_TOWN = new ConcurrentHashMap<>();

    private SnowballSessionIndex() {}

    @Nonnull
    public static SnowballSession getOrCreate(@Nonnull UUID townId) {
        return BY_TOWN.computeIfAbsent(townId, id -> new SnowballSession());
    }

    @Nullable
    public static SnowballSession get(@Nonnull UUID townId) {
        return BY_TOWN.get(townId);
    }

    public static void remove(@Nonnull UUID townId) {
        SnowballSession session = BY_TOWN.remove(townId);
        if (session != null) {
            session.clearAll();
        }
    }

    @Nonnull
    public static Iterable<Map.Entry<UUID, SnowballSession>> entries() {
        return BY_TOWN.entrySet();
    }

    public static boolean isLivingFighter(@Nonnull UUID uuid) {
        for (SnowballSession session : BY_TOWN.values()) {
            if (session.isLivingFighter(uuid)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static SnowballSession sessionForFighter(@Nonnull UUID uuid) {
        for (SnowballSession session : BY_TOWN.values()) {
            if (session.isFighter(uuid) && session.isFightBusy()) {
                return session;
            }
        }
        return null;
    }
}
