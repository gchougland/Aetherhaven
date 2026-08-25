package com.hexvane.aetherhaven.festival.pigrace;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live pig race sessions keyed by town id. Not persisted. */
public final class PigRaceSessionIndex {
    private static final Map<UUID, PigRaceSession> BY_TOWN = new ConcurrentHashMap<>();

    private PigRaceSessionIndex() {}

    @Nonnull
    public static Set<Map.Entry<UUID, PigRaceSession>> entries() {
        return BY_TOWN.entrySet();
    }

    @Nonnull
    public static PigRaceSession getOrCreate(@Nonnull UUID townId) {
        return BY_TOWN.computeIfAbsent(townId, id -> new PigRaceSession());
    }

    @Nullable
    public static PigRaceSession get(@Nonnull UUID townId) {
        return BY_TOWN.get(townId);
    }

    public static void remove(@Nonnull UUID townId) {
        PigRaceSession session = BY_TOWN.remove(townId);
        if (session != null) {
            session.clearAll();
        }
    }
}
