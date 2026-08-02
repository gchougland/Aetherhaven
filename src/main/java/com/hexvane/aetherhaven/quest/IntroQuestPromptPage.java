package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class IntroQuestPromptPage extends AetherhavenInteractiveCustomUIPage<IntroQuestPromptPage.PageData> {
    private static final String MSG = "aetherhaven_intro_quest.aetherhaven.introQuest.prompt";

    private boolean templateAppended;

    public IntroQuestPromptPage(@Nonnull PlayerRef playerRef) {
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
            commandBuilder.append("Aetherhaven/IntroQuestPromptPage.ui");
            templateAppended = true;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#IntroPromptAccept",
                EventData.of("Action", "Accept"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#IntroPromptDecline",
                EventData.of("Action", "Decline"),
                false
            );
        }
        commandBuilder.set("#IntroPromptTitle.TextSpans", Message.translation(MSG + ".title"));
        commandBuilder.set("#IntroPromptText.TextSpans", Message.translation(MSG + ".body"));
        commandBuilder.set("#IntroPromptAccept.TextSpans", Message.translation(MSG + ".accept"));
        commandBuilder.set("#IntroPromptDecline.TextSpans", Message.translation(MSG + ".decline"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if ("Accept".equals(data.action)) {
            IntroQuestPromptService.accept(AetherhavenPlugin.get(), ref, store, playerRef);
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        IntroQuestPromptService.decline(ref, store);
        player.getPageManager().setPage(ref, store, Page.None);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .build();

        @Nullable
        String action;
    }
}
