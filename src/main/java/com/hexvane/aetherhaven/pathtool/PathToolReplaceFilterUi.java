package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PathToolReplaceFilterUi {
    private PathToolReplaceFilterUi() {}

    public static void handleUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull PathToolPlayerComponent st
    ) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return;
        }
        @Nullable
        PathToolReplaceFilterSessions.Session session = PathToolReplaceFilterSessions.get(playerId);
        if (session != null && session.editingActive) {
            openChestWindow(ref, store, playerRef, session);
            return;
        }
        session = PathToolReplaceFilterSessions.getOrCreate(playerId);
        Set<String> toLoad = st.getReplaceFilterBlockIds();
        if (toLoad.isEmpty()) {
            toLoad = PathToolReplaceFilterEditorHelper.defaultDisplayBlockIds();
        }
        PathToolReplaceFilterEditorHelper.loadBlockIdsIntoContainer(session.container, toLoad);
        session.editingActive = true;
        openChestWindow(ref, store, playerRef, session);
    }

    public static boolean isActivelyEditing(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return false;
        }
        @Nullable
        PathToolReplaceFilterSessions.Session session = PathToolReplaceFilterSessions.get(playerId);
        return session != null && session.editingActive;
    }

    /** Copies the open chest into player state without closing the bench (e.g. leaving replace-filter mode with Q). */
    public static void syncPendingFromSession(
        @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PathToolPlayerComponent st
    ) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return;
        }
        @Nullable
        PathToolReplaceFilterSessions.Session session = PathToolReplaceFilterSessions.get(playerId);
        if (session == null || !session.editingActive) {
            return;
        }
        st.setReplaceFilterBlockIds(PathToolReplaceFilterEditorHelper.snapshotContainer(session.container));
    }

    public static boolean tryFinishEditing(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull PathToolPlayerComponent st
    ) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return false;
        }
        @Nullable
        PathToolReplaceFilterSessions.Session session = PathToolReplaceFilterSessions.get(playerId);
        if (session == null || !session.editingActive) {
            return false;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return false;
        }
        Set<String> ids = PathToolReplaceFilterEditorHelper.snapshotContainer(session.container);
        st.setReplaceFilterBlockIds(ids);
        session.editingActive = false;
        PathToolReplaceFilterSessions.clear(playerId);
        player.getPageManager().setPage(ref, store, Page.None);
        if (ids.isEmpty()) {
            playerRef.sendMessage(Message.translation("aetherhaven_items.aetherhaven.pathTool.replaceFilterUsingDefaults"));
        } else {
            playerRef.sendMessage(
                Message.translation("aetherhaven_items.aetherhaven.pathTool.replaceFilterSaved").param("count", String.valueOf(ids.size()))
            );
        }
        return true;
    }

    private static void openChestWindow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull PathToolReplaceFilterSessions.Session session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        session.editingActive = true;
        ContainerWindow window = new ContainerWindow(session.container);
        player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }
}
