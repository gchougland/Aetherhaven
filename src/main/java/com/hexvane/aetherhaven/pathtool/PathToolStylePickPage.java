package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.PathToolStyleDefinition;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.ui.AetherhavenUiLocalization;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Choose the active path style for placement (not the style editor). */
public final class PathToolStylePickPage extends AetherhavenInteractiveCustomUIPage<PathToolStylePickPage.PageData> {
    private static final String ROWS = "#ListScroll #Rows";

    private boolean templateAppended;
    private int selectedIndex;

    public PathToolStylePickPage(@Nonnull PlayerRef playerRef, int initialIndex) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.selectedIndex = Math.max(0, initialIndex);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PathToolStylePickPage.ui");
            AetherhavenUiLocalization.applyPathToolStylePickPage(commandBuilder);
            templateAppended = true;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ChooseButton",
                EventData.of("Action", "Choose"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CancelButton",
                EventData.of("Action", "Cancel"),
                false
            );
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        commandBuilder.clear(ROWS);
        if (plugin == null) {
            commandBuilder.set(
                "#Hint.TextSpans",
                Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded")
            );
            return;
        }
        List<PathToolStyleDefinition> styles = plugin.getConfig().get().getPathToolStyleDefinitions();
        if (selectedIndex >= styles.size()) {
            selectedIndex = Math.max(0, styles.size() - 1);
        }
        if (styles.isEmpty()) {
            commandBuilder.set(
                "#Hint.TextSpans",
                Message.translation("aetherhaven_items.aetherhaven.pathTool.styleListEmpty")
            );
            return;
        }
        commandBuilder.set(
            "#Hint.TextSpans",
            Message
                .translation("aetherhaven_items.aetherhaven.pathTool.stylePickHint")
                .param("name", styles.get(selectedIndex).getName())
        );
        for (int i = 0; i < styles.size(); i++) {
            commandBuilder.append(ROWS, "Aetherhaven/PathToolStyleListRow.ui");
            String row = ROWS + "[" + i + "]";
            boolean selected = i == selectedIndex;
            commandBuilder.set(row + " #SelectHilite.Visible", selected);
            commandBuilder.set(row + " #SelectedMark.Visible", selected);
            if (selected) {
                commandBuilder.set(
                    row + " #SelectedMark.TextSpans",
                    Message.translation("aetherhaven_items.aetherhaven.pathTool.styleListRowSelected")
                );
            }
            commandBuilder.set(row + " #Select #StyleName.TextSpans", Message.raw(styles.get(i).getName()));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                new EventData().append("Action", "Select").append("Index", Integer.toString(i)),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        switch (data.action) {
            case "Cancel" -> close();
            case "Select" -> {
                int idx = parseIndex(data.indexRaw);
                if (idx >= 0) {
                    selectedIndex = idx;
                    refreshList(ref, store);
                }
            }
            case "Choose" -> {
                if (plugin == null) {
                    close();
                    return;
                }
                int n = plugin.getConfig().get().getPathToolStyleDefinitions().size();
                if (n <= 0 || selectedIndex < 0 || selectedIndex >= n) {
                    playerRef.sendMessage(Message.translation("aetherhaven_items.aetherhaven.pathTool.styleListPickOne"));
                    return;
                }
                PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
                if (st != null) {
                    st.setPathStyleIndex(selectedIndex);
                }
                String name = plugin.getConfig().get().getPathToolStyleName(selectedIndex);
                NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.translation("aetherhaven_items.aetherhaven.pathTool.toastStyleCycled"),
                    Message.translation("aetherhaven_items.aetherhaven.pathTool.styleCycled").param("style", name),
                    NotificationStyle.Default
                );
                close();
            }
            default -> {}
        }
    }

    private void refreshList(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
        int idx = st != null ? st.getPathStyleIndex() : 0;
        var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PathToolStylePickPage(playerRef, idx));
    }

    private static int parseIndex(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("Index", Codec.STRING), (d, v) -> d.indexRaw = v, d -> d.indexRaw)
            .add()
            .build();

        @Nullable
        String action;
        @Nullable
        String indexRaw;
    }
}
