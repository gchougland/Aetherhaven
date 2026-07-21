package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PathToolReplaceFilterSessions {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private PathToolReplaceFilterSessions() {}

    public static final class Session {
        @Nonnull
        public final SimpleItemContainer container = PathToolReplaceFilterEditorHelper.createContainer();
        public boolean editingActive;
    }

    @Nonnull
    public static Session getOrCreate(@Nonnull UUID playerId) {
        return SESSIONS.computeIfAbsent(playerId, u -> new Session());
    }

    @Nullable
    public static Session get(@Nonnull UUID playerId) {
        return SESSIONS.get(playerId);
    }

    public static void clear(@Nonnull UUID playerId) {
        SESSIONS.remove(playerId);
    }
}
