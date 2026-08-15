package com.hexvane.aetherhaven.festival.market;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live Market Festival sessions keyed by town id. Not persisted. */
public final class MarketSessionIndex {
    private static final Map<UUID, MarketSession> BY_TOWN = new ConcurrentHashMap<>();

    private MarketSessionIndex() {}

    @Nonnull
    public static MarketSession getOrCreate(@Nonnull UUID townId) {
        return BY_TOWN.computeIfAbsent(townId, id -> new MarketSession());
    }

    @Nullable
    public static MarketSession get(@Nonnull UUID townId) {
        return BY_TOWN.get(townId);
    }

    @Nonnull
    public static Set<Map.Entry<UUID, MarketSession>> entries() {
        return BY_TOWN.entrySet();
    }

    public static void remove(@Nonnull UUID townId) {
        MarketSession session = BY_TOWN.remove(townId);
        if (session != null) {
            session.clearAll();
        }
    }
}
