package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.quest.QuestRewardService;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hexvane.aetherhaven.questboard.QuestBoardCatalog;
import com.hexvane.aetherhaven.questboard.HuntQuestBoardHandler;
import com.hexvane.aetherhaven.questboard.RaidQuestBoardHandler;
import com.hexvane.aetherhaven.questboard.QuestBoardGiverDisplay;
import com.hexvane.aetherhaven.questboard.QuestBoardItemRequirement;
import com.hexvane.aetherhaven.questboard.QuestBoardService;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotState;
import com.hexvane.aetherhaven.questboard.TownQuestBoardRank;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
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

public final class QuestBoardPage extends AetherhavenInteractiveCustomUIPage<QuestBoardPage.PageData> {
    private static final String CARD_ROW = "#QuestBoardRoot #Content #CardRowScroll #CardRow";
    private static final int MAX_ITEMS = 6;
    private static final int MAX_REWARD_ITEMS = 6;

    /**
     * {@code append(ui)} must run only once per page instance; repeating it on every {@link #sendUpdate} duplicates the
     * whole tree and breaks card selectors.
     */
    private boolean templateAppended;

    public QuestBoardPage(@Nonnull PlayerRef playerRef) {
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

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, ref, tm);
        if (town == null) {
            showBlocked(commandBuilder, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.needTown"));
            return;
        }
        if (!town.playerHasQuestPermission(uc.getUuid())) {
            showBlocked(commandBuilder, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.noPermission"));
            return;
        }

        QuestBoardCatalog catalog = plugin.getQuestBoardCatalog();
        Random rng = new Random(town.getTownId().hashCode() ^ System.nanoTime());
        QuestBoardService.ensureBoardInitialized(town, store, catalog, rng);
        tm.updateTown(town);

        commandBuilder.set("#BlockedMessage.Visible", false);
        commandBuilder.set("#RankBar.Visible", true);
        commandBuilder.set("#CardRowScroll.Visible", true);

        applyRankBar(commandBuilder, town, catalog);
        applyCards(commandBuilder, eventBuilder, town, store, catalog, uc.getUuid());
    }

    private static void showBlocked(@Nonnull UICommandBuilder commandBuilder, @Nonnull Message msg) {
        commandBuilder.set("#BlockedMessage.Visible", true);
        commandBuilder.set("#BlockedMessage.TextSpans", msg);
        commandBuilder.set("#RankBar.Visible", false);
        commandBuilder.set("#CardRowScroll.Visible", false);
    }

    private static void applyRankBar(@Nonnull UICommandBuilder cmd, @Nonnull TownRecord town, @Nonnull QuestBoardCatalog catalog) {
        int xp = town.getQuestBoardRankXp();
        String tier = TownQuestBoardRank.tierIdForXp(xp, catalog);
        cmd.set("#TownRankIcon.AssetPath", TownQuestBoardRank.iconPathForRank(tier, catalog));
        cmd.set(
            "#TownRankLabel.TextSpans",
            Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.townRankLabel")
        );
        QuestBoardRankBarUi.apply(cmd, xp, catalog);
    }

    private static void applyCards(
        @Nonnull UICommandBuilder cmd,
        @Nonnull UIEventBuilder evt,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull UUID playerUuid
    ) {
        cmd.clear(CARD_ROW);
        List<QuestBoardSlotRecord> slots = town.getQuestBoardSlots();
        int n = Math.min(slots.size(), catalog.slotCount());
        for (int i = 0; i < n; i++) {
            QuestBoardSlotRecord slot = slots.get(i);
            cmd.append(CARD_ROW, "Aetherhaven/QuestBoardCard.ui");
            String card = CARD_ROW + "[" + i + "]";
            QuestBoardSlotState state = slot.stateEnum();

            if (state == QuestBoardSlotState.EMPTY) {
                cmd.set(card + " #EmptyHint.Visible", true);
                cmd.set(card + " #CardInner.Visible", false);
                cmd.set(
                    card + " #EmptyHint.TextSpans",
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.emptySlot")
                );
                continue;
            }

            cmd.set(card + " #EmptyHint.Visible", false);
            cmd.set(card + " #CardInner.Visible", true);

            if (state == QuestBoardSlotState.COMPLETED) {
                cmd.set(card + " #CardBody.Visible", false);
                cmd.set(card + " #CardFooter.Visible", false);
                cmd.set(card + " #CompletedOnly.Visible", true);
                cmd.set(
                    card + " #CompletedBanner.TextSpans",
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.completedBanner")
                        .insert(Message.raw("\n"))
                        .insert(Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.completedRefreshHint"))
                );
                continue;
            }

            cmd.set(card + " #CompletedOnly.Visible", false);
            cmd.set(card + " #CardBody.Visible", true);
            cmd.set(card + " #CardFooter.Visible", true);
            cmd.set(card + " #CardHeader.Visible", true);
            cmd.set(card + " #QuestDescription.Visible", true);
            cmd.set(card + " #RequestedLabel.Visible", true);
            cmd.set(card + " #ItemRow.Visible", true);
            cmd.set(card + " #RewardLabelHeader.Visible", true);
            cmd.set(card + " #DaysLeft.Visible", true);
            cmd.set(card + " #ActionButton.Visible", true);
            if (slot.isHuntQuest() || slot.isRaidQuest()) {
                cmd.set(
                    card + " #RequestedLabel.TextSpans",
                    Message.translation(
                        slot.isRaidQuest()
                            ? "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.requestedRaidLabel"
                            : "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.requestedHuntLabel"
                    )
                );
            } else {
                cmd.set(
                    card + " #RequestedLabel.TextSpans",
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.requestedLabel")
                );
            }
            cmd.set(
                card + " #RewardLabelHeader.TextSpans",
                Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.rewardLabel")
            );

            String questRank = slot.getQuestRank() != null ? slot.getQuestRank() : "E";
            cmd.set(card + " #QuestRankIcon.AssetPath", TownQuestBoardRank.iconPathForRank(questRank, catalog));
            cmd.set(card + " #Portrait.AssetPath", QuestBoardGiverDisplay.portraitPath(slot, store));
            cmd.set(card + " #QuestTitle.TextSpans", QuestBoardService.displayTitle(slot, town, store, catalog));
            cmd.set(card + " #QuestDescription.TextSpans", QuestBoardService.displayDescription(slot, town, store, catalog));

            applyObjectiveRow(cmd, card, slot);
            applyRewardRow(cmd, card, slot, store, town);

            if (state == QuestBoardSlotState.ACCEPTED) {
                cmd.set(card + " #DaysLeft.Visible", true);
                int days = QuestBoardService.daysRemaining(slot);
                cmd.set(
                    card + " #DaysLeft.TextSpans",
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.daysLeft").param("days", String.valueOf(days))
                );
                cmd.set(
                    card + " #ActionButton.TextSpans",
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.abandonButton")
                );
                if (town.playerCanAbandonQuests(playerUuid)) {
                    cmd.set(card + " #ActionButton.Disabled", false);
                    evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        card + " #ActionButton",
                        new EventData().append("Action", "AbandonSlot").append("SlotIndex", String.valueOf(i)),
                        false
                    );
                } else {
                    cmd.set(card + " #ActionButton.Disabled", true);
                }
            } else {
                cmd.set(card + " #DaysLeft.Visible", true);
                cmd.set(
                    card + " #DaysLeft.TextSpans",
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.daysLimit")
                        .param("days", String.valueOf(slot.getDaysLimit()))
                );
                cmd.set(
                    card + " #ActionButton.TextSpans",
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.acceptButton")
                );
                cmd.set(card + " #ActionButton.Disabled", false);
                if (town.playerCanAcceptQuests(playerUuid)) {
                    evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        card + " #ActionButton",
                        new EventData().append("Action", "AcceptSlot").append("SlotIndex", String.valueOf(i)),
                        false
                    );
                } else {
                    cmd.set(card + " #ActionButton.Disabled", true);
                }
            }
        }
    }

