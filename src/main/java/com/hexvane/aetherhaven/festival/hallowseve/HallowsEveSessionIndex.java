package com.hexvane.aetherhaven.festival.hallowseve;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live Hallow's Eve maze sessions keyed by town id. Not persisted. */
public final class HallowsEveSessionIndex {
    private static final Map<UUID, HallowsEveSession> BY_TOWN = new ConcurrentHashMap<>();

    private HallowsEveSessionIndex() {}

    @Nonnull
    public static HallowsEveSession getOrCreate(@Nonnull UUID townId) {
        return BY_TOWN.computeIfAbsent(townId, id -> new HallowsEveSession());
    }

    @Nullable
    public static HallowsEveSession get(@Nonnull UUID townId) {
        return BY_TOWN.get(townId);
    }

    @Nonnull
    public static Set<Map.Entry<UUID, HallowsEveSession>> entries() {
        return BY_TOWN.entrySet();
    }

    public static void remove(@Nonnull UUID townId) {
        HallowsEveSession session = BY_TOWN.remove(townId);
        if (session != null) {
            session.clearAll();
        }
    }
}
