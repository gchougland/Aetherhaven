package com.hexvane.aetherhaven.festival.treeclimb;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live tree climb sessions keyed by town id. Not persisted. */
public final class TreeClimbSessionIndex {
    private static final Map<UUID, TreeClimbSession> BY_TOWN = new ConcurrentHashMap<>();

    private TreeClimbSessionIndex() {}

    @Nonnull
    public static TreeClimbSession getOrCreate(@Nonnull UUID townId) {
        return BY_TOWN.computeIfAbsent(townId, id -> new TreeClimbSession());
    }

    @Nullable
    public static TreeClimbSession get(@Nonnull UUID townId) {
        return BY_TOWN.get(townId);
    }

    public static void remove(@Nonnull UUID townId) {
        TreeClimbSession session = BY_TOWN.remove(townId);
        if (session != null) {
            session.clearAll();
        }
    }

    @Nonnull
    public static Iterable<Map.Entry<UUID, TreeClimbSession>> entries() {
        return BY_TOWN.entrySet();
    }
}