    private static void applyObjectiveRow(@Nonnull UICommandBuilder cmd, @Nonnull String card, @Nonnull QuestBoardSlotRecord slot) {
        if (slot.isRaidQuest()) {
            cmd.set(card + " #ItemRow.Visible", false);
            cmd.set(card + " #ObjectiveText.Visible", true);
            cmd.set(card + " #ObjectiveText.TextSpans", RaidQuestBoardHandler.raidObjectiveCardText(slot));
            return;
        }
        if (slot.isHuntQuest()) {
            cmd.set(card + " #ItemRow.Visible", false);
            cmd.set(card + " #ObjectiveText.Visible", true);
            cmd.set(card + " #ObjectiveText.TextSpans", HuntQuestBoardHandler.huntObjectiveCardText(slot));
            return;
        }
        cmd.set(card + " #ObjectiveText.Visible", false);
        cmd.clear(card + " #ItemRow");
        List<QuestBoardItemRequirement> items = slot.requiredItemsOrEmpty();
        int count = Math.min(items.size(), MAX_ITEMS);
        int slotIndex = 0;
        for (int j = 0; j < count; j++) {
            QuestBoardItemRequirement req = items.get(j);
            ItemGridSlot gridSlot = AetherhavenUiItemGrids.slotForKnownItem(req.itemIdOrEmpty(), req.count());
            if (gridSlot == null) {
                continue;
            }
            cmd.append(card + " #ItemRow", "Aetherhaven/QuestBoardItemSlot.ui");
            String itemSel = card + " #ItemRow[" + slotIndex + "]";
            slotIndex++;
            AetherhavenUiItemGrids.setSingleSlot(cmd, itemSel + " #ItemIcon", gridSlot);
        }
        cmd.set(card + " #ItemRow.Visible", slotIndex > 0);
    }

