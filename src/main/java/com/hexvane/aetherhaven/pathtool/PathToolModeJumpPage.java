package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Quick jump menu for path tool gizmo modes (middle mouse / Pick). */
public final class PathToolModeJumpPage extends AetherhavenInteractiveCustomUIPage<PathToolModeJumpPage.PageData> {
    private static final String MODE_ROW = "#ModeJumpRow";

    private boolean templateAppended;

    public PathToolModeJumpPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PathToolModeJumpPage.ui");
            templateAppended = true;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CancelButton",
                EventData.of("Action", "Cancel"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TownsfolkButton",
                EventData.of("Action", "Townsfolk"),
                false
            );
        }
        commandBuilder.set(
            "#ModeJumpTitleText.TextSpans",
            Message.translation("aetherhaven_items.aetherhaven.pathTool.modeJump.title")
        );
        commandBuilder.set(
            "#ModeHint.TextSpans",
            Message.translation("aetherhaven_items.aetherhaven.pathTool.modeJump.hint")
        );
        commandBuilder.set(
            "#CancelButton.TextSpans",
            Message.translation("aetherhaven_items.aetherhaven.pathTool.modeJump.close")
        );
        PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
        boolean villagerNav = st == null || st.isVillagerNav();
        commandBuilder.set(
            "#TownsfolkButton.TextSpans",
            Message.translation(
                villagerNav
                    ? "aetherhaven_items.aetherhaven.pathTool.modeJump.townsfolkOn"
                    : "aetherhaven_items.aetherhaven.pathTool.modeJump.townsfolkOff"
            )
        );
        applyModeRow(commandBuilder, eventBuilder, st != null ? st.getGizmoMode() : PathToolGizmoMode.Commit);
    }

    private void applyModeRow(
        @Nonnull UICommandBuilder b,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull PathToolGizmoMode current
    ) {
        List<PathToolModeJumpModel.JumpNode> nodes = PathToolModeJumpModel.nodes(current);
        b.clear(MODE_ROW);
        for (int i = 0; i < nodes.size(); i++) {
            PathToolModeJumpModel.JumpNode node = nodes.get(i);
            b.append(MODE_ROW, "Aetherhaven/PathToolModeJumpNode.ui");
            String row = MODE_ROW + "[" + i + "]";
            boolean isCurrent = node.current();
            b.set(row + " #ModeIcon.AssetPath", node.iconAssetPath());
            b.set(row + " #ModeLabel.TextSpans", Message.translation(node.shortLangKey()));
            b.set(row + " #ModeLabel.Style.FontSize", isCurrent ? 13 : 11);
            b.set(row + " #ModeLabel.Style.RenderBold", isCurrent);
            b.set(row + " #ModeLabel.Style.TextColor", isCurrent ? "#f4e8c8" : "#c8d0d8");
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #ModeButton",
                EventData.of("Action", "Jump").append("Mode", node.mode().name()),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if ("Cancel".equals(data.action)) {
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            return;
        }
        if ("Townsfolk".equals(data.action)) {
            st.toggleVillagerNav();
            String messageId =
                st.isVillagerNav()
                    ? "aetherhaven_items.aetherhaven.pathTool.hudTownsfolkOn"
                    : "aetherhaven_items.aetherhaven.pathTool.hudTownsfolkOff";
            playerRef.sendMessage(Message.translation(messageId));
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.translation(messageId),
                Message.translation(messageId),
                NotificationStyle.Default
            );
            refreshHud(ref, store, player);
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (!"Jump".equals(data.action) || data.mode == null || data.mode.isBlank()) {
            return;
        }
        PathToolGizmoMode target;
        try {
            target = PathToolGizmoMode.valueOf(data.mode.trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.ReplaceFilter) {
            PathToolReplaceFilterUi.syncPendingFromSession(ref, store, st);
        }
        st.setGizmoMode(target);
        playerRef.sendMessage(Message.translation(PathToolInteractions.modeCycleMessageId(target)));
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(),
            Message.translation(PathToolInteractions.modeToastId(target)),
            Message.translation(PathToolInteractions.modeCycleMessageId(target)),
            NotificationStyle.Default
        );
        player.getPageManager().setPage(ref, store, Page.None);
        refreshHud(ref, store, player);
    }

    private void refreshHud(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player
    ) {
        PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (st == null || plugin == null) {
            return;
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        PathToolHudSupport.obtainPathToolHud(player, playerRef).refresh(st, cfg, playerRef);
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PathToolModeJumpPage(playerRef));
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("Mode", Codec.STRING), (d, v) -> d.mode = v, d -> d.mode)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String mode;
    }
}
