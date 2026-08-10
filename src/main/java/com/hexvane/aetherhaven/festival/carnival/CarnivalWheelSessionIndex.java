package com.hexvane.aetherhaven.festival.carnival;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live wheel sessions keyed by town id. Not persisted. */
public final class CarnivalWheelSessionIndex {
    private static final Map<UUID, CarnivalWheelSession> BY_TOWN = new ConcurrentHashMap<>();

    private CarnivalWheelSessionIndex() {}

    @Nonnull
    public static CarnivalWheelSession getOrCreate(@Nonnull UUID townId) {
        return BY_TOWN.computeIfAbsent(townId, id -> new CarnivalWheelSession());
    }

    @Nullable
    public static CarnivalWheelSession get(@Nonnull UUID townId) {
        return BY_TOWN.get(townId);
    }

    public static void remove(@Nonnull UUID townId) {
        CarnivalWheelSession session = BY_TOWN.remove(townId);
        if (session != null) {
            session.clearAll();
        }
    }
}