    private static void applyRewardRow(
        @Nonnull UICommandBuilder cmd,
        @Nonnull String card,
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town
    ) {
        List<QuestReward> itemRewards = QuestBoardService.itemRewards(slot);
        QuestRewardService.ReputationRewardPreview repRw = QuestBoardService.firstReputationReward(slot);
        boolean hasItems = !itemRewards.isEmpty();
        boolean hasRep = repRw != null;

        cmd.clear(card + " #RewardItemsRow");
        if (hasItems) {
            int count = Math.min(itemRewards.size(), MAX_REWARD_ITEMS);
            int slotIndex = 0;
            for (int j = 0; j < count; j++) {
                QuestReward rw = itemRewards.get(j);
                ItemGridSlot gridSlot = AetherhavenUiItemGrids.slotForKnownItem(rw.itemId().trim(), rw.count());
                if (gridSlot == null) {
                    continue;
                }
                cmd.append(card + " #RewardItemsRow", "Aetherhaven/QuestBoardItemSlot.ui");
                String itemSel = card + " #RewardItemsRow[" + slotIndex + "]";
                slotIndex++;
                AetherhavenUiItemGrids.setSingleSlot(cmd, itemSel + " #ItemIcon", gridSlot);
            }
            cmd.set(card + " #RewardItemsRow.Visible", slotIndex > 0);
        } else {
            cmd.set(card + " #RewardItemsRow.Visible", false);
        }

        if (hasRep) {
            cmd.set(card + " #RewardReputationLine.Visible", true);
            String roleId = repRw.npcRoleId();
            if (roleId == null || roleId.isBlank()) {
                roleId = slot.getGiverRoleId();
            }
            Message villagerName =
                roleId != null && !roleId.isBlank()
                    ? Message.translation("aetherhaven_ui_journal_items_tail.npcRoles." + roleId.trim() + ".name")
                    : Message.raw(QuestBoardGiverDisplay.giverName(slot, store, town));
            cmd.set(
                card + " #RewardReputationLine.TextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.rewardReputationLine")
                    .param("amount", String.valueOf(repRw.amount()))
                    .param("villager", villagerName)
            );
        } else {
            cmd.set(card + " #RewardReputationLine.Visible", false);
            cmd.set(card + " #RewardReputationLine.TextSpans", Message.raw(""));
        }
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
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, ref, tm);
        if (town == null) {
            return;
        }
        QuestBoardCatalog catalog = plugin.getQuestBoardCatalog();
        int slotIndex = parseSlotIndex(data.slotIndex);

        if ("AcceptSlot".equalsIgnoreCase(data.action)) {
            QuestBoardSlotRecord slotBefore = town.getQuestBoardSlots().get(slotIndex);
            boolean wasRaidOffer = slotBefore.stateEnum() == QuestBoardSlotState.OFFER && slotBefore.isRaidQuest();
            boolean accepted = QuestBoardService.acceptOffer(town, uc.getUuid(), slotIndex, catalog, world, store);
            tm.updateTown(town);
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                if (accepted) {
                    pr.sendMessage(Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.acceptedToast"));
                } else if (wasRaidOffer) {
                    pr.sendMessage(Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.raidSpawnFailedToast"));
                }
            }
        } else if ("AbandonSlot".equalsIgnoreCase(data.action)) {
            boolean abandoned = QuestBoardService.abandonOffer(town, uc.getUuid(), slotIndex, catalog, world, store);
            tm.updateTown(town);
            PlayerRef abandonPr = store.getComponent(ref, PlayerRef.getComponentType());
            if (abandonPr != null) {
                if (abandoned) {
                    abandonPr.sendMessage(Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.abandonedToast"));
                } else {
                    abandonPr.sendMessage(Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.abandonFailedToast"));
                }
            }
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
