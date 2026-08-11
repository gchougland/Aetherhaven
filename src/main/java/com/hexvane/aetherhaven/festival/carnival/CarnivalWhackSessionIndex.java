package com.hexvane.aetherhaven.festival.carnival;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live whack sessions keyed by town id. Not persisted. */
public final class CarnivalWhackSessionIndex {
    private static final Map<UUID, CarnivalWhackSession> BY_TOWN = new ConcurrentHashMap<>();

    private CarnivalWhackSessionIndex() {}

    @Nonnull
    public static CarnivalWhackSession getOrCreate(@Nonnull UUID townId) {
        return BY_TOWN.computeIfAbsent(townId, id -> new CarnivalWhackSession());
    }

    @Nullable
    public static CarnivalWhackSession get(@Nonnull UUID townId) {
        return BY_TOWN.get(townId);
    }

    @Nonnull
    public static Set<Map.Entry<UUID, CarnivalWhackSession>> entries() {
        return BY_TOWN.entrySet();
    }

    public static void remove(@Nonnull UUID townId) {
        CarnivalWhackSession session = BY_TOWN.remove(townId);
        if (session != null) {
            session.clearAll();
        }
    }
}
