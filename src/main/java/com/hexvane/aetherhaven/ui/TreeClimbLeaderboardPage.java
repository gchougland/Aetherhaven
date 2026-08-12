package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbIds;
import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbLeaderboard;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Shared world tree climb times: rank, name, and best time. */
public final class TreeClimbLeaderboardPage
    extends AetherhavenInteractiveCustomUIPage<TreeClimbLeaderboardPage.PageData> {
    private static final String ROWS = "#LeaderboardListPanel #LeaderboardScroll #LeaderboardRows";
    private boolean templateAppended;

    public TreeClimbLeaderboardPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/TreeClimbLeaderboardPage.ui");
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "Close"),
                false
            );
            templateAppended = true;
        }
        commandBuilder.set(
            "#LeaderboardTitleText.TextSpans",
            Message.translation("aetherhaven_festivals.aetherhaven.festival.tree_climbing.leaderboard.title")
        );
        commandBuilder.set(
            "#LeaderboardHintText.TextSpans",
            Message.translation("aetherhaven_festivals.aetherhaven.festival.tree_climbing.leaderboard.hint")
        );
        commandBuilder.set(
            "#CloseButton.TextSpans",
            Message.translation("aetherhaven_festivals.aetherhaven.festival.tree_climbing.leaderboard.close")
        );

        commandBuilder.clear(ROWS);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null) {
            return;
        }
        List<TreeClimbLeaderboard.Entry> entries = TreeClimbLeaderboard.topEntries(world, plugin);
        UUID self = null;
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc != null) {
            self = uc.getUuid();
        }
        TreeClimbLeaderboard.Entry mine =
            self != null ? TreeClimbLeaderboard.bestForPlayer(world, plugin, self) : null;
        if (mine != null) {
            commandBuilder.set(
                "#YourBestText.TextSpans",
                Message.translation("aetherhaven_festivals.aetherhaven.festival.tree_climbing.leaderboard.your_best")
                    .param("time", TreeClimbIds.formatTime(mine.bestSeconds()))
            );
        } else {
            commandBuilder.set(
                "#YourBestText.TextSpans",
                Message.translation("aetherhaven_festivals.aetherhaven.festival.tree_climbing.leaderboard.your_best_none")
            );
        }

        if (entries.isEmpty()) {
            commandBuilder.append(ROWS, "Aetherhaven/TreeClimbLeaderboardRow.ui");
            commandBuilder.set(ROWS + "[0] #RankLabel.TextSpans", Message.raw("-"));
            commandBuilder.set(
                ROWS + "[0] #NameLabel.TextSpans",
                Message.translation("aetherhaven_festivals.aetherhaven.festival.tree_climbing.leaderboard.empty")
            );
            commandBuilder.set(ROWS + "[0] #TimeLabel.TextSpans", Message.raw(""));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            TreeClimbLeaderboard.Entry e = entries.get(i);
            commandBuilder.append(ROWS, "Aetherhaven/TreeClimbLeaderboardRow.ui");
            String row = ROWS + "[" + i + "]";
            commandBuilder.set(row + " #RankLabel.TextSpans", Message.raw(String.valueOf(i + 1)));
            commandBuilder.set(row + " #NameLabel.TextSpans", Message.raw(e.playerName()));
            commandBuilder.set(
                row + " #TimeLabel.TextSpans",
                Message.raw(TreeClimbIds.formatTime(e.bestSeconds()))
            );
        }
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if ("Close".equalsIgnoreCase(data.action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().setPage(ref, store, Page.None);
            }
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action)
                .add()
                .build();

        public String action = "";
    }
}
