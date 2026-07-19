package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotState;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.worldnpc.WorldNpcPlayerProgress;
import com.hexvane.aetherhaven.worldnpc.WorldNpcRegistry;
import com.hexvane.aetherhaven.worldnpc.WorldQuestBoardCatalog;
import com.hexvane.aetherhaven.worldnpc.WorldQuestBoardProfileJson;
import com.hexvane.aetherhaven.worldnpc.WorldQuestBoardService;
import com.hexvane.aetherhaven.worldnpc.WorldQuestIds;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Thin world quest board UI (per player profile, no town required). */
public final class WorldQuestBoardPage extends AetherhavenInteractiveCustomUIPage<WorldQuestBoardPage.PageData> {
    private static final String CARD_ROW = "#QuestBoardRoot #Content #CardRowScroll #CardRow";

    private boolean templateAppended;
    @Nonnull
    private String profileId = "hub_default";

    public WorldQuestBoardPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, "hub_default");
    }

    public WorldQuestBoardPage(@Nonnull PlayerRef playerRef, @Nonnull String profileId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.profileId = profileId != null && !profileId.isBlank() ? profileId.trim() : "hub_default";
    }

    public void setProfileId(@Nonnull String profileId) {
        this.profileId = profileId.trim();
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/QuestBoardPage.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyQuestBoardPage(commandBuilder);
        populateContent(ref, commandBuilder, eventBuilder, store);
    }

    private void populateContent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        World world = store.getExternalData().getWorld();
        if (plugin == null || uc == null) {
            showBlocked(commandBuilder, Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        WorldQuestBoardCatalog catalog = plugin.getWorldQuestBoardCatalog();
        WorldQuestBoardProfileJson profile = catalog.get(profileId);
        if (profile == null) {
            showBlocked(
                commandBuilder,
                Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.worldQuestBoard.missingProfile")
                    .param("id", profileId)
            );
            return;
        }
        WorldQuestBoardService.ensureInitialized(world, plugin, uc.getUuid(), profileId);
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(uc.getUuid());
        List<QuestBoardSlotRecord> slots = progress.boardSlots(profileId);

        commandBuilder.set("#BlockedMessage.Visible", false);
        commandBuilder.set("#RankBar.Visible", true);
        commandBuilder.set("#CardRowScroll.Visible", true);
        int xp = progress.boardRankXp(profileId);
        String tier = WorldQuestBoardService.tierIdForXp(xp, profile);
        commandBuilder.set(
            "#TownRankLabel.TextSpans",
            Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.worldQuestBoard.rankLabel")
                .param("rank", tier)
                .param("xp", String.valueOf(xp))
        );

        commandBuilder.clear(CARD_ROW);
        int n = Math.min(slots.size(), profile.slotCount());
        for (int i = 0; i < n; i++) {
            QuestBoardSlotRecord slot = slots.get(i);
            commandBuilder.append(CARD_ROW, "Aetherhaven/QuestBoardCard.ui");
            String card = CARD_ROW + "[" + i + "]";
            QuestBoardSlotState state = slot.stateEnum();
            if (state == QuestBoardSlotState.EMPTY) {
                commandBuilder.set(card + " #EmptyHint.Visible", true);
                commandBuilder.set(card + " #CardInner.Visible", false);
                continue;
            }
            commandBuilder.set(card + " #EmptyHint.Visible", false);
            commandBuilder.set(card + " #CardInner.Visible", true);
            commandBuilder.set(card + " #CompletedOnly.Visible", false);
            commandBuilder.set(card + " #CardBody.Visible", true);
            commandBuilder.set(card + " #CardFooter.Visible", true);
            Message title =
                slot.getTitleLangKey() != null && !slot.getTitleLangKey().isBlank()
                    ? Message.translation(slot.getTitleLangKey())
                    : Message.raw(WorldQuestIds.boardRow(slot.instanceIdOrEmpty()));
            Message desc =
                slot.getDescriptionLangKey() != null && !slot.getDescriptionLangKey().isBlank()
                    ? Message.translation(slot.getDescriptionLangKey())
                    : Message.raw("");
            commandBuilder.set(card + " #QuestTitle.TextSpans", title);
            commandBuilder.set(card + " #QuestDescription.TextSpans", desc);
            commandBuilder.set(card + " #ItemRow.Visible", false);
            commandBuilder.set(card + " #ObjectiveText.Visible", false);
            commandBuilder.set(card + " #RewardReputationLine.Visible", false);
            if (state == QuestBoardSlotState.ACCEPTED) {
                commandBuilder.set(
                    card + " #ActionButton.TextSpans",
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.abandonButton")
                );
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    card + " #ActionButton",
                    new EventData().append("Action", "AbandonSlot").append("SlotIndex", String.valueOf(i)),
                    false
                );
            } else {
                commandBuilder.set(
                    card + " #ActionButton.TextSpans",
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.acceptButton")
                );
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    card + " #ActionButton",
                    new EventData().append("Action", "AcceptSlot").append("SlotIndex", String.valueOf(i)),
                    false
                );
            }
        }
    }

    private static void showBlocked(@Nonnull UICommandBuilder commandBuilder, @Nonnull Message msg) {
        commandBuilder.set("#BlockedMessage.Visible", true);
        commandBuilder.set("#BlockedMessage.TextSpans", msg);
        commandBuilder.set("#RankBar.Visible", false);
        commandBuilder.set("#CardRowScroll.Visible", false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null || data.action.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || uc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        WorldNpcPlayerProgress progress =
            AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin).getOrCreatePlayerProgress(uc.getUuid());
        List<QuestBoardSlotRecord> slots = progress.boardSlots(profileId);
        int slotIndex = parseSlotIndex(data.slotIndex);
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return;
        }
        QuestBoardSlotRecord slot = slots.get(slotIndex);
        String instanceId = slot.instanceIdOrEmpty();
        if ("AcceptSlot".equalsIgnoreCase(data.action)) {
            WorldQuestBoardService.accept(world, plugin, uc.getUuid(), profileId, instanceId);
        } else if ("AbandonSlot".equalsIgnoreCase(data.action)) {
            WorldQuestBoardService.abandon(world, plugin, uc.getUuid(), profileId, instanceId);
        } else {
            return;
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        populateContent(ref, cmd, evt, store);
        sendUpdate(cmd, evt, false);
    }

    private static int parseSlotIndex(@Nullable String raw) {
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
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("SlotIndex", Codec.STRING), (d, v) -> d.slotIndex = v, d -> d.slotIndex)
                .add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String slotIndex;
    }
}
