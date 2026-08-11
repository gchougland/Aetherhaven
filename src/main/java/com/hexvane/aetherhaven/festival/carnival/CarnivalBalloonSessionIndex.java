package com.hexvane.aetherhaven.festival.carnival;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live balloon sessions keyed by town id. Not persisted. */
public final class CarnivalBalloonSessionIndex {
    private static final Map<UUID, CarnivalBalloonSession> BY_TOWN = new ConcurrentHashMap<>();

    private CarnivalBalloonSessionIndex() {}

    @Nonnull
    public static CarnivalBalloonSession getOrCreate(@Nonnull UUID townId) {
        return BY_TOWN.computeIfAbsent(townId, id -> new CarnivalBalloonSession());
    }

    @Nullable
    public static CarnivalBalloonSession get(@Nonnull UUID townId) {
        return BY_TOWN.get(townId);
    }

    @Nonnull
    public static Set<Map.Entry<UUID, CarnivalBalloonSession>> entries() {
        return BY_TOWN.entrySet();
    }

    public static void remove(@Nonnull UUID townId) {
        CarnivalBalloonSession session = BY_TOWN.remove(townId);
        if (session != null) {
            session.clearAll();
        }
    }
}
