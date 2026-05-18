package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.dialogue.DialogueActionExecutor;
import com.hexvane.aetherhaven.guide.GuideMarkdownUiAppender;
import com.hexvane.aetherhaven.guide.GuideScheduleWeekAppender;
import com.hexvane.aetherhaven.guide.GuideTopicFile;
import com.hexvane.aetherhaven.guide.GuideTopicRepository;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.map.TownBorderMapOverlayService;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.config.PluginConfigMerge;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintChunkUtil;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownDissolutionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.google.gson.JsonObject;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestJournalPage extends AetherhavenInteractiveCustomUIPage<QuestJournalPage.PageData> {
    private static final String QUEST_ROWS = "#QuestsPage #QuestsSplit #QuestListPane #QuestRowList";
    private static final String GUIDE_TOPIC_ROWS = "#GuidePage #GuideSplit #GuideListPane #GuideTopicRowList";
    private static final String GUIDE_MD_ROWS = "#GuidePage #GuideSplit #GuideDetailPane #GuideMarkdownHost";
    private static final String GIFT_ROWS =
        "#GuidePage #GuideSplit #GuideDetailPane #GuideGiftListScrolling #GuideGiftRows";
    private static final String GUIDE_SCHEDULE_ROWS =
        "#GuidePage #GuideSplit #GuideDetailPane #GuideScheduleListScrolling #GuideScheduleRows";
    private static final String TOWN_VILLAGER_ROWS =
        "#TownPage #TownSplit #TownVillagerPane #TownVillagerScroll #TownVillagerRowList";
    private static final String TOWN_PLOT_ROWS =
        "#TownPage #TownSplit #TownPlotPane #TownPlotScroll #TownPlotRowList";
    private static final int MAX_ROWS = 24;
    private static final int MAX_TOWN_VILLAGERS = 24;
    private static final int MAX_TOWN_PLOTS = 32;
    private static final int MAX_GUIDE_TOPICS = 48;
    private static final int MAX_GUIDE_MD_ROWS = 96;
    /** Tier blocks (section label + item grid), including continuation chunks for long lists. */
    private static final int MAX_GUIDE_GIFT_BLOCKS = 48;
    /** Icons per grid chunk (same widget as town gift history). */
    private static final int MAX_ICONS_PER_GUIDE_GIFT_GRID = 400;

    private boolean templateAppended;
    @Nullable
    private String selectedQuestId;
    private boolean abandonConfirmOpen;
    @Nullable
    private String pendingAbandonQuestId;
    private boolean plotRemoveConfirmOpen;
    @Nullable
    private String pendingRemovePlotId;

    @Nonnull
    private String selectedGuideTopicId = "welcome";
    private boolean guideGiftSpoilerOpen;
    private boolean guideScheduleSpoilerOpen;
    /** Depth-1 section ids (for example mechanics, villagers) whose child topics are hidden in the sidebar. */
    @Nonnull
    private final Set<String> guideNavCollapsedSectionIds = new HashSet<>();
    private boolean journalSettingsPlotModalOpen;
    private boolean journalSettingsVillagerModalOpen;
    private boolean journalSettingsResetConfirmOpen;
    /**
     * When non-null, journal settings inputs are filled from this object so the player can preview defaults without
     * changing the live config until Save.
     */
    @Nullable
    private AetherhavenPluginConfig journalSettingsFormSnapshot;

    public QuestJournalPage(@Nonnull PlayerRef playerRef) {
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
            commandBuilder.append("Aetherhaven/QuestJournal.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyTownJournalStatic(commandBuilder);

        World world = store.getExternalData().getWorld();
        PlayerTownJournalState journalState = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        PlayerTownJournalState stateForTabs = journalState != null ? journalState : new PlayerTownJournalState();
        if (journalState == null) {
            scheduleEnsureJournalStateComponent(world);
        }
        boolean journalSettingsAllowed = JournalSettingsAccess.canOpen(store, ref);
        PlayerTownJournalState.JournalTab currentTab = stateForTabs.getLastTab();
        if (!journalSettingsAllowed && currentTab == PlayerTownJournalState.JournalTab.SETTINGS) {
            currentTab = PlayerTownJournalState.JournalTab.QUESTS;
            if (journalState != null) {
                scheduleCoerceJournalTabFromSettingsIfStillIllegal(world);
            }
        }
        if (!journalSettingsAllowed) {
            journalSettingsPlotModalOpen = false;
            journalSettingsVillagerModalOpen = false;
            journalSettingsResetConfirmOpen = false;
            journalSettingsFormSnapshot = null;
        }

        commandBuilder.set("#TabSettings.Visible", journalSettingsAllowed);

        commandBuilder.set("#TabTown.Disabled", currentTab == PlayerTownJournalState.JournalTab.TOWN);
        commandBuilder.set("#TabGuide.Disabled", currentTab == PlayerTownJournalState.JournalTab.GUIDE);
        commandBuilder.set("#TabQuests.Disabled", currentTab == PlayerTownJournalState.JournalTab.QUESTS);
        commandBuilder.set("#TabSettings.Disabled", currentTab == PlayerTownJournalState.JournalTab.SETTINGS);

        commandBuilder.set("#QuestsPage.Visible", currentTab == PlayerTownJournalState.JournalTab.QUESTS);
        commandBuilder.set("#TownPage.Visible", currentTab == PlayerTownJournalState.JournalTab.TOWN);
        commandBuilder.set("#GuidePage.Visible", currentTab == PlayerTownJournalState.JournalTab.GUIDE);
        commandBuilder.set("#SettingsPage.Visible", currentTab == PlayerTownJournalState.JournalTab.SETTINGS);

        commandBuilder.set(
            "#PageTitle.TextSpans",
            Message.translation(pageTitleKey(currentTab))
        );

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());

        boolean abandonModalBlocking = false;
        if (abandonConfirmOpen && pendingAbandonQuestId != null && plugin != null && uc != null) {
            TownRecord townModal = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            List<String> activeModal =
                townModal != null ? new ArrayList<>(townModal.getActiveQuestIdsSnapshot()) : List.of();
            if (townModal != null
                && townModal.playerCanAbandonQuests(uc.getUuid())
                && activeModal.contains(pendingAbandonQuestId)) {
                abandonModalBlocking = true;
            } else {
                abandonConfirmOpen = false;
                pendingAbandonQuestId = null;
            }
        }
        if (abandonModalBlocking) {
            plotRemoveConfirmOpen = false;
            pendingRemovePlotId = null;
            journalSettingsPlotModalOpen = false;
            journalSettingsVillagerModalOpen = false;
            journalSettingsResetConfirmOpen = false;
            journalSettingsFormSnapshot = null;
        }

        boolean plotModalBlocking = false;
        PlotInstance plotForRemoveModal = null;
        if (!abandonModalBlocking
            && plotRemoveConfirmOpen
            && pendingRemovePlotId != null
            && plugin != null
            && uc != null) {
            TownRecord townPlot = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            UUID plotUuid = tryParseUuid(pendingRemovePlotId);
            PlotInstance plotInst = townPlot != null && plotUuid != null ? townPlot.findPlotById(plotUuid) : null;
            if (townPlot != null
                && plotInst != null
                && townPlot.playerCanRemovePlots(uc.getUuid())) {
                plotModalBlocking = true;
                plotForRemoveModal = plotInst;
            } else {
                plotRemoveConfirmOpen = false;
                pendingRemovePlotId = null;
            }
        }
        if (plotModalBlocking) {
            journalSettingsPlotModalOpen = false;
            journalSettingsVillagerModalOpen = false;
            journalSettingsResetConfirmOpen = false;
            journalSettingsFormSnapshot = null;
        }

        boolean journalSettingsResetModalBlocking =
            !abandonModalBlocking
                && !plotModalBlocking
                && journalSettingsResetConfirmOpen
                && journalSettingsAllowed
                && currentTab == PlayerTownJournalState.JournalTab.SETTINGS;
        if (journalSettingsResetModalBlocking) {
            journalSettingsPlotModalOpen = false;
            journalSettingsVillagerModalOpen = false;
        }

        boolean journalPlotSettingsModalBlocking =
            !abandonModalBlocking
                && !plotModalBlocking
                && !journalSettingsResetModalBlocking
                && journalSettingsPlotModalOpen
                && journalSettingsAllowed;
        boolean journalVillagerReportModalBlocking =
            !abandonModalBlocking
                && !plotModalBlocking
                && !journalPlotSettingsModalBlocking
                && !journalSettingsResetModalBlocking
                && journalSettingsVillagerModalOpen
                && journalSettingsAllowed;

        commandBuilder.set("#JournalAbandonModal.Visible", abandonModalBlocking);
        commandBuilder.set("#JournalPlotRemoveModal.Visible", plotModalBlocking);
        commandBuilder.set("#JournalSettingsResetModal.Visible", journalSettingsResetModalBlocking);
        commandBuilder.set("#JournalSettingsPlotModal.Visible", journalPlotSettingsModalBlocking);
        commandBuilder.set("#JournalSettingsVillagerModal.Visible", journalVillagerReportModalBlocking);
        commandBuilder.set("#JournalPlotRemoveModalConfirm.Disabled", false);
        if (plotModalBlocking && plotForRemoveModal != null) {
            commandBuilder.set(
                "#JournalPlotRemoveModalConfirm.Disabled",
                !PlotFootprintChunkUtil.isPlotFullyLoaded(world, plotForRemoveModal)
            );
        }
        if (abandonModalBlocking) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JournalAbandonModalConfirm",
                new EventData().append("Action", "AbandonModalConfirm"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JournalAbandonModalCancel",
                new EventData().append("Action", "AbandonModalCancel"),
                false
            );
            return;
        }
        if (plotModalBlocking) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JournalPlotRemoveModalConfirm",
                new EventData().append("Action", "PlotRemoveModalConfirm"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JournalPlotRemoveModalCancel",
                new EventData().append("Action", "PlotRemoveModalCancel"),
                false
            );
            return;
        }
        if (journalSettingsResetModalBlocking) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JournalSettingsResetModalConfirm",
                new EventData().append("Action", "SettingsResetDefaultsConfirm"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JournalSettingsResetModalCancel",
                new EventData().append("Action", "SettingsResetDefaultsCancel"),
                false
            );
            return;
        }
        if (journalPlotSettingsModalBlocking) {
            wireJournalSettingsPlotModal(commandBuilder, eventBuilder, plugin, store, ref, world, uc);
            return;
        }
        if (journalVillagerReportModalBlocking) {
            wireJournalSettingsVillagerModal(commandBuilder, eventBuilder, plugin, store, ref, world, uc);
            return;
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabTown",
            new EventData().append("Action", "Tab").append("TabId", "TOWN"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabGuide",
            new EventData().append("Action", "Tab").append("TabId", "GUIDE"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabQuests",
            new EventData().append("Action", "Tab").append("TabId", "QUESTS"),
            false
        );
        if (journalSettingsAllowed) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabSettings",
                new EventData().append("Action", "Tab").append("TabId", "SETTINGS"),
                false
            );
        }

        commandBuilder.set("#TownShowBordersCheck #CheckBox.Value", stateForTabs.isShowTownBordersOnMap());
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#TownShowBordersCheck #CheckBox",
            new EventData()
                .append("Action", "TownShowBordersToggle")
                .append("@Checked", "#TownShowBordersCheck #CheckBox.Value"),
            false
        );

        if (currentTab != PlayerTownJournalState.JournalTab.QUESTS) {
            commandBuilder.clear(QUEST_ROWS);
            clearQuestDetailPane(commandBuilder);
        }
        if (currentTab != PlayerTownJournalState.JournalTab.GUIDE) {
            clearGuideTab(commandBuilder);
        }
        if (currentTab != PlayerTownJournalState.JournalTab.TOWN) {
            commandBuilder.clear(TOWN_VILLAGER_ROWS);
            commandBuilder.clear(TOWN_PLOT_ROWS);
            plotRemoveConfirmOpen = false;
            pendingRemovePlotId = null;
        }
        if (currentTab != PlayerTownJournalState.JournalTab.SETTINGS) {
            journalSettingsPlotModalOpen = false;
            journalSettingsVillagerModalOpen = false;
            journalSettingsResetConfirmOpen = false;
            journalSettingsFormSnapshot = null;
        }

        if (currentTab == PlayerTownJournalState.JournalTab.QUESTS) {
            if (plugin == null) {
                setQuestsBlocked(commandBuilder, Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
                return;
            }
            if (uc == null) {
                setQuestsBlocked(commandBuilder, Message.translation("aetherhaven_common.aetherhaven.common.noPlayerId"));
                return;
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null) {
                setQuestsBlocked(commandBuilder, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.needTown"));
                selectedQuestId = null;
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                setQuestsBlocked(commandBuilder, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.noPermission"));
                selectedQuestId = null;
                return;
            }

            List<String> active = new ArrayList<>(town.getActiveQuestIdsSnapshot());
            if (active.isEmpty()) {
                setQuestsBlocked(commandBuilder, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.noActive"));
                selectedQuestId = null;
                return;
            }

            commandBuilder.set("#QuestsBlocked.Visible", false);
            commandBuilder.set("#QuestsSplit.Visible", true);

            if (selectedQuestId == null || !active.contains(selectedQuestId)) {
                selectedQuestId = active.get(0);
            }

            QuestCatalog quests = plugin.getQuestCatalog();
            commandBuilder.clear(QUEST_ROWS);
            int n = Math.min(active.size(), MAX_ROWS);
            for (int i = 0; i < n; i++) {
                String qid = active.get(i);
                commandBuilder.append(QUEST_ROWS, "Aetherhaven/QuestJournalRow.ui");
                String row = QUEST_ROWS + "[" + i + "]";
                commandBuilder.set(row + " #Select #QuestTitle.TextSpans", Message.raw(quests.displayName(qid)));
                boolean sel = qid.equals(selectedQuestId);
                commandBuilder.set(row + " #QuestTitle.Style.TextColor", sel ? "#f4e8c8" : "#e8dcc8");
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #Select",
                    new EventData().append("Action", "Select").append("QuestId", qid),
                    false
                );
            }

            String sel = selectedQuestId != null ? selectedQuestId : active.get(0);
            commandBuilder.set("#QuestDetailTitle.TextSpans", Message.raw(quests.displayName(sel)));
            commandBuilder.set("#QuestDetailDescription.TextSpans", Message.raw(quests.description(sel)));

            String steps = quests.objectivesText(sel, town);
            boolean hasSteps = !steps.isEmpty();
            commandBuilder.set("#QuestStepsHeading.Visible", hasSteps);
            commandBuilder.set("#QuestStepsBody.Visible", hasSteps);
            if (hasSteps) {
                commandBuilder.set("#QuestStepsHeading.TextSpans", Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.stepsHeading"));
                commandBuilder.set("#QuestStepsBody.TextSpans", Message.raw(steps));
            } else {
                commandBuilder.set("#QuestStepsHeading.TextSpans", Message.raw(""));
                commandBuilder.set("#QuestStepsBody.TextSpans", Message.raw(""));
            }

            QuestCatalog.FirstItemReward itemRw = quests.firstItemReward(sel);
            if (itemRw != null) {
                commandBuilder.set("#RewardRow.Visible", true);
                commandBuilder.set("#RewardFallback.Visible", false);
                commandBuilder.set(
                    "#RewardSlot.Slots",
                    new ItemGridSlot[]{new ItemGridSlot(new ItemStack(itemRw.itemId(), itemRw.count()))}
                );
                commandBuilder.set("#RewardQuantity.TextSpans", Message.raw(String.valueOf(itemRw.count())));
                Item assetItem = Item.getAssetMap().getAsset(itemRw.itemId());
                if (assetItem != null
                    && assetItem.getTranslationKey() != null
                    && !assetItem.getTranslationKey().isBlank()) {
                    commandBuilder.set("#RewardTitle.TextSpans", Message.translation(assetItem.getTranslationKey()));
                } else {
                    commandBuilder.set("#RewardTitle.TextSpans", Message.raw(itemRw.itemId()));
                }
            } else {
                commandBuilder.set("#RewardRow.Visible", false);
                commandBuilder.set("#RewardFallback.Visible", true);
                commandBuilder.set(
                    "#RewardFallback.TextSpans",
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.rewardFallback")
                );
                commandBuilder.set("#RewardSlot.Slots", new ItemGridSlot[]{new ItemGridSlot()});
                commandBuilder.set("#RewardQuantity.TextSpans", Message.raw(""));
                commandBuilder.set("#RewardTitle.TextSpans", Message.raw(""));
            }

            boolean canAbandon = town.playerCanAbandonQuests(uc.getUuid());
            commandBuilder.set("#AbandonQuestButton.Visible", canAbandon);
            if (canAbandon) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#AbandonQuestButton",
                    new EventData().append("Action", "BeginAbandonConfirm"),
                    false
                );
            }
            return;
        }

        if (currentTab == PlayerTownJournalState.JournalTab.TOWN) {
            buildTownTab(commandBuilder, eventBuilder, plugin, store, ref, uc, world);
            return;
        }

        if (currentTab == PlayerTownJournalState.JournalTab.SETTINGS && journalSettingsAllowed) {
            buildJournalSettingsTab(commandBuilder, eventBuilder, plugin, store, ref, uc, world);
            return;
        }

        if (currentTab == PlayerTownJournalState.JournalTab.GUIDE) {
            buildGuideTab(commandBuilder, eventBuilder, plugin, store);
        }
    }

    private void wireJournalSettingsPlotModal(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull World world,
        @Nullable UUIDComponent uc
    ) {
        commandBuilder.set(
            "#JournalSettingsPlotModalTitle.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.plotModalTitle")
        );
        commandBuilder.set(
            "#JournalSettingsPlotModalHint.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.plotModalHint")
        );
        commandBuilder.set(
            "#JournalSettingsPlotFieldLabel.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.plotFieldLabel")
        );
        commandBuilder.set(
            "#JournalSettingsPlotModalConfirm.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.plotModalConfirm")
        );
        commandBuilder.set(
            "#JournalSettingsPlotModalCancel.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.plotModalCancel")
        );
        if (plugin == null || uc == null) {
            ObjectArrayList<DropdownEntryInfo> empty = new ObjectArrayList<>();
            empty.add(
                new DropdownEntryInfo(
                    LocalizableString.fromMessageId("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.plotPickNone"),
                    ""
                )
            );
            commandBuilder.set("#JournalSettingsPlotDropdown #Input.Entries", empty);
            commandBuilder.set("#JournalSettingsPlotDropdown #Input.Value", "");
            commandBuilder.set("#JournalSettingsPlotModalConfirm.Disabled", true);
        } else {
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
            entries.add(
                new DropdownEntryInfo(
                    LocalizableString.fromMessageId("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.plotPickNone"),
                    ""
                )
            );
            String firstValue = "";
            if (town != null) {
                ConstructionCatalog ccat = plugin.getConstructionCatalog();
                for (PlotInstance p : town.getPlotInstances()) {
                    if (p.getState() != PlotInstanceState.ASSEMBLING) {
                        continue;
                    }
                    String title = journalPlotConstructionTitle(ccat, p);
                    String label = title + "  " + p.getSignX() + " " + p.getSignY() + " " + p.getSignZ();
                    String v = p.getPlotId().toString();
                    if (firstValue.isEmpty()) {
                        firstValue = v;
                    }
                    entries.add(new DropdownEntryInfo(LocalizableString.fromString(label), v));
                }
            }
            commandBuilder.set("#JournalSettingsPlotDropdown #Input.Entries", entries);
            commandBuilder.set("#JournalSettingsPlotDropdown #Input.Value", firstValue);
            commandBuilder.set("#JournalSettingsPlotModalConfirm.Disabled", firstValue.isEmpty());
        }
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#JournalSettingsPlotModalConfirm",
            new EventData()
                .append("Action", "JournalPlotFinishConfirm")
                .append("@PlotPick", "#JournalSettingsPlotDropdown #Input.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#JournalSettingsPlotModalCancel",
            new EventData().append("Action", "JournalPlotFinishCancel"),
            false
        );
    }

    private void wireJournalSettingsVillagerModal(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull World world,
        @Nullable UUIDComponent uc
    ) {
        commandBuilder.set(
            "#JournalSettingsVillagerModalTitle.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.villagerModalTitle")
        );
        commandBuilder.set("#JournalSettingsVillagerReportField.Value", "");
        commandBuilder.set(
            "#JournalSettingsVillagerModalHint.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.villagerModalHint")
        );
        commandBuilder.set(
            "#JournalSettingsVillagerFieldLabel.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.villagerFieldLabel")
        );
        commandBuilder.set(
            "#JournalSettingsVillagerBuildReportButton.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.villagerBuildReport")
        );
        commandBuilder.set(
            "#JournalSettingsVillagerCopyHint.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.villagerCopyHint")
        );
        commandBuilder.set(
            "#JournalSettingsVillagerModalClose.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.villagerModalClose")
        );
        if (plugin == null || uc == null) {
            ObjectArrayList<DropdownEntryInfo> empty = new ObjectArrayList<>();
            empty.add(
                new DropdownEntryInfo(
                    LocalizableString.fromMessageId("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.villagerPickNone"),
                    ""
                )
            );
            commandBuilder.set("#JournalSettingsVillagerDropdown #Input.Entries", empty);
            commandBuilder.set("#JournalSettingsVillagerDropdown #Input.Value", "");
        } else {
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
            entries.add(
                new DropdownEntryInfo(
                    LocalizableString.fromMessageId("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.villagerPickNone"),
                    ""
                )
            );
            String first = "";
            if (town != null) {
                for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
                    if (first.isEmpty()) {
                        first = row.entityUuid().toString();
                    }
                    entries.add(new DropdownEntryInfo(LocalizableString.fromString(row.label()), row.entityUuid().toString()));
                }
            }
            commandBuilder.set("#JournalSettingsVillagerDropdown #Input.Entries", entries);
            commandBuilder.set("#JournalSettingsVillagerDropdown #Input.Value", first);
        }
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#JournalSettingsVillagerBuildReportButton",
            new EventData()
                .append("Action", "JournalVillagerReportBuild")
                .append("@VillagerPick", "#JournalSettingsVillagerDropdown #Input.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#JournalSettingsVillagerModalClose",
            new EventData().append("Action", "JournalVillagerReportClose"),
            false
        );
    }

    private void buildJournalSettingsTab(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nullable UUIDComponent uc,
        @Nonnull World world
    ) {
        commandBuilder.set("#SettingsStatus.TextSpans", Message.raw(""));
        if (plugin == null) {
            commandBuilder.set("#SettingsSaveButton.Disabled", true);
            commandBuilder.set("#SettingsResetDefaultsButton.Disabled", true);
            commandBuilder.set("#SettingsToolsRow1.Visible", false);
            commandBuilder.set("#SettingsToolsRow2.Visible", false);
            commandBuilder.set("#SettingsToolsBlocked.Visible", true);
            commandBuilder.set(
                "#SettingsToolsBlocked.TextSpans",
                Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded")
            );
            return;
        }
        AetherhavenPluginConfig cfg =
            journalSettingsFormSnapshot != null ? journalSettingsFormSnapshot : plugin.getConfig().get();
        commandBuilder.set("#SettingsSaveButton.Disabled", false);
        commandBuilder.set("#SettingsResetDefaultsButton.Disabled", false);
        commandBuilder.set("#SettingsPassiveCheck #CheckBox.Value", cfg.isPassivePlotAssemblyEnabled());
        commandBuilder.set("#SettingsConstrBptField.Value", String.valueOf(cfg.getConstructionBlocksPerTick()));
        commandBuilder.set("#SettingsConstrMsField.Value", String.valueOf(cfg.getConstructionMinIntervalMs()));
        commandBuilder.set("#SettingsGeodeField.Value", String.format(Locale.US, "%.4f", cfg.getGeodeDropChancePerOreBreak()));
        commandBuilder.set("#SettingsChestJewelryField.Value", String.format(Locale.US, "%.3f", cfg.getLootChestJewelryChance()));
        commandBuilder.set("#SettingsGoldChanceField.Value", String.format(Locale.US, "%.3f", cfg.getLootChestGoldCoinChance()));
        commandBuilder.set("#SettingsGoldMinField.Value", String.valueOf(cfg.getLootChestGoldCoinMin()));
        commandBuilder.set("#SettingsGoldMaxField.Value", String.valueOf(cfg.getLootChestGoldCoinMax()));
        commandBuilder.set(
            "#SettingsBreakableWeightNoneField.Value",
            String.valueOf(cfg.getBreakableContainers().getGold().getWeightNone())
        );
        commandBuilder.set(
            "#SettingsBreakableWeightOneField.Value",
            String.valueOf(cfg.getBreakableContainers().getGold().getWeightOne())
        );
        commandBuilder.set(
            "#SettingsBreakableWeightTwoField.Value",
            String.valueOf(cfg.getBreakableContainers().getGold().getWeightTwo())
        );
        commandBuilder.set("#SettingsGiftEnabledCheck #CheckBox.Value", cfg.isFloatingGiftEnabled());
        commandBuilder.set(
            "#SettingsGiftDaysMinField.Value",
            String.format(Locale.US, "%.2f", cfg.getFloatingGiftSpawnIntervalDaysMin())
        );
        commandBuilder.set(
            "#SettingsGiftDaysMaxField.Value",
            String.format(Locale.US, "%.2f", cfg.getFloatingGiftSpawnIntervalDaysMax())
        );

        TownRecord town = uc != null ? AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid()) : null;
        boolean tools = town != null;
        commandBuilder.set("#SettingsToolsRow1.Visible", tools);
        commandBuilder.set("#SettingsToolsRow2.Visible", tools);
        commandBuilder.set("#SettingsToolsBlocked.Visible", !tools);
        if (!tools) {
            commandBuilder.set(
                "#SettingsToolsBlocked.TextSpans",
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.needTown")
            );
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SettingsSaveButton",
            new EventData()
                .append("Action", "SettingsSave")
                .append("@Passive", "#SettingsPassiveCheck #CheckBox.Value")
                .append("@ConstrBpt", "#SettingsConstrBptField.Value")
                .append("@ConstrMs", "#SettingsConstrMsField.Value")
                .append("@Geode", "#SettingsGeodeField.Value")
                .append("@ChestJewel", "#SettingsChestJewelryField.Value")
                .append("@GoldCh", "#SettingsGoldChanceField.Value")
                .append("@GoldMin", "#SettingsGoldMinField.Value")
                .append("@GoldMax", "#SettingsGoldMaxField.Value")
                .append("@BreakW0", "#SettingsBreakableWeightNoneField.Value")
                .append("@BreakW1", "#SettingsBreakableWeightOneField.Value")
                .append("@BreakW2", "#SettingsBreakableWeightTwoField.Value")
                .append("@GiftEn", "#SettingsGiftEnabledCheck #CheckBox.Value")
                .append("@GiftMinDays", "#SettingsGiftDaysMinField.Value")
                .append("@GiftMaxDays", "#SettingsGiftDaysMaxField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SettingsResetDefaultsButton",
            new EventData().append("Action", "SettingsResetDefaultsOpen"),
            false
        );
        if (tools) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SettingsResetVillagersButton",
                new EventData().append("Action", "JournalResetVillagers"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SettingsFixInnButton",
                new EventData().append("Action", "JournalFixInn"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SettingsFinishPlotButton",
                new EventData().append("Action", "JournalOpenPlotFinishModal"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SettingsVillagerReportButton",
                new EventData().append("Action", "JournalOpenVillagerReportModal"),
                false
            );
        }
    }

    private void buildTownTab(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nullable UUIDComponent uc,
        @Nonnull World world
    ) {
        if (plugin == null) {
            setTownTabBlocked(commandBuilder, Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        if (uc == null) {
            setTownTabBlocked(commandBuilder, Message.translation("aetherhaven_common.aetherhaven.common.noPlayerId"));
            return;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
        if (town == null) {
            setTownTabBlocked(commandBuilder, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.needTown"));
            return;
        }
        commandBuilder.set("#TownBlocked.Visible", false);
        commandBuilder.set("#TownSplit.Visible", true);

        commandBuilder.clear(TOWN_VILLAGER_ROWS);
        List<TownVillagerRow> villagers = TownVillagerDirectory.listResidents(store, town);
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        LocalDateTime gameNow = wtr != null ? wtr.getGameDateTime() : null;
        int nv = Math.min(villagers.size(), MAX_TOWN_VILLAGERS);
        for (int i = 0; i < nv; i++) {
            TownVillagerRow r = villagers.get(i);
            commandBuilder.append(TOWN_VILLAGER_ROWS, "Aetherhaven/TownJournalVillagerRow.ui");
            String row = TOWN_VILLAGER_ROWS + "[" + i + "]";
            commandBuilder.set(row + " #Portrait.AssetPath", NpcPortraitProvider.portraitPathForRoleId(r.roleId()));
            commandBuilder.set(
                row + " #VillagerName.TextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.npcRoles." + r.roleId() + ".name")
            );
            String heartsPath = row + " #ReputationHeartSlots";
            for (int h = 0; h < 10; h++) {
                commandBuilder.append(heartsPath, "Aetherhaven/HeartSlot.ui");
            }
            int rep = VillagerReputationService.getOrCreateEntry(town, uc.getUuid(), r.entityUuid()).getReputation();
            ReputationHeartUi.applyHearts(commandBuilder, heartsPath, rep);
            commandBuilder.set(row + " #ScheduleLocation.TextSpans", scheduleLocationMessage(plugin, r.roleId(), gameNow));
        }

        commandBuilder.clear(TOWN_PLOT_ROWS);
        List<PlotInstance> plots = new ArrayList<>(town.getPlotInstances());
        boolean canRemovePlots = town.playerCanRemovePlots(uc.getUuid());
        ConstructionCatalog plotCatalog = plugin.getConstructionCatalog();
        int np = Math.min(plots.size(), MAX_TOWN_PLOTS);
        for (int i = 0; i < np; i++) {
            PlotInstance p = plots.get(i);
            commandBuilder.append(TOWN_PLOT_ROWS, "Aetherhaven/TownJournalPlotRow.ui");
            String row = TOWN_PLOT_ROWS + "[" + i + "]";
            String title = journalPlotConstructionTitle(plotCatalog, p);
            commandBuilder.set(row + " #PlotTitle.TextSpans", Message.raw(title));
            String coords = p.getSignX() + " " + p.getSignY() + " " + p.getSignZ();
            commandBuilder.set(row + " #PlotCoords.TextSpans", Message.raw(coords));
            PlotInstanceState pst = p.getState();
            commandBuilder.set(row + " #PlotStatus.TextSpans", Message.translation(plotStatusLangKey(pst)));
            String tokenId = journalPlotTokenItemId(plotCatalog, p);
            if (tokenId != null && !tokenId.isBlank()) {
                AetherhavenUiItemGrids.setSingleSlot(
                    commandBuilder, row + " #PlotTokenSlot", new ItemStack(tokenId.trim(), 1));
            } else {
                AetherhavenUiItemGrids.setSingleSlotEmpty(commandBuilder, row + " #PlotTokenSlot");
            }
            boolean areaLoaded = PlotFootprintChunkUtil.isPlotFullyLoaded(world, p);
            commandBuilder.set(row + " #RemovePlot.Visible", canRemovePlots);
            commandBuilder.set(
                row + " #RemovePlot.TooltipTextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.removePlotTooltip")
            );
            if (canRemovePlots) {
                commandBuilder.set(row + " #RemovePlot.Disabled", !areaLoaded);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #RemovePlot",
                    new EventData().append("Action", "BeginPlotRemoveConfirm").append("PlotId", p.getPlotId().toString()),
                    false
                );
            }
        }
    }

    @Nonnull
    private static String journalPlotConstructionTitle(@Nonnull ConstructionCatalog catalog, @Nonnull PlotInstance plot) {
        String stored = plot.getConstructionId();
        if (stored == null || stored.isBlank()) {
            return "?";
        }
        String t = stored.trim();
        ConstructionDefinition byStored = catalog.get(t);
        if (byStored != null) {
            return byStored.getDisplayName();
        }
        String gameplay = catalog.resolveGameplayConstructionId(t);
        ConstructionDefinition byGameplay = catalog.get(gameplay);
        return byGameplay != null ? byGameplay.getDisplayName() : t;
    }

    @Nullable
    private static String journalPlotTokenItemId(@Nonnull ConstructionCatalog catalog, @Nonnull PlotInstance plot) {
        String stored = plot.getConstructionId();
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String t = stored.trim();
        ConstructionDefinition byStored = catalog.get(t);
        if (byStored != null) {
            String tok = byStored.getPlotTokenItemId();
            if (tok != null && !tok.isBlank()) {
                return tok.trim();
            }
        }
        String gameplay = catalog.resolveGameplayConstructionId(t);
        ConstructionDefinition byGameplay = catalog.get(gameplay);
        if (byGameplay == null) {
            return null;
        }
        String tok = byGameplay.getPlotTokenItemId();
        return tok != null && !tok.isBlank() ? tok.trim() : null;
    }

    private static void setTownTabBlocked(@Nonnull UICommandBuilder commandBuilder, @Nonnull Message msg) {
        commandBuilder.set("#TownBlocked.Visible", true);
        commandBuilder.set("#TownBlocked.TextSpans", msg);
        commandBuilder.set("#TownSplit.Visible", false);
        commandBuilder.clear(TOWN_VILLAGER_ROWS);
        commandBuilder.clear(TOWN_PLOT_ROWS);
    }

    @Nonnull
    private static Message scheduleLocationMessage(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String npcRoleId,
        @Nullable LocalDateTime gameNow
    ) {
        if (gameNow == null) {
            return Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleUnknown");
        }
        VillagerScheduleDefinition sched =
            plugin.getVillagerDefinitionCatalog().effectiveSchedule(npcRoleId, plugin.getVillagerScheduleRegistry());
        if (sched == null || sched.getTransitions().isEmpty()) {
            return Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleUnknown");
        }
        String sym = VillagerScheduleResolver.activeLocationSymbol(sched, gameNow);
        if (sym == null || sym.isBlank()) {
            return Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleUnknown");
        }
        return switch (sym.trim().toLowerCase()) {
            case VillagerScheduleResolver.LOC_HOME ->
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleHome");
            case VillagerScheduleResolver.LOC_WORK ->
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleWork");
            case VillagerScheduleResolver.LOC_INN ->
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleInn");
            case VillagerScheduleResolver.LOC_PARK ->
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.schedulePark");
            case VillagerScheduleResolver.LOC_GAIA_ALTAR ->
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleAltar");
            default -> Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleUnknown");
        };
    }

    @Nonnull
    private static String plotStatusLangKey(@Nullable PlotInstanceState state) {
        if (state == null) {
            return "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.plotStatusUnknown";
        }
        return switch (state) {
            case BLUEPRINTING -> "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.plotStatusNotStarted";
            case ASSEMBLING -> "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.plotStatusInProgress";
            case COMPLETE -> "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.plotStatusComplete";
        };
    }

    @Nullable
    private static UUID tryParseUuid(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isGuideNavSectionWithChildren(@Nonnull GuideTopicFile topic, int depth) {
        return depth == 1 && !topic.subTopicIds().isEmpty();
    }

    @Nonnull
    private static List<GuideTopicRepository.GuideNavEntry> filterCollapsedGuideNav(
        @Nonnull List<GuideTopicRepository.GuideNavEntry> full,
        @Nonnull Set<String> collapsedDepth1SectionIds
    ) {
        List<GuideTopicRepository.GuideNavEntry> out = new ArrayList<>();
        String depth1Parent = null;
        for (GuideTopicRepository.GuideNavEntry e : full) {
            int d = e.depth();
            if (d == 0) {
                depth1Parent = null;
                out.add(e);
            } else if (d == 1) {
                depth1Parent = e.topicId();
                out.add(e);
            } else {
                if (depth1Parent == null || !collapsedDepth1SectionIds.contains(depth1Parent)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    private static void clearGuideTab(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.clear(GUIDE_TOPIC_ROWS);
        commandBuilder.clear(GUIDE_MD_ROWS);
        commandBuilder.clear(GIFT_ROWS);
        commandBuilder.clear(GUIDE_SCHEDULE_ROWS);
        commandBuilder.set("#GuidePluginMissing.Visible", false);
        commandBuilder.set("#GuideSplit.Visible", true);
        commandBuilder.set("#GuideGiftBlock.Visible", false);
        commandBuilder.set("#GuideGiftListScrolling.Visible", false);
        commandBuilder.set("#GuideScheduleBlock.Visible", false);
        commandBuilder.set("#GuideScheduleListScrolling.Visible", false);
    }

    private void buildGuideTab(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store
    ) {
        if (plugin == null) {
            commandBuilder.set("#GuidePluginMissing.Visible", true);
            commandBuilder.set("#GuideSplit.Visible", false);
            commandBuilder.set(
                "#GuidePluginMissing.TextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.pluginMissing")
            );
            return;
        }
        commandBuilder.set("#GuidePluginMissing.Visible", false);
        commandBuilder.set("#GuideSplit.Visible", true);

        GuideTopicRepository repo = GuideTopicRepository.get(plugin.getClass().getClassLoader());
        boolean idOk = false;
        for (GuideTopicRepository.GuideNavEntry e : repo.navEntries()) {
            if (e.topicId().equals(selectedGuideTopicId)) {
                idOk = true;
                break;
            }
        }
        if (!idOk) {
            selectedGuideTopicId = "welcome";
        }
        GuideTopicFile topic = repo.byId(selectedGuideTopicId);
        if (topic == null) {
            selectedGuideTopicId = "welcome";
            topic = repo.byId("welcome");
        }

        commandBuilder.clear(GUIDE_TOPIC_ROWS);
        commandBuilder.clear(GUIDE_MD_ROWS);
        commandBuilder.clear(GIFT_ROWS);
        commandBuilder.clear(GUIDE_SCHEDULE_ROWS);

        List<GuideTopicRepository.GuideNavEntry> nav =
            filterCollapsedGuideNav(repo.navEntries(), guideNavCollapsedSectionIds);
        int nt = Math.min(nav.size(), MAX_GUIDE_TOPICS);
        for (int i = 0; i < nt; i++) {
            GuideTopicRepository.GuideNavEntry e = nav.get(i);
            GuideTopicFile navTopic = repo.byId(e.topicId());
            if (navTopic == null) {
                navTopic = GuideTopicFile.missing(e.topicId());
            }
            commandBuilder.append(GUIDE_TOPIC_ROWS, "Aetherhaven/GuideTopicRow.ui");
            String row = GUIDE_TOPIC_ROWS + "[" + i + "]";
            String indent = "  ".repeat(Math.max(0, e.depth()));
            commandBuilder.set(row + " #Select #TopicTitle.TextSpans", Message.raw(indent + e.title()));
            boolean sel = e.topicId().equals(selectedGuideTopicId);
            int depth = e.depth();
            String titleStyle = row + " #Select #TopicTitle.Style";
            commandBuilder.set(titleStyle + ".FontSize", 13);
            commandBuilder.set(titleStyle + ".RenderBold", true);
            // Depth 0 = welcome; depth 1 = main sections (Mechanics, Villagers). Deeper = leaf subpages — muted only.
            if (depth <= 1) {
                commandBuilder.set(titleStyle + ".TextColor", sel ? "#f4e8c8" : "#e8dcc8");
            } else {
                commandBuilder.set(titleStyle + ".TextColor", sel ? "#d4c8b8" : "#9a9286");
            }
            boolean showChevron = isGuideNavSectionWithChildren(navTopic, e.depth());
            commandBuilder.set(row + " #ExpandToggleHost.Visible", showChevron);
            if (showChevron) {
                boolean collapsed = guideNavCollapsedSectionIds.contains(e.topicId());
                commandBuilder.set(row + " #ExpandToggleCollapsed.Visible", collapsed);
                commandBuilder.set(row + " #ExpandToggleExpanded.Visible", !collapsed);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #ExpandToggleCollapsed",
                    new EventData().append("Action", "GuideNavToggle").append("GuideNavSectionId", e.topicId()),
                    false
                );
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #ExpandToggleExpanded",
                    new EventData().append("Action", "GuideNavToggle").append("GuideNavSectionId", e.topicId()),
                    false
                );
            }
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                new EventData().append("Action", "GuideTopic").append("GuideTopicId", e.topicId()),
                false
            );
        }

        GuideMarkdownUiAppender.appendMarkdown(
            commandBuilder,
            GUIDE_MD_ROWS,
            topic.markdownBody(),
            plugin.getClass().getClassLoader(),
            topic.npcRoleId(),
            MAX_GUIDE_MD_ROWS
        );

        String npcRoleId = topic.npcRoleId();
        VillagerDefinition vdef =
            npcRoleId != null && !npcRoleId.isBlank() ? plugin.getVillagerDefinitionCatalog().byNpcRoleId(npcRoleId) : null;

        if (npcRoleId != null && !npcRoleId.isBlank() && vdef != null) {
            // Same resolution as gameplay: embedded weeklySchedule in villager JSON, else VillagerSchedules/*.json
            // (see VillagerDefinitionCatalog.effectiveSchedule).
            VillagerScheduleDefinition wsched =
                plugin.getVillagerDefinitionCatalog().effectiveSchedule(npcRoleId, plugin.getVillagerScheduleRegistry());
            boolean hasSched = wsched != null && !wsched.getTransitions().isEmpty();
            if (hasSched) {
                commandBuilder.set("#GuideScheduleBlock.Visible", true);
                commandBuilder.set(
                    "#GuideScheduleToggleButton.TextSpans",
                    Message.translation(
                        guideScheduleSpoilerOpen
                            ? "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.scheduleToggleHide"
                            : "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.scheduleToggleShow"
                    )
                );
                commandBuilder.set("#GuideScheduleListScrolling.Visible", guideScheduleSpoilerOpen);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#GuideScheduleToggleButton",
                    new EventData().append("Action", "GuideScheduleToggle"),
                    false
                );
                if (guideScheduleSpoilerOpen) {
                    // Same wall clock as villager schedules: WorldTimeResource#getGameDateTime() (UTC calendar).
                    WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
                    LocalDateTime gameNow = wtr != null ? wtr.getGameDateTime() : null;
                    GuideScheduleWeekAppender.appendWeek(commandBuilder, GUIDE_SCHEDULE_ROWS, wsched, gameNow);
                }
            } else {
                commandBuilder.set("#GuideScheduleBlock.Visible", false);
                commandBuilder.set("#GuideScheduleListScrolling.Visible", false);
            }

            int giftCount =
                vdef.getGiftLoves().size() + vdef.getGiftLikes().size() + vdef.getGiftDislikes().size();
            if (giftCount > 0) {
                commandBuilder.set("#GuideGiftBlock.Visible", true);
                commandBuilder.set(
                    "#GuideGiftToggleButton.TextSpans",
                    Message.translation(
                        guideGiftSpoilerOpen
                            ? "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.giftToggleHide"
                            : "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.giftToggleShow"
                    )
                );
                commandBuilder.set("#GuideGiftListScrolling.Visible", guideGiftSpoilerOpen);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#GuideGiftToggleButton",
                    new EventData().append("Action", "GuideGiftToggle"),
                    false
                );
                if (guideGiftSpoilerOpen) {
                    int gi = 0;
                    gi = appendGuideGiftTierSections(
                        commandBuilder, gi, "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.giftHeaderLoves", vdef.getGiftLoves());
                    gi = appendGuideGiftTierSections(
                        commandBuilder, gi, "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.giftHeaderLikes", vdef.getGiftLikes());
                    appendGuideGiftTierSections(
                        commandBuilder, gi, "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.giftHeaderDislikes", vdef.getGiftDislikes());
                }
            } else {
                commandBuilder.set("#GuideGiftBlock.Visible", false);
                commandBuilder.set("#GuideGiftListScrolling.Visible", false);
            }
        } else {
            commandBuilder.set("#GuideGiftBlock.Visible", false);
            commandBuilder.set("#GuideGiftListScrolling.Visible", false);
            commandBuilder.set("#GuideScheduleBlock.Visible", false);
            commandBuilder.set("#GuideScheduleListScrolling.Visible", false);
        }
    }

    /**
     * Appends the same section header plus wrapping item grid as the town gift history page, using every gift item
     * id from the villager definition (not only gifts the player has tried). Long tiers are split into extra grids
     * without repeating the section title.
     */
    private int appendGuideGiftTierSections(
        @Nonnull UICommandBuilder commandBuilder,
        int startBlockIndex,
        @Nonnull String sectionLangKey,
        @Nonnull List<String> itemIds
    ) {
        if (itemIds.isEmpty()) {
            return startBlockIndex;
        }
        List<String> sorted = new ArrayList<>(itemIds.size());
        for (String id : itemIds) {
            if (id != null && !id.isBlank()) {
                sorted.add(id.trim());
            }
        }
        if (sorted.isEmpty()) {
            return startBlockIndex;
        }
        Collections.sort(sorted);
        int bi = startBlockIndex;
        for (int off = 0; off < sorted.size(); off += MAX_ICONS_PER_GUIDE_GIFT_GRID) {
            if (bi >= MAX_GUIDE_GIFT_BLOCKS) {
                break;
            }
            int n = Math.min(MAX_ICONS_PER_GUIDE_GIFT_GRID, sorted.size() - off);
            commandBuilder.append(GIFT_ROWS, "Aetherhaven/VillagerGiftHistoryTierBlock.ui");
            String block = GIFT_ROWS + "[" + bi + "]";
            boolean showSectionTitle = off == 0;
            commandBuilder.set(
                block + " #Section.TextSpans",
                showSectionTitle ? Message.translation(sectionLangKey) : Message.raw("")
            );
            commandBuilder.set(block + " #Section.Visible", showSectionTitle);
            ItemGridSlot[] gridSlots = new ItemGridSlot[n];
            for (int i = 0; i < n; i++) {
                gridSlots[i] = new ItemGridSlot(new ItemStack(sorted.get(off + i), 1));
            }
            AetherhavenUiItemGrids.setSlots(commandBuilder, block + " #IconGrid", gridSlots);
            bi++;
        }
        return bi;
    }

    private static void setQuestsBlocked(@Nonnull UICommandBuilder commandBuilder, @Nonnull Message msg) {
        commandBuilder.set("#QuestsBlocked.Visible", true);
        commandBuilder.set("#QuestsBlocked.TextSpans", msg);
        commandBuilder.set("#QuestsSplit.Visible", false);
        commandBuilder.set("#AbandonQuestButton.Visible", false);
        clearQuestDetailPane(commandBuilder);
    }

    private static void clearQuestDetailPane(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set("#QuestDetailTitle.TextSpans", Message.raw(""));
        commandBuilder.set("#QuestDetailDescription.TextSpans", Message.raw(""));
        commandBuilder.set("#QuestStepsHeading.Visible", false);
        commandBuilder.set("#QuestStepsBody.Visible", false);
        commandBuilder.set("#QuestStepsHeading.TextSpans", Message.raw(""));
        commandBuilder.set("#QuestStepsBody.TextSpans", Message.raw(""));
        commandBuilder.set("#RewardRow.Visible", false);
        commandBuilder.set("#RewardFallback.Visible", false);
        commandBuilder.set("#RewardSlot.Slots", new ItemGridSlot[]{new ItemGridSlot()});
        commandBuilder.set("#RewardQuantity.TextSpans", Message.raw(""));
        commandBuilder.set("#RewardTitle.TextSpans", Message.raw(""));
    }

    @Nonnull
    private static String pageTitleKey(@Nonnull PlayerTownJournalState.JournalTab tab) {
        return switch (tab) {
            case TOWN -> "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.pageTitle.town";
            case GUIDE -> "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.pageTitle.guide";
            case QUESTS -> "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.pageTitle.quests";
            case SETTINGS -> "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.pageTitle.settings";
        };
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        String action = data.action;
        if (action == null) {
            return;
        }
        if (action.equalsIgnoreCase("TownShowBordersToggle")) {
            if (data.checked == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
            }
            st.setShowTownBordersOnMap(data.checked);
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            World world = store.getExternalData().getWorld();
            TownBorderMapOverlayService.refreshPlayer(world, uc.getUuid());
            return;
        }
        if (action.equalsIgnoreCase("Tab")) {
            String tabId = data.tabId;
            PlayerTownJournalState.JournalTab tab = parseTab(tabId);
            if (tab == PlayerTownJournalState.JournalTab.SETTINGS && !JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
                store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            }
            st.setLastTab(tab);
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            abandonConfirmOpen = false;
            pendingAbandonQuestId = null;
            plotRemoveConfirmOpen = false;
            pendingRemovePlotId = null;
            journalSettingsPlotModalOpen = false;
            journalSettingsVillagerModalOpen = false;
            journalSettingsResetConfirmOpen = false;
            journalSettingsFormSnapshot = null;
            if (tab != PlayerTownJournalState.JournalTab.GUIDE) {
                guideGiftSpoilerOpen = false;
                guideScheduleSpoilerOpen = false;
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("Select")) {
            String qid = data.questId;
            if (qid != null && !qid.isBlank()) {
                selectedQuestId = qid.trim();
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("GuideTopic")) {
            String tid = data.guideTopicId;
            if (tid != null && !tid.isBlank()) {
                selectedGuideTopicId = tid.trim();
                guideGiftSpoilerOpen = false;
                guideScheduleSpoilerOpen = false;
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("GuideGiftToggle")) {
            guideGiftSpoilerOpen = !guideGiftSpoilerOpen;
            if (guideGiftSpoilerOpen) {
                guideScheduleSpoilerOpen = false;
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("GuideScheduleToggle")) {
            guideScheduleSpoilerOpen = !guideScheduleSpoilerOpen;
            if (guideScheduleSpoilerOpen) {
                guideGiftSpoilerOpen = false;
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("GuideNavToggle")) {
            String sid = data.guideNavSectionId;
            if (sid != null && !sid.isBlank()) {
                String key = sid.trim();
                if (!guideNavCollapsedSectionIds.remove(key)) {
                    guideNavCollapsedSectionIds.add(key);
                }
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("BeginAbandonConfirm")) {
            if (selectedQuestId == null || selectedQuestId.isBlank()) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null || !town.playerCanAbandonQuests(uc.getUuid())) {
                return;
            }
            if (!town.getActiveQuestIdsSnapshot().contains(selectedQuestId)) {
                return;
            }
            pendingAbandonQuestId = selectedQuestId;
            abandonConfirmOpen = true;
            plotRemoveConfirmOpen = false;
            pendingRemovePlotId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("AbandonModalCancel")) {
            abandonConfirmOpen = false;
            pendingAbandonQuestId = null;
            plotRemoveConfirmOpen = false;
            pendingRemovePlotId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("BeginPlotRemoveConfirm")) {
            String pid = data.plotId;
            if (pid == null || pid.isBlank()) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            UUID plotUuid = tryParseUuid(pid);
            if (town == null || plotUuid == null || town.findPlotById(plotUuid) == null || !town.playerCanRemovePlots(uc.getUuid())) {
                return;
            }
            pendingRemovePlotId = pid.trim();
            plotRemoveConfirmOpen = true;
            abandonConfirmOpen = false;
            pendingAbandonQuestId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("PlotRemoveModalCancel")) {
            plotRemoveConfirmOpen = false;
            pendingRemovePlotId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("PlotRemoveModalConfirm")) {
            String pid = pendingRemovePlotId;
            if (pid == null || pid.isBlank()) {
                plotRemoveConfirmOpen = false;
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
            UUID plotUuid = tryParseUuid(pid);
            PlotInstance plot = town != null && plotUuid != null ? town.findPlotById(plotUuid) : null;
            if (town == null || plot == null || !town.playerCanRemovePlots(uc.getUuid())) {
                plotRemoveConfirmOpen = false;
                pendingRemovePlotId = null;
                return;
            }
            if (!PlotFootprintChunkUtil.isPlotFullyLoaded(world, plot)) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.removePlotAreaNotLoaded")
                );
                plotRemoveConfirmOpen = false;
                pendingRemovePlotId = null;
                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder ev = new UIEventBuilder();
                build(ref, cmd, ev, store);
                sendUpdate(cmd, ev, false);
                return;
            }
            PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            TownDissolutionService.clearPlotFromWorld(world, plugin, town, plot, store, reg);
            if (!town.removePlotInstance(plotUuid)) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.removePlotFailed")
                );
                plotRemoveConfirmOpen = false;
                pendingRemovePlotId = null;
                return;
            }
            tm.updateTown(town);
            plotRemoveConfirmOpen = false;
            pendingRemovePlotId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("SettingsSave")) {
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            AetherhavenPluginConfig cfg = plugin.getConfig().get();
            AetherhavenPluginConfig parseSrc =
                journalSettingsFormSnapshot != null ? journalSettingsFormSnapshot : cfg;
            Boolean passive = data.passive;
            int constrBpt = parseIntSafe(data.constrBpt, 1, 9999, parseSrc.getConstructionBlocksPerTick());
            long constrMs = parseLongSafe(data.constrMs, 0L, 1_000_000L, parseSrc.getConstructionMinIntervalMs());
            double geode = parseDoubleSafe(data.geodeChance, 0.0, 1.0, parseSrc.getGeodeDropChancePerOreBreak());
            double chestJewel = parseDoubleSafe(data.chestJewel, 0.0, 1.0, parseSrc.getLootChestJewelryChance());
            double goldCh = parseDoubleSafe(data.goldCh, 0.0, 1.0, parseSrc.getLootChestGoldCoinChance());
            int goldMinDef = parseSrc.getLootChestGoldCoinMin();
            int goldMaxDef = parseSrc.getLootChestGoldCoinMax();
            int goldMin = parseIntSafe(data.goldMin, 1, 10_000, goldMinDef);
            int goldMax = parseIntSafe(data.goldMax, 1, 10_000, goldMaxDef);
            if (goldMax < goldMin) {
                goldMax = goldMin;
            }
            int breakW0Def = parseSrc.getBreakableContainers().getGold().getWeightNone();
            int breakW1Def = parseSrc.getBreakableContainers().getGold().getWeightOne();
            int breakW2Def = parseSrc.getBreakableContainers().getGold().getWeightTwo();
            int breakW0 = parseIntSafe(data.breakW0, 0, 10_000, breakW0Def);
            int breakW1 = parseIntSafe(data.breakW1, 0, 10_000, breakW1Def);
            int breakW2 = parseIntSafe(data.breakW2, 0, 10_000, breakW2Def);
            double giftMinDays = parseDoubleSafe(
                data.giftMinDays,
                0.1,
                10_000.0,
                parseSrc.getFloatingGiftSpawnIntervalDaysMin()
            );
            double giftMaxDays = parseDoubleSafe(
                data.giftMaxDays,
                0.1,
                10_000.0,
                parseSrc.getFloatingGiftSpawnIntervalDaysMax()
            );
            if (giftMaxDays < giftMinDays) {
                giftMaxDays = giftMinDays;
            }
            boolean floatingOn =
                data.giftEn != null ? data.giftEn.booleanValue() : parseSrc.isFloatingGiftEnabled();
            if (journalSettingsFormSnapshot != null) {
                cfg.copyStateFrom(journalSettingsFormSnapshot);
                journalSettingsFormSnapshot = null;
            }
            cfg.applyTownJournalGameplayTuning(
                Boolean.TRUE.equals(passive),
                constrBpt,
                constrMs,
                geode,
                chestJewel,
                goldCh,
                goldMin,
                goldMax,
                breakW0,
                breakW1,
                breakW2,
                floatingOn,
                giftMinDays,
                giftMaxDays
            );
            try {
                plugin.getConfig().save().join();
                PluginConfigMerge.rewritePrettyJson(plugin.getDataDirectory().resolve("config.json"));
                playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.saveOk")
                );
            } catch (RuntimeException e) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.saveFail")
                );
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("SettingsResetDefaultsOpen")) {
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            journalSettingsResetConfirmOpen = true;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("SettingsResetDefaultsCancel")) {
            journalSettingsResetConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("SettingsResetDefaultsConfirm")) {
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            journalSettingsResetConfirmOpen = false;
            journalSettingsFormSnapshot = AetherhavenPluginConfig.defaults();
            playerRef.sendMessage(
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.resetApplied")
            );
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("JournalResetVillagers")) {
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null) {
                return;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                return;
            }
            String err = TownJournalAdminService.resetTownVillagersNearPlayer(world, plugin, town, store, tc.getPosition().clone());
            if (err != null) {
                playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.villager.resetFailed").param("reason", err));
            } else {
                playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.villager.resetDone"));
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("JournalFixInn")) {
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null) {
                return;
            }
            InnPoolService.RepairReport rep = TownJournalAdminService.repairInn(world, plugin, town, store);
            playerRef.sendMessage(
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.innRepairDone")
                    .param("locked", String.valueOf(rep.getLockedQuestVisitors()))
                    .param("promoted", String.valueOf(rep.getPromotedResidents()))
                    .param("removed", String.valueOf(rep.getRemovedPoolEntries()))
            );
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("JournalOpenPlotFinishModal")) {
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            journalSettingsPlotModalOpen = true;
            journalSettingsVillagerModalOpen = false;
            journalSettingsResetConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("JournalPlotFinishCancel")) {
            journalSettingsPlotModalOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("JournalPlotFinishConfirm")) {
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            String pick = data.plotPick;
            if (pick == null || pick.isBlank()) {
                return;
            }
            UUID plotUuid = tryParseUuid(pick);
            if (plotUuid == null) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null) {
                return;
            }
            TownJournalAdminService.FinishPlotResult r =
                TownJournalAdminService.tryFinishAssemblingPlot(world, plugin, town, store, plotUuid);
            switch (r) {
                case OK -> {
                    journalSettingsPlotModalOpen = false;
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.finishPlotOk")
                    );
                }
                case NOT_ASSEMBLING ->
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.finishPlotNotAssembling")
                    );
                case NOT_LOADED ->
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.finishPlotNotLoaded")
                    );
                case NO_JOB ->
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.finishPlotNoJob")
                    );
                case FAILED ->
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.finishPlotFailed")
                    );
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("JournalOpenVillagerReportModal")) {
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            journalSettingsVillagerModalOpen = true;
            journalSettingsPlotModalOpen = false;
            journalSettingsResetConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("JournalVillagerReportClose")) {
            journalSettingsVillagerModalOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("JournalVillagerReportBuild")) {
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            String pick = data.villagerPick;
            if (pick == null || pick.isBlank()) {
                return;
            }
            UUID vid = tryParseUuid(pick);
            if (vid == null) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            World world = store.getExternalData().getWorld();
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null) {
                return;
            }
            String report = TownJournalAdminService.buildVillagerSupportReport(store, town, vid, plugin);
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            cmd.set("#JournalSettingsVillagerReportField.Value", report);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("AbandonModalConfirm")) {
            String qid = pendingAbandonQuestId;
            if (qid == null || qid.isBlank()) {
                abandonConfirmOpen = false;
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null || !town.playerCanAbandonQuests(uc.getUuid())) {
                abandonConfirmOpen = false;
                pendingAbandonQuestId = null;
                return;
            }
            JsonObject a = new JsonObject();
            a.addProperty("type", "abandon_quest");
            a.addProperty("id", qid.trim());
            DialogueActionExecutor ex = new DialogueActionExecutor();
            ex.runBatch(List.of(a), ref, store, new DialogueActionBatchResult());
            if (qid.trim().equals(selectedQuestId)) {
                selectedQuestId = null;
            }
            abandonConfirmOpen = false;
            pendingAbandonQuestId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
        }
    }

    private static int parseIntSafe(@Nullable String raw, int min, int max, int def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long parseLongSafe(@Nullable String raw, long min, long max, long def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            long v = Long.parseLong(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDoubleSafe(@Nullable String raw, double min, double max, double def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            if (Double.isNaN(v)) {
                return def;
            }
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void scheduleEnsureJournalStateComponent(@Nonnull World world) {
        world.execute(() -> {
            Ref<EntityStore> pref = this.playerRef.getReference();
            if (pref == null || !pref.isValid()) {
                return;
            }
            Store<EntityStore> st = pref.getStore();
            if (st.getComponent(pref, PlayerTownJournalState.getComponentType()) != null) {
                return;
            }
            st.putComponent(pref, PlayerTownJournalState.getComponentType(), new PlayerTownJournalState());
        });
    }

    private void scheduleCoerceJournalTabFromSettingsIfStillIllegal(@Nonnull World world) {
        world.execute(() -> {
            Ref<EntityStore> pref = this.playerRef.getReference();
            if (pref == null || !pref.isValid()) {
                return;
            }
            Store<EntityStore> st = pref.getStore();
            PlayerTownJournalState js = st.getComponent(pref, PlayerTownJournalState.getComponentType());
            if (js == null) {
                return;
            }
            if (js.getLastTab() != PlayerTownJournalState.JournalTab.SETTINGS) {
                return;
            }
            if (JournalSettingsAccess.canOpen(st, pref)) {
                return;
            }
            js.setLastTab(PlayerTownJournalState.JournalTab.QUESTS);
            st.putComponent(pref, PlayerTownJournalState.getComponentType(), js);
        });
    }

    @Nonnull
    private static PlayerTownJournalState.JournalTab parseTab(@Nullable String tabId) {
        if (tabId == null || tabId.isBlank()) {
            return PlayerTownJournalState.JournalTab.QUESTS;
        }
        return switch (tabId.trim().toUpperCase()) {
            case "TOWN" -> PlayerTownJournalState.JournalTab.TOWN;
            case "GUIDE" -> PlayerTownJournalState.JournalTab.GUIDE;
            case "SETTINGS" -> PlayerTownJournalState.JournalTab.SETTINGS;
            default -> PlayerTownJournalState.JournalTab.QUESTS;
        };
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("QuestId", Codec.STRING), (d, v) -> d.questId = v, d -> d.questId)
            .add()
            .append(new KeyedCodec<>("TabId", Codec.STRING), (d, v) -> d.tabId = v, d -> d.tabId)
            .add()
            .append(new KeyedCodec<>("GuideTopicId", Codec.STRING), (d, v) -> d.guideTopicId = v, d -> d.guideTopicId)
            .add()
            .append(
                new KeyedCodec<>("GuideNavSectionId", Codec.STRING),
                (d, v) -> d.guideNavSectionId = v,
                d -> d.guideNavSectionId
            )
            .add()
            .append(new KeyedCodec<>("PlotId", Codec.STRING), (d, v) -> d.plotId = v, d -> d.plotId)
            .add()
            .append(new KeyedCodec<>("@Passive", Codec.BOOLEAN), (d, v) -> d.passive = v, d -> d.passive)
            .add()
            .append(new KeyedCodec<>("@ConstrBpt", Codec.STRING), (d, v) -> d.constrBpt = v, d -> d.constrBpt)
            .add()
            .append(new KeyedCodec<>("@ConstrMs", Codec.STRING), (d, v) -> d.constrMs = v, d -> d.constrMs)
            .add()
            .append(new KeyedCodec<>("@Geode", Codec.STRING), (d, v) -> d.geodeChance = v, d -> d.geodeChance)
            .add()
            .append(new KeyedCodec<>("@ChestJewel", Codec.STRING), (d, v) -> d.chestJewel = v, d -> d.chestJewel)
            .add()
            .append(new KeyedCodec<>("@GoldCh", Codec.STRING), (d, v) -> d.goldCh = v, d -> d.goldCh)
            .add()
            .append(new KeyedCodec<>("@GoldMin", Codec.STRING), (d, v) -> d.goldMin = v, d -> d.goldMin)
            .add()
            .append(new KeyedCodec<>("@GoldMax", Codec.STRING), (d, v) -> d.goldMax = v, d -> d.goldMax)
            .add()
            .append(new KeyedCodec<>("@BreakW0", Codec.STRING), (d, v) -> d.breakW0 = v, d -> d.breakW0)
            .add()
            .append(new KeyedCodec<>("@BreakW1", Codec.STRING), (d, v) -> d.breakW1 = v, d -> d.breakW1)
            .add()
            .append(new KeyedCodec<>("@BreakW2", Codec.STRING), (d, v) -> d.breakW2 = v, d -> d.breakW2)
            .add()
            .append(new KeyedCodec<>("@GiftEn", Codec.BOOLEAN), (d, v) -> d.giftEn = v, d -> d.giftEn)
            .add()
            .append(new KeyedCodec<>("@GiftMinDays", Codec.STRING), (d, v) -> d.giftMinDays = v, d -> d.giftMinDays)
            .add()
            .append(new KeyedCodec<>("@GiftMaxDays", Codec.STRING), (d, v) -> d.giftMaxDays = v, d -> d.giftMaxDays)
            .add()
            .append(new KeyedCodec<>("@PlotPick", Codec.STRING), (d, v) -> d.plotPick = v, d -> d.plotPick)
            .add()
            .append(new KeyedCodec<>("@VillagerPick", Codec.STRING), (d, v) -> d.villagerPick = v, d -> d.villagerPick)
            .add()
            .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (d, v) -> d.checked = v, d -> d.checked)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String questId;
        @Nullable
        private String tabId;
        @Nullable
        private String guideTopicId;
        @Nullable
        private String guideNavSectionId;
        @Nullable
        private String plotId;
        @Nullable
        private Boolean passive;
        @Nullable
        private String constrBpt;
        @Nullable
        private String constrMs;
        @Nullable
        private String geodeChance;
        @Nullable
        private String chestJewel;
        @Nullable
        private String goldCh;
        @Nullable
        private String goldMin;
        @Nullable
        private String goldMax;
        @Nullable
        private String breakW0;
        @Nullable
        private String breakW1;
        @Nullable
        private String breakW2;
        @Nullable
        private Boolean giftEn;
        @Nullable
        private String giftMinDays;
        @Nullable
        private String giftMaxDays;
        @Nullable
        private String plotPick;
        @Nullable
        private String villagerPick;
        @Nullable
        private Boolean checked;
    }
}
