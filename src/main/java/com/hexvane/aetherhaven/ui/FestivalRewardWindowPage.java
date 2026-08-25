package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.festival.FestivalRewardQueue;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Centred window listing everything a festival activity just handed over, with an icon, name and amount per prize.
 * Shown instead of a stack of corner toasts so a player who wins several things sees one readable summary.
 */
public final class FestivalRewardWindowPage extends AetherhavenInteractiveCustomUIPage<FestivalRewardWindowPage.PageData> {
    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.reward";
    private static final String ROWS = "#RewardScroll #RewardRows";

    private final List<FestivalRewardQueue.Entry> entries;
    private final FestivalRewardQueue.Outcome outcome;
    private boolean templateAppended;

    public FestivalRewardWindowPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull List<FestivalRewardQueue.Entry> entries,
        @Nonnull FestivalRewardQueue.Outcome outcome
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.entries = List.copyOf(entries);
        this.outcome = outcome;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/FestivalRewardWindow.ui");
            templateAppended = true;
        }
        commandBuilder.set("#FestivalRewardTitleText.TextSpans", Message.translation(LANG + ".window.title"));
        commandBuilder.set("#FestivalRewardHeadline.TextSpans", headline());
        commandBuilder.set("#FestivalRewardCloseButton.TextSpans", Message.translation(LANG + ".window.close"));

        boolean hasItems = !entries.isEmpty();
        commandBuilder.set("#RewardScroll.Visible", hasItems);
        commandBuilder.set(
            "#FestivalRewardSubtext.TextSpans",
            hasItems
                ? Message.translation(LANG + ".window.subtext.items")
                : Message.translation(LANG + ".window.subtext.empty")
        );

        commandBuilder.clear(ROWS);
        for (int i = 0; i < entries.size(); i++) {
            FestivalRewardQueue.Entry entry = entries.get(i);
            commandBuilder.append(ROWS, "Aetherhaven/FestivalRewardWindowRow.ui");
            String row = ROWS + "[" + i + "]";
            ItemGridSlot slot = AetherhavenUiItemGrids.slotForKnownItem(entry.itemId(), entry.amount());
            if (slot != null) {
                AetherhavenUiItemGrids.setSingleSlot(commandBuilder, row + " #RewardIcon", slot);
            } else {
                AetherhavenUiItemGrids.setSingleSlotEmpty(commandBuilder, row + " #RewardIcon");
            }
            commandBuilder.set(row + " #RewardName.TextSpans", UiMaterialLabels.itemNameMessage(entry.itemId()));
            commandBuilder.set(
                row + " #RewardAmount.TextSpans",
                Message.translation(LANG + ".window.amount").param("count", String.valueOf(entry.amount()))
            );
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#FestivalRewardCloseButton",
            new EventData().append("Action", "Close"),
            false
        );
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (data.action != null && data.action.equalsIgnoreCase("Close")) {
            close();
        }
    }

    @Nonnull
    private Message headline() {
        return switch (outcome) {
            case WON -> Message.translation(LANG + ".window.headline.won");
            case LOST -> Message.translation(LANG + ".window.headline.lost");
            case GIFTED -> Message.translation(LANG + ".window.headline.gifted");
            case NEUTRAL -> Message.translation(LANG + ".window.headline.neutral");
        };
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .build();

        @Nullable
        private String action;
    }
}
