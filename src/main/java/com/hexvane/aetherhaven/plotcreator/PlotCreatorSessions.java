package com.hexvane.aetherhaven.plotcreator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotCreatorSessions {
    private static final Map<UUID, PlotCreatorSession> BY_PLAYER = new ConcurrentHashMap<>();

    private PlotCreatorSessions() {}

    @Nullable
    public static PlotCreatorSession get(@Nonnull UUID playerUuid) {
        return BY_PLAYER.get(playerUuid);
    }

    public static void put(@Nonnull PlotCreatorSession session) {
        BY_PLAYER.put(session.getPlayerUuid(), session);
    }

    @Nullable
    public static PlotCreatorSession remove(@Nonnull UUID playerUuid) {
        return BY_PLAYER.remove(playerUuid);
    }

    public static boolean has(@Nonnull UUID playerUuid) {
        return BY_PLAYER.containsKey(playerUuid);
    }

    /** True when any active plot creator or building editor session targets this construction id. */
    public static boolean isConstructionInActiveSession(@Nonnull String constructionId) {
        String target = constructionId.trim().toLowerCase(java.util.Locale.ROOT);
        if (target.isEmpty()) {
            return false;
        }
        for (PlotCreatorSession session : BY_PLAYER.values()) {
            PlotCreatorDraft draft = session.getDraft();
            String id = draft.getConstructionId();
            if (id != null && target.equals(id.trim().toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
            String editing = draft.getEditingConstructionId();
            if (editing != null && target.equals(editing.trim().toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
