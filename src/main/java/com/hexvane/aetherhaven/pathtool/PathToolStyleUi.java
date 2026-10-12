package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.PathToolStyleDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Opens the style list, name prompt, and vanilla chest editor (plot creator materials pattern). */
public final class PathToolStyleUi {
    private PathToolStyleUi() {}

    public static void handleUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return;
        }
        @Nullable
        PathToolStyleSessions.Session session = PathToolStyleSessions.get(playerId);
        if (session != null && session.editingActive) {
            openChestWindow(ref, store, playerRef, session);
            return;
        }
        openStyleList(ref, store, playerRef);
    }

    /** True while the player has an open or resumable chest edit session for a path style. */
    public static boolean isActivelyEditing(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        return activeSession(ref, store) != null;
    }

    /** Second extra tool key in style designer while the chest is open: save and close the chest. */
    public static boolean tryFinishEditing(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return false;
        }
        @Nullable
        PathToolStyleSessions.Session session = PathToolStyleSessions.get(playerId);
        if (session == null || !session.editingActive) {
            return false;
        }
        return saveSession(ref, store, playerRef, session);
    }

    public static void openStyleList(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PathToolStyleListPage(playerRef));
    }

    public static void beginCreate(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PathToolStyleSessions.Session session = PathToolStyleSessions.getOrCreate(playerId);
        session.creating = true;
        session.editIndex = -1;
        session.editingActive = false;
        session.styleName = "New path";
        session.container.clear();
        PathToolStyleEditorHelper.loadStyleIntoContainer(session.container, PathToolStyleDefinition.newDefaultDirtStyle());
        player.getPageManager().openCustomPage(ref, store, new PathToolStyleNamePage(playerRef));
    }

    public static void beginEdit(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        int styleIndex
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        List<PathToolStyleDefinition> styles = plugin.getConfig().get().getPathToolStyleDefinitions();
        if (styleIndex < 0 || styleIndex >= styles.size()) {
            return;
        }
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return;
        }
        PathToolStyleSessions.Session session = PathToolStyleSessions.getOrCreate(playerId);
        session.creating = false;
        session.editIndex = styleIndex;
        session.listSelectedIndex = styleIndex;
        session.styleName = styles.get(styleIndex).getName();
        session.container.clear();
        PathToolStyleEditorHelper.loadStyleIntoContainer(
            session.container,
            PathToolStyleDefinition.copyOf(styles.get(styleIndex))
        );
        session.editingActive = true;
        openChestWindow(ref, store, playerRef, session);
    }

    public static void continueFromNamePage(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull String styleName
    ) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return;
        }
        PathToolStyleSessions.Session session = PathToolStyleSessions.get(playerId);
        if (session == null) {
            return;
        }
        session.styleName = styleName;
        session.editingActive = true;
        openChestWindow(ref, store, playerRef, session);
    }

    public static void openChestWindow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull PathToolStyleSessions.Session session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        session.editingActive = true;
        ContainerWindow window = new ContainerWindow(session.container);
        player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
    }

    public static void cancelAll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        @Nullable
        PathToolStyleSessions.Session session = PathToolStyleSessions.get(playerId);
        if (player != null && session != null) {
            PathToolStyleEditorHelper.returnAllItems(session.container, player, ref, store);
            PathToolStyleSessions.clear(playerId);
        }
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    private static boolean saveSession(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull PathToolStyleSessions.Session session
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (plugin == null || player == null) {
            return false;
        }
        PathToolStyleDefinition snapshot = PathToolStyleEditorHelper.snapshotContainer(session.container, session.styleName);
        if (!PathToolStyleEditorHelper.hasAnyBlock(snapshot)) {
            playerRef.sendMessage(Message.translation("aetherhaven_items.aetherhaven.pathTool.styleEditNeedBlocks"));
            return false;
        }
        List<PathToolStyleDefinition> styles = new ArrayList<>(plugin.getConfig().get().getPathToolStyleDefinitions());
        if (session.creating || session.editIndex < 0) {
            styles.add(snapshot);
            session.editIndex = styles.size() - 1;
            session.creating = false;
            session.listSelectedIndex = session.editIndex;
        } else if (session.editIndex < styles.size()) {
            styles.set(session.editIndex, snapshot);
        } else {
            styles.add(snapshot);
            session.editIndex = styles.size() - 1;
        }
        plugin.getConfig().get().setPathToolStyleDefinitions(styles);
        plugin.getConfig().save().join();
        session.editingActive = false;
        player.getPageManager().setPage(ref, store, Page.None);
        playerRef.sendMessage(
            Message
                .translation("aetherhaven_items.aetherhaven.pathTool.styleSaved")
                .param("name", session.styleName)
        );
        return true;
    }

    @Nullable
    public static PathToolStyleSessions.Session activeSession(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID playerId = playerUuid(ref, store);
        if (playerId == null) {
            return null;
        }
        PathToolStyleSessions.Session session = PathToolStyleSessions.get(playerId);
        if (session != null && session.editingActive) {
            return session;
        }
        return null;
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }
}
