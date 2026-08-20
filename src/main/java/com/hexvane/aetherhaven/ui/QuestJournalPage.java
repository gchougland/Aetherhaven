package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.PoiScoring;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.difficulty.DifficultyPreset;
import com.hexvane.aetherhaven.difficulty.WorldDifficultyState;
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
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.patrol.PatrolRouteRecord;
import com.hexvane.aetherhaven.patrol.PatrolRouteRegistry;
import com.hexvane.aetherhaven.plugin.JournalTabVisibility;
import com.hexvane.aetherhaven.calendar.PlayerBirthdayService;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.CalendarDate;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import com.hexvane.aetherhaven.quest.IntroQuestPromptPage;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.QuestRewardService;
import com.hexvane.aetherhaven.questboard.QuestBoardCatalog;
import com.hexvane.aetherhaven.questboard.QuestBoardService;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.villager.VillagerBefriendableResolver;
import com.hexvane.aetherhaven.rts.RtsCommandPlayerComponent;
import com.hexvane.aetherhaven.rts.RtsPickTuning;
import com.hexvane.aetherhaven.rts.RtsScreenPickUtil;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.placement.PlotBlockClearMode;
import com.hexvane.aetherhaven.placement.PlotConstructionOpenHelper;
import com.hexvane.aetherhaven.placement.PlotConstructionOpenHelper.OpenResult;
import com.hexvane.aetherhaven.plot.ConstructionFavoritesService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintChunkUtil;
import com.hexvane.aetherhaven.town.PlotJournalRemovalRefundService;
import com.hexvane.aetherhaven.town.PlotJournalRemovalRefundService.RefundResult;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.PlotLocatePlayerComponent;
import com.hexvane.aetherhaven.town.PlotLocateTargetResolver;
import com.hexvane.aetherhaven.town.PlotLinkReconcileService;
import com.hexvane.aetherhaven.town.TownDissolutionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownResidentReconcileService;
import com.hexvane.aetherhaven.town.TownResidentEligibility;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerLocatePlayerComponent;
import com.hexvane.aetherhaven.town.ResidentLastKnownPositionService;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.google.gson.JsonObject;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hexvane.aetherhaven.quest.PlayerQuestIds;
import com.hexvane.aetherhaven.quest.PlayerQuestProgress;
import com.hexvane.aetherhaven.quest.PlayerQuestProgressionService;
import com.hexvane.aetherhaven.worldnpc.WorldNpcPlayerProgress;
import com.hexvane.aetherhaven.worldnpc.WorldQuestBoardService;
import com.hexvane.aetherhaven.worldnpc.WorldQuestIds;
import com.hexvane.aetherhaven.worldnpc.WorldQuestProgressionService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
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
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final int MAX_GUIDE_TOPICS = 48;
    /** Long topics (e.g. mechanic_commands) need nested list rows; 96 truncated sub-bullets. */
    private static final int MAX_GUIDE_MD_ROWS = 256;
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
    @Nullable
    private Message journalSettingsPersonalStatus;

    public QuestJournalPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Nullable
    private static TownRecord journalTown(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nullable AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid
    ) {
        if (plugin == null) {
            return null;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        if (journal != null) {
            TownPlayerResolution.reconcileActiveTownId(tm, playerUuid, journal);
        }
        return TownPlayerResolution.resolveActiveTown(world, store, ref, tm, journal);
    }

    private void wireActiveTownSelector(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nullable AetherhavenPlugin plugin,
        @Nullable UUIDComponent uc,
        @Nonnull PlayerTownJournalState stateForTabs
    ) {
        if (plugin == null || uc == null) {
            commandBuilder.set("#ActiveTownRow.Visible", false);
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        List<TownRecord> affiliated = TownPlayerResolution.listAffiliatedTownsInWorld(tm, uc.getUuid());
        if (affiliated.size() <= 1) {
            commandBuilder.set("#ActiveTownRow.Visible", false);
            return;
        }
        commandBuilder.set("#ActiveTownRow.Visible", true);
        commandBuilder.set(
            "#ActiveTownLabel.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.activeTownLabel")
        );
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        UUID self = uc.getUuid();
        TownRecord active = journalTown(world, store, ref, plugin, self);
        String selected = active != null ? active.getTownId().toString() : affiliated.get(0).getTownId().toString();
        String ownedSuffix =
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.activeTownOwnedSuffix")
                .getAnsiMessage();
        for (TownRecord t : affiliated) {
            String label = t.getDisplayName();
            if (t.isOwner(self)) {
                label = label + " (" + ownedSuffix + ")";
            }
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(label), t.getTownId().toString()));
        }
        commandBuilder.set("#ActiveTownDropdown #Input.Entries", entries);
        commandBuilder.set("#ActiveTownDropdown #Input.Value", selected);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ActiveTownDropdown #Input",
            new EventData().append("Action", "SelectActiveTown").append("@ActiveTownId", "#ActiveTownDropdown #Input.Value"),
            false
        );
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
        PlayerTownJournalState.SettingsSubTab settingsSubTab = stateForTabs.getLastSettingsSubTab();
        if (!journalSettingsAllowed && settingsSubTab == PlayerTownJournalState.SettingsSubTab.SERVER) {
            settingsSubTab = PlayerTownJournalState.SettingsSubTab.PERSONAL;
            if (journalState != null) {
                scheduleCoerceSettingsSubTabFromServerIfStillIllegal(world);
            }
        }
        if (!journalSettingsAllowed) {
            journalSettingsPlotModalOpen = false;
            journalSettingsVillagerModalOpen = false;
            journalSettingsResetConfirmOpen = false;
            journalSettingsFormSnapshot = null;
            journalSettingsPersonalStatus = null;
        }

        commandBuilder.set("#TabSettings.Visible", true);

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

        wireActiveTownSelector(commandBuilder, eventBuilder, world, store, ref, plugin, uc, stateForTabs);

        boolean abandonModalBlocking = false;
        if (abandonConfirmOpen && pendingAbandonQuestId != null && plugin != null && uc != null) {
            String pendingId = pendingAbandonQuestId.trim();
            WorldNpcPlayerProgress worldProgressModal =
                AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin)
                    .getOrCreatePlayerProgress(uc.getUuid());
            PlayerQuestProgress playerProgressModal = store.getComponent(ref, PlayerQuestProgress.getComponentType());
            boolean worldPending =
                isActiveWorldJournalQuest(worldProgressModal, pendingId);
            boolean playerPending =
                isActivePlayerJournalQuest(playerProgressModal, pendingId);
            TownRecord townModal =
                journalTown(world, store, ref, plugin, uc.getUuid());
            boolean townPending =
                townModal != null
                    && townModal.playerCanAbandonQuests(uc.getUuid())
                    && QuestBoardService.isActiveJournalQuest(townModal, pendingId);
            if (worldPending || playerPending || townPending) {
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
            journalSettingsPersonalStatus = null;
        }

        boolean plotModalBlocking = false;
        PlotInstance plotForRemoveModal = null;
        if (!abandonModalBlocking
            && plotRemoveConfirmOpen
            && pendingRemovePlotId != null
            && plugin != null
            && uc != null) {
            TownRecord townPlot = journalTown(world, store, ref, plugin, uc.getUuid());
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
            journalSettingsPersonalStatus = null;
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
            ConstructionDefinition removeModalDef =
                plugin.getConstructionCatalog().get(plotForRemoveModal.getConstructionId());
            if (removeModalDef != null) {
                commandBuilder.set(
                    "#JournalPlotRemoveModalText.TextSpans",
                    Message.translation(
                        PlotJournalRemovalRefundService.confirmBodyLangKey(
                            removeModalDef,
                            plotForRemoveModal,
                            world,
                            plugin
                        )
                    )
                );
            }
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
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabSettings",
            new EventData().append("Action", "Tab").append("TabId", "SETTINGS"),
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
            journalSettingsPersonalStatus = null;
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            WorldNpcPlayerProgress worldProgress =
                AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin)
                    .getOrCreatePlayerProgress(uc.getUuid());
            PlayerQuestProgress playerProgress = store.getComponent(ref, PlayerQuestProgress.getComponentType());
            List<String> active = collectActiveJournalQuestIds(town, uc.getUuid(), worldProgress, playerProgress);
            if (active.isEmpty()) {
                if (town == null) {
                    setQuestsBlocked(commandBuilder, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.noActive"));
                } else if (!town.playerHasQuestPermission(uc.getUuid())) {
                    setQuestsBlocked(commandBuilder, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.noPermission"));
                } else {
                    setQuestsBlocked(commandBuilder, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.noActive"));
                }
                selectedQuestId = null;
                return;
            }

            commandBuilder.set("#QuestsBlocked.Visible", false);
            commandBuilder.set("#QuestsSplit.Visible", true);

            if (selectedQuestId == null || !active.contains(selectedQuestId)) {
                selectedQuestId = active.get(0);
            }

            QuestCatalog quests = plugin.getQuestCatalog();
            QuestBoardCatalog boardCatalog = plugin.getQuestBoardCatalog();
            Store<EntityStore> entityStore =
                world.getEntityStore() != null ? world.getEntityStore().getStore() : store;
            commandBuilder.clear(QUEST_ROWS);
            int n = Math.min(active.size(), MAX_ROWS);
            for (int i = 0; i < n; i++) {
                String qid = active.get(i);
                commandBuilder.append(QUEST_ROWS, "Aetherhaven/QuestJournalRow.ui");
                String row = QUEST_ROWS + "[" + i + "]";
                Message titleMsg =
                    journalQuestTitle(qid, town, worldProgress, playerProgress, quests, boardCatalog, entityStore, plugin);
                commandBuilder.set(row + " #Select #QuestTitle.TextSpans", titleMsg);
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
            applyJournalQuestDetail(
                commandBuilder,
                sel,
                town,
                worldProgress,
                playerProgress,
                quests,
                boardCatalog,
                entityStore,
                plugin,
                ref
            );

            boolean canAbandon =
                PlayerQuestIds.isPlayerQuestRow(sel)
                    || WorldQuestIds.isWorldQuestRow(sel)
                    || WorldQuestIds.isWorldBoardRow(sel)
                    || (town != null && town.playerCanAbandonQuests(uc.getUuid()));
            commandBuilder.set("#AbandonQuestButton.Visible", canAbandon);
            if (canAbandon) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#AbandonQuestButton",
                    new EventData().append("Action", "BeginAbandonConfirm"),
                    false
                );
            }
            boolean pinned = stateForTabs.isQuestPinned(sel);
            int activePinnedCount = stateForTabs.activePinnedQuestCount(new HashSet<>(active));
            boolean pinLimitReached =
                !pinned && activePinnedCount >= PlayerTownJournalState.MAX_PINNED_QUESTS;
            commandBuilder.set("#PinQuestButton.Visible", true);
            commandBuilder.set("#PinQuestButton.Disabled", pinLimitReached);
            commandBuilder.set(
                "#PinQuestButton.TextSpans",
                Message.translation(
                    pinned
                        ? "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.unpinQuest"
                        : pinLimitReached
                            ? "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.pinLimit"
                            : "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.pinQuest"
                )
            );
            if (!pinLimitReached) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#PinQuestButton",
                    new EventData().append("Action", "ToggleQuestPin").append("QuestId", sel),
                    false
                );
            }
            return;
        }

        if (currentTab == PlayerTownJournalState.JournalTab.TOWN) {
            buildTownTab(commandBuilder, eventBuilder, plugin, store, ref, uc, world);
            return;
        }

        if (currentTab == PlayerTownJournalState.JournalTab.SETTINGS) {
            buildJournalSettingsTab(
                commandBuilder,
                eventBuilder,
                plugin,
                store,
                ref,
                uc,
                world,
                journalSettingsAllowed,
                settingsSubTab,
                stateForTabs
            );
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
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
                    Message title = journalPlotConstructionTitle(ccat, p);
                    String label = title.getAnsiMessage() + "  " + p.getSignX() + " " + p.getSignY() + " " + p.getSignZ();
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
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
        @Nonnull World world,
        boolean serverSettingsAllowed,
        @Nonnull PlayerTownJournalState.SettingsSubTab settingsSubTab,
        @Nonnull PlayerTownJournalState journalPrefs
    ) {
        boolean personalActive =
            settingsSubTab == PlayerTownJournalState.SettingsSubTab.PERSONAL || !serverSettingsAllowed;
        commandBuilder.set("#SettingsSubTabStrip.Visible", serverSettingsAllowed);
        commandBuilder.set("#SettingsTabServer.Visible", serverSettingsAllowed);
        commandBuilder.set("#SettingsTabPersonal.Disabled", personalActive);
        commandBuilder.set("#SettingsTabServer.Disabled", serverSettingsAllowed && !personalActive);
        commandBuilder.set("#SettingsPersonalPage.Visible", personalActive);
        commandBuilder.set("#SettingsServerPage.Visible", serverSettingsAllowed && !personalActive);

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SettingsTabPersonal",
            new EventData().append("Action", "SettingsSubTab").append("SubTabId", "PERSONAL"),
            false
        );
        if (serverSettingsAllowed) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SettingsTabServer",
                new EventData().append("Action", "SettingsSubTab").append("SubTabId", "SERVER"),
                false
            );
        }

        if (personalActive) {
            buildJournalSettingsPersonalTab(commandBuilder, eventBuilder, journalPrefs, store);
        } else {
            buildJournalSettingsServerTab(commandBuilder, eventBuilder, plugin, store, ref, uc, world);
        }
    }

    private void buildJournalSettingsPersonalTab(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull PlayerTownJournalState journalPrefs,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.set("#SettingsPersonalStatus.TextSpans", journalSettingsPersonalStatus != null ? journalSettingsPersonalStatus : Message.raw(""));
        boolean rtsSettings = JournalTabVisibility.rtsTuningTab();
        commandBuilder.set("#SettingsRtsPickFovField.Visible", rtsSettings);
        commandBuilder.set("#SettingsRtsPickAspectField.Visible", rtsSettings);
        if (rtsSettings) {
            commandBuilder.set(
                "#SettingsRtsPickFovField.Value",
                String.format(Locale.US, "%.1f", journalPrefs.effectiveRtsPickVerticalFovDeg())
            );
            commandBuilder.set(
                "#SettingsRtsPickAspectField.Value",
                String.format(Locale.US, "%.3f", journalPrefs.effectiveRtsPickAspectRatio())
            );
        }
        commandBuilder.set("#SettingsShowBordersCheck #CheckBox.Value", journalPrefs.isShowTownBordersOnMap());
        Season birthdaySeason = journalPrefs.getBirthdaySeason();
        int birthdayDay = journalPrefs.getBirthdayDay();
        if (birthdaySeason == null || birthdayDay < 1) {
            CalendarDate today = settingsToday(store);
            birthdaySeason = today.season();
            birthdayDay = today.dayOfSeason();
        }
        commandBuilder.set("#SettingsBirthdaySeasonDropdown #Input.Entries", IntroQuestPromptPage.seasonEntries());
        commandBuilder.set(
            "#SettingsBirthdaySeasonDropdown #Input.Value",
            PlayerBirthdayService.seasonValue(birthdaySeason)
        );
        commandBuilder.set("#SettingsBirthdayDayDropdown #Input.Entries", IntroQuestPromptPage.dayEntries());
        commandBuilder.set("#SettingsBirthdayDayDropdown #Input.Value", String.valueOf(birthdayDay));
        commandBuilder.set("#SettingsSpeechEnableCheck #CheckBox.Value", journalPrefs.isDialogueSpeechEnabled());
        commandBuilder.set("#SettingsSpeechVolumeSlider.Value", journalPrefs.getDialogueSpeechVolumePercent());
        commandBuilder.set(
            "#SettingsSpeechVolumeValue.TextSpans",
            Message.raw(journalPrefs.getDialogueSpeechVolumePercent() + "%")
        );
        commandBuilder.set("#SettingsHudTimeCheck #CheckBox.Value", journalPrefs.isHudShowTime());
        commandBuilder.set("#SettingsHudDateCheck #CheckBox.Value", journalPrefs.isHudShowDate());
        commandBuilder.set("#SettingsHudGoldCheck #CheckBox.Value", journalPrefs.isHudShowGold());
        commandBuilder.set("#SettingsHudQuestsCheck #CheckBox.Value", journalPrefs.isHudShowQuests());
        commandBuilder.set(
            "#SettingsHudOpacitySlider.Value",
            Math.round(journalPrefs.getHudBackgroundOpacity() * 100f)
        );
        commandBuilder.set(
            "#SettingsHudOpacityValue.TextSpans",
            Message.raw(Math.round(journalPrefs.getHudBackgroundOpacity() * 100f) + "%")
        );
        commandBuilder.set("#SettingsHudStatusPlacement #Input.Entries", hudPlacementEntries());
        commandBuilder.set("#SettingsHudStatusPlacement #Input.Value", journalPrefs.getHudStatusPlacement());
        commandBuilder.set("#SettingsHudStatusXField.Value", String.valueOf(journalPrefs.getHudStatusX()));
        commandBuilder.set("#SettingsHudStatusYField.Value", String.valueOf(journalPrefs.getHudStatusY()));
        commandBuilder.set("#SettingsHudQuestPlacement #Input.Entries", hudPlacementEntries());
        commandBuilder.set("#SettingsHudQuestPlacement #Input.Value", journalPrefs.getHudQuestPlacement());
        commandBuilder.set("#SettingsHudQuestXField.Value", String.valueOf(journalPrefs.getHudQuestX()));
        commandBuilder.set("#SettingsHudQuestYField.Value", String.valueOf(journalPrefs.getHudQuestY()));
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SettingsShowBordersCheck #CheckBox",
            new EventData()
                .append("Action", "TownShowBordersToggle")
                .append("@Checked", "#SettingsShowBordersCheck #CheckBox.Value"),
            false
        );

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SettingsSpeechEnableCheck #CheckBox",
            new EventData()
                .append("Action", "DialogueSpeechToggle")
                .append("@Checked", "#SettingsSpeechEnableCheck #CheckBox.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SettingsSpeechVolumeSlider",
            new EventData()
                .append("Action", "DialogueSpeechVolumePreview")
                .append("@SpeechVolume", "#SettingsSpeechVolumeSlider.Value"),
            false
        );

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SettingsHudOpacitySlider",
            new EventData()
                .append("Action", "HudOpacityPreview")
                .append("@HudOpacity", "#SettingsHudOpacitySlider.Value"),
            false
        );

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SettingsPersonalSaveButton",
            new EventData()
                .append("Action", "PersonalSettingsSave")
                .append("@RtsFov", "#SettingsRtsPickFovField.Value")
                .append("@RtsAspect", "#SettingsRtsPickAspectField.Value")
                .append("@SpeechEnabled", "#SettingsSpeechEnableCheck #CheckBox.Value")
                .append("@SpeechVolume", "#SettingsSpeechVolumeSlider.Value")
                .append("@HudTime", "#SettingsHudTimeCheck #CheckBox.Value")
                .append("@HudDate", "#SettingsHudDateCheck #CheckBox.Value")
                .append("@HudGold", "#SettingsHudGoldCheck #CheckBox.Value")
                .append("@HudQuests", "#SettingsHudQuestsCheck #CheckBox.Value")
                .append("@HudOpacity", "#SettingsHudOpacitySlider.Value")
                .append("@HudStatusPlacement", "#SettingsHudStatusPlacement #Input.Value")
                .append("@HudStatusX", "#SettingsHudStatusXField.Value")
                .append("@HudStatusY", "#SettingsHudStatusYField.Value")
                .append("@HudQuestPlacement", "#SettingsHudQuestPlacement #Input.Value")
                .append("@HudQuestX", "#SettingsHudQuestXField.Value")
                .append("@HudQuestY", "#SettingsHudQuestYField.Value")
                .append("@BirthdaySeason", "#SettingsBirthdaySeasonDropdown #Input.Value")
                .append("@BirthdayDay", "#SettingsBirthdayDayDropdown #Input.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SettingsPersonalResetButton",
            new EventData().append("Action", "PersonalSettingsReset"),
            false
        );
    }

    @Nonnull
    private static CalendarDate settingsToday(@Nonnull Store<EntityStore> store) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return new CalendarDate(Season.SPRING, 1, 1L);
        }
        return AetherhavenCalendar.from(wtr.getGameDateTime());
    }

    @Nonnull
    private static ObjectArrayList<DropdownEntryInfo> hudPlacementEntries() {
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        String base = "aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.place.";
        entries.add(new DropdownEntryInfo(LocalizableString.fromMessageId(base + "topLeft"), "TOP_LEFT"));
        entries.add(new DropdownEntryInfo(LocalizableString.fromMessageId(base + "topRight"), "TOP_RIGHT"));
        entries.add(new DropdownEntryInfo(LocalizableString.fromMessageId(base + "bottomLeft"), "BOTTOM_LEFT"));
        entries.add(new DropdownEntryInfo(LocalizableString.fromMessageId(base + "bottomRight"), "BOTTOM_RIGHT"));
        entries.add(new DropdownEntryInfo(LocalizableString.fromMessageId(base + "custom"), "CUSTOM"));
        return entries;
    }

    private void buildJournalSettingsServerTab(
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
        WorldDifficultyState worldDifficulty = AetherhavenWorldRegistries.getOrLoadWorldDifficulty(world, plugin);
        commandBuilder.set(
            "#SettingsDifficultyCurrent.TextSpans",
            Message.translation("aetherhaven_difficulty.aetherhaven.difficulty.journalCurrent")
                .param("preset", Message.translation(presetLangKey(worldDifficulty.getPreset())))
        );
        boolean difficultyUi = JournalTabVisibility.difficultyTab();
        commandBuilder.set("#SettingsOpenDifficultyButton.Visible", difficultyUi);
        if (difficultyUi) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SettingsOpenDifficultyButton",
                new EventData().append("Action", "OpenDifficulty"),
                false
            );
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
        commandBuilder.set(
            "#SettingsShopMemberPriceField.Value",
            String.valueOf(cfg.getShopSpotPlayerListingPricePercent())
        );

        TownRecord town = uc != null ? journalTown(world, store, ref, plugin, uc.getUuid()) : null;
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
                .append("@GiftMaxDays", "#SettingsGiftDaysMaxField.Value")
                .append("@ShopMemberPct", "#SettingsShopMemberPriceField.Value"),
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
                "#SettingsRepairPlotsButton",
                new EventData().append("Action", "JournalRepairPlots"),
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
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SettingsDedupeVillagersButton",
                new EventData().append("Action", "JournalDedupeVillagers"),
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
        TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
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
        VillagerLocatePlayerComponent locateSession = VillagerLocatePlayerComponent.get(store, ref);
        for (int i = 0; i < villagers.size(); i++) {
            TownVillagerRow r = villagers.get(i);
            commandBuilder.append(TOWN_VILLAGER_ROWS, "Aetherhaven/TownJournalVillagerRow.ui");
            String row = TOWN_VILLAGER_ROWS + "[" + i + "]";
            commandBuilder.set(row + " #Select #Portrait.AssetPath", r.portraitPath());
            commandBuilder.set(row + " #Select #VillagerName.TextSpans", Message.raw(r.label()));
            boolean befriendable =
                JournalTabVisibility.reputationTab()
                    && VillagerBefriendableResolver.isBefriendableForJournal(store, r.entityUuid(), r.roleId(), plugin);
            String heartsPath = row + " #Select #ReputationHeartSlots";
            commandBuilder.set(heartsPath + ".Visible", befriendable);
            if (befriendable) {
                for (int h = 0; h < 10; h++) {
                    commandBuilder.append(heartsPath, "Aetherhaven/HeartSlot.ui");
                }
                int rep = VillagerReputationService.getOrCreateEntry(town, uc.getUuid(), r.entityUuid()).getReputation();
                ReputationHeartUi.applyHearts(commandBuilder, heartsPath, rep);
            }
            commandBuilder.set(
                row + " #Select #ScheduleLocation.TextSpans",
                townJournalSecondaryLineMessage(plugin, world, store, r, gameNow)
            );
            boolean showSchedule =
                TownVillagerBinding.KIND_GUARD.equals(r.bindingKind())
                    || !TownResidentEligibility.isTownsfolkPoolKind(r.bindingKind(), r.roleId(), plugin)
                    || liveNeedBreakActivityMessage(plugin, world, store, r.entityUuid()) != null;
            commandBuilder.set(row + " #Select #ScheduleLocation.Visible", showSchedule);
            boolean locateActive = locateSession != null && locateSession.isActiveFor(r.entityUuid());
            commandBuilder.set(row + " #LocateVillager.Visible", !locateActive);
            commandBuilder.set(row + " #LocateVillagerActive.Visible", locateActive);
            String locateTooltipKey = locateActive
                ? "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateVillagerActiveTooltip"
                : "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateVillagerTooltip";
            Message locateTooltip = Message.translation(locateTooltipKey);
            commandBuilder.set(row + " #LocateVillager.TooltipTextSpans", locateTooltip);
            commandBuilder.set(row + " #LocateVillagerActive.TooltipTextSpans", locateTooltip);
            EventData locateEvent =
                new EventData().append("Action", "ToggleLocateVillager").append("VillagerUuid", r.entityUuid().toString());
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #LocateVillager",
                locateEvent,
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #LocateVillagerActive",
                locateEvent,
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                new EventData().append("Action", "OpenVillagerNeeds").append("VillagerUuid", r.entityUuid().toString()),
                false
            );
        }

        commandBuilder.clear(TOWN_PLOT_ROWS);
        List<PlotInstance> plots = new ArrayList<>();
        ConstructionCatalog plotCatalog = plugin.getConstructionCatalog();
        for (PlotInstance p : town.getPlotInstances()) {
            ConstructionDefinition plotDef = plotCatalog.get(p.getConstructionId());
            if (plotDef != null && plotDef.isExcludeFromTownJournal()) {
                continue;
            }
            plots.add(p);
        }
        plots.sort((a, b) -> compareJournalPlots(plotCatalog, a, b));
        List<String> communityPlotIconIds = new ArrayList<>();
        for (PlotInstance plot : plots) {
            String constructionId = plot.getConstructionId();
            if (constructionId != null && ConstructionFavoritesService.isCommunityBuildingId(constructionId)) {
                communityPlotIconIds.add(constructionId.trim());
            }
        }
        if (!communityPlotIconIds.isEmpty()) {
            plugin.getCommunityCatalogService().ensureIconsForIds(communityPlotIconIds);
        }
        boolean canRemovePlots = town.playerCanRemovePlots(uc.getUuid());
        boolean canRepairPlots = town.playerCanManageConstructions(uc.getUuid());
        PlotLocatePlayerComponent locatePlotSession = PlotLocatePlayerComponent.get(store, ref);
        java.util.Set<String> plotIconsEnsured = new java.util.HashSet<>();
        for (int i = 0; i < plots.size(); i++) {
            PlotInstance p = plots.get(i);
            commandBuilder.append(TOWN_PLOT_ROWS, "Aetherhaven/TownJournalPlotRow.ui");
            String row = TOWN_PLOT_ROWS + "[" + i + "]";
            Message title = journalPlotConstructionTitle(plotCatalog, p);
            commandBuilder.set(row + " #Select #PlotTitle.TextSpans", title);
            String coords = p.getSignX() + " " + p.getSignY() + " " + p.getSignZ();
            commandBuilder.set(row + " #Select #PlotCoords.TextSpans", Message.raw(coords));
            commandBuilder.set(
                row + " #Select #PlotStatus.TextSpans",
                JournalPlotAssigneeFormatter.plotStatusLine(plugin, store, town, plotCatalog, p)
            );
            String constructionId = p.getConstructionId();
            if (constructionId != null && plotIconsEnsured.add(constructionId.trim())) {
                ConstructionTokenIconPath.registerRuntimeIconIfPresent(plugin, constructionId);
            }
            ItemGridSlot tokenSlot = AetherhavenUiItemGrids.plotTokenSlotForConstruction(p.getConstructionId(), plotCatalog);
            if (tokenSlot != null) {
                commandBuilder.set(row + " #Select #PlotIconHost.Visible", true);
                AetherhavenUiItemGrids.setSingleSlot(commandBuilder, row + " #Select #PlotTokenSlot", tokenSlot);
            } else {
                commandBuilder.set(row + " #Select #PlotIconHost.Visible", false);
                AetherhavenUiItemGrids.setSingleSlotEmpty(commandBuilder, row + " #Select #PlotTokenSlot");
            }
            boolean areaLoaded = PlotFootprintChunkUtil.isPlotRepairAreaLoaded(world, p);
            boolean locatePlotActive = locatePlotSession != null && locatePlotSession.isActiveFor(p.getPlotId());
            commandBuilder.set(row + " #LocatePlot.Visible", !locatePlotActive);
            commandBuilder.set(row + " #LocatePlotActive.Visible", locatePlotActive);
            String locatePlotTooltipKey = locatePlotActive
                ? "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locatePlotActiveTooltip"
                : "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locatePlotTooltip";
            Message locatePlotTooltip = Message.translation(locatePlotTooltipKey);
            commandBuilder.set(row + " #LocatePlot.TooltipTextSpans", locatePlotTooltip);
            commandBuilder.set(row + " #LocatePlotActive.TooltipTextSpans", locatePlotTooltip);
            EventData locatePlotEvent =
                new EventData().append("Action", "ToggleLocatePlot").append("PlotId", p.getPlotId().toString());
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #LocatePlot",
                locatePlotEvent,
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #LocatePlotActive",
                locatePlotEvent,
                false
            );
            commandBuilder.set(row + " #RepairPlot.Visible", canRepairPlots);
            commandBuilder.set(
                row + " #RepairPlot.TooltipTextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.repairPlotTooltip")
            );
            if (canRepairPlots) {
                commandBuilder.set(row + " #RepairPlot.Disabled", !areaLoaded);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #RepairPlot",
                    new EventData().append("Action", "RepairPlot").append("PlotId", p.getPlotId().toString()),
                    false
                );
            }
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
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                new EventData().append("Action", "OpenPlotMaterials").append("PlotId", p.getPlotId().toString()),
                false
            );
        }
    }

    @Nonnull
    private static String presetLangKey(@Nonnull DifficultyPreset preset) {
        return switch (preset) {
            case EASY -> "aetherhaven_difficulty.aetherhaven.difficulty.easy.title";
            case HARD -> "aetherhaven_difficulty.aetherhaven.difficulty.hard.title";
            case CUSTOM -> "aetherhaven_difficulty.aetherhaven.difficulty.custom.title";
            case NORMAL -> "aetherhaven_difficulty.aetherhaven.difficulty.normal.title";
        };
    }

    @Nonnull
    private static Message journalPlotConstructionTitle(@Nonnull ConstructionCatalog catalog, @Nonnull PlotInstance plot) {
        String stored = plot.getConstructionId();
        if (stored == null || stored.isBlank()) {
            return Message.raw("?");
        }
        String t = stored.trim();
        ConstructionDefinition byStored = catalog.get(t);
        if (byStored != null) {
            return byStored.displayNameMessage();
        }
        String gameplay = catalog.resolveGameplayConstructionId(t);
        ConstructionDefinition byGameplay = catalog.get(gameplay);
        return byGameplay != null ? byGameplay.displayNameMessage() : Message.raw(t);
    }

    private static int compareJournalPlots(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull PlotInstance a,
        @Nonnull PlotInstance b
    ) {
        int byTitle =
            journalPlotConstructionTitle(catalog, a)
                .getAnsiMessage()
                .compareToIgnoreCase(journalPlotConstructionTitle(catalog, b).getAnsiMessage());
        if (byTitle != 0) {
            return byTitle;
        }
        int byX = Integer.compare(a.getSignX(), b.getSignX());
        if (byX != 0) {
            return byX;
        }
        int byZ = Integer.compare(a.getSignZ(), b.getSignZ());
        if (byZ != 0) {
            return byZ;
        }
        return Integer.compare(a.getSignY(), b.getSignY());
    }

    private static void setTownTabBlocked(@Nonnull UICommandBuilder commandBuilder, @Nonnull Message msg) {
        commandBuilder.set("#TownBlocked.Visible", true);
        commandBuilder.set("#TownBlocked.TextSpans", msg);
        commandBuilder.set("#TownSplit.Visible", false);
        commandBuilder.clear(TOWN_VILLAGER_ROWS);
        commandBuilder.clear(TOWN_PLOT_ROWS);
    }

    @Nonnull
    private static Message townJournalSecondaryLineMessage(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownVillagerRow row,
        @Nullable LocalDateTime gameNow
    ) {
        if (TownVillagerBinding.KIND_GUARD.equals(row.bindingKind())) {
            return guardPatrolRouteMessage(world, plugin, row.entityUuid());
        }
        Message eating = liveNeedBreakActivityMessage(plugin, world, store, row.entityUuid());
        if (eating != null) {
            return eating;
        }
        if (TownResidentEligibility.isTownsfolkPoolKind(row.bindingKind(), row.roleId(), plugin)) {
            return Message.raw("");
        }
        return scheduleLocationMessage(plugin, row.roleId(), gameNow);
    }

    /**
     * When a villager is filling hunger, energy, or fun, override the schedule line so the journal shows the break
     * instead of work / home / inn.
     */
    @Nullable
    private static Message liveNeedBreakActivityMessage(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid
    ) {
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(entityUuid);
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        VillagerAutonomyState autonomy = store.getComponent(npcRef, VillagerAutonomyState.getComponentType());
        if (autonomy == null) {
            return null;
        }
        int phase = autonomy.getPhase();
        UUID poiId = autonomy.getTargetPoiUuid();
        PoiEntry poi = null;
        if (poiId != null && !AetherhavenConstants.isScheduleZoneCommutePoi(poiId)) {
            PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            poi = reg.get(poiId);
        }
        if (autonomy.isFillingHunger()) {
            return needBreakActivityMessage(phase, poi, NeedBreakJournalKind.HUNGER);
        }
        if (autonomy.isFillingEnergy()) {
            return needBreakActivityMessage(phase, poi, NeedBreakJournalKind.ENERGY);
        }
        if (autonomy.isFillingFun()) {
            return needBreakActivityMessage(phase, poi, NeedBreakJournalKind.FUN);
        }
        if (phase != VillagerAutonomyState.PHASE_TRAVEL && phase != VillagerAutonomyState.PHASE_USE) {
            return null;
        }
        if (poi == null) {
            return null;
        }
        if (PoiScoring.isEatPoi(poi)) {
            return needBreakActivityMessage(phase, poi, NeedBreakJournalKind.HUNGER);
        }
        if (PoiScoring.isRestPoi(poi)) {
            return needBreakActivityMessage(phase, poi, NeedBreakJournalKind.ENERGY);
        }
        if (PoiScoring.isFunPoi(poi)) {
            return needBreakActivityMessage(phase, poi, NeedBreakJournalKind.FUN);
        }
        return null;
    }

    private enum NeedBreakJournalKind {
        HUNGER,
        ENERGY,
        FUN
    }

    @Nullable
    private static Message needBreakActivityMessage(
        int phase,
        @Nullable PoiEntry poi,
        @Nonnull NeedBreakJournalKind kind
    ) {
        boolean traveling = phase == VillagerAutonomyState.PHASE_TRAVEL;
        boolean using = phase == VillagerAutonomyState.PHASE_USE;
        return switch (kind) {
            case HUNGER -> {
                boolean atRestaurant =
                    poi != null && poi.getTags().contains(AetherhavenConstants.POI_TAG_RESTAURANT);
                if (traveling) {
                    yield Message.translation(
                        atRestaurant
                            ? "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleGoingToRestaurant"
                            : "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleGoingToEat"
                    );
                }
                if (using) {
                    yield Message.translation(
                        atRestaurant
                            ? "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleEatingRestaurant"
                            : "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleEating"
                    );
                }
                yield Message.translation(
                    "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleGoingToEat"
                );
            }
            case ENERGY -> {
                if (using) {
                    yield Message.translation(
                        "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleResting"
                    );
                }
                yield Message.translation(
                    "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleGoingToRest"
                );
            }
            case FUN -> {
                if (using) {
                    yield Message.translation(
                        "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleAtPark"
                    );
                }
                yield Message.translation(
                    "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleGoingToPark"
                );
            }
        };
    }

    @Nonnull
    private static Message guardPatrolRouteMessage(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID guardEntityUuid
    ) {
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        List<PatrolRouteRecord> routes = reg.routesForGuard(guardEntityUuid);
        if (routes.isEmpty()) {
            return Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.scheduleUnknown");
        }
        if (routes.size() == 1) {
            return Message.raw(routes.get(0).safeDisplayName());
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < routes.size(); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(routes.get(i).safeDisplayName());
        }
        return Message.raw(names.toString());
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
        return plugin.getScheduleLocationCatalog().journalDisplayMessage(sym);
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

        String guideLocale = playerRef.getLanguage() != null && !playerRef.getLanguage().isBlank()
            ? playerRef.getLanguage()
            : "en-US";
        GuideTopicRepository repo = GuideTopicRepository.get(plugin.getClass().getClassLoader(), guideLocale);
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
                    GuideScheduleWeekAppender.appendWeek(
                        commandBuilder,
                        GUIDE_SCHEDULE_ROWS,
                        wsched,
                        gameNow,
                        plugin.getScheduleLocationCatalog()
                    );
                }
            } else {
                commandBuilder.set("#GuideScheduleBlock.Visible", false);
                commandBuilder.set("#GuideScheduleListScrolling.Visible", false);
            }

            int giftCount =
                vdef.isBefriendable()
                    ? vdef.getGiftLoves().size() + vdef.getGiftLikes().size() + vdef.getGiftDislikes().size()
                    : 0;
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
                ItemGridSlot slot = AetherhavenUiItemGrids.slotForKnownItem(sorted.get(off + i), 1);
                gridSlots[i] = slot != null ? slot : new ItemGridSlot();
            }
            AetherhavenUiItemGrids.setSlots(commandBuilder, block + " #IconGrid", gridSlots);
            bi++;
        }
        return bi;
    }

    @Nonnull
    private static List<String> collectActiveJournalQuestIds(
        @Nullable TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull WorldNpcPlayerProgress worldProgress,
        @Nullable PlayerQuestProgress playerProgress
    ) {
        List<String> active = new ArrayList<>();
        if (playerProgress != null) {
            for (String questId : playerProgress.activeQuestIdsSnapshot()) {
                active.add(PlayerQuestIds.playerRow(questId));
            }
        }
        if (town != null && town.playerHasQuestPermission(playerUuid)) {
            active.addAll(town.getActiveQuestIdsSnapshot());
            for (QuestBoardSlotRecord boardSlot : town.acceptedBoardQuestsSnapshot()) {
                active.add(QuestBoardService.journalRowId(boardSlot.instanceIdOrEmpty()));
            }
        }
        for (String questId : worldProgress.activeQuestIdsSnapshot()) {
            active.add(WorldQuestIds.worldRow(questId));
        }
        for (QuestBoardSlotRecord boardSlot : worldProgress.acceptedBoardSlotsSnapshot()) {
            active.add(WorldQuestIds.boardRow(boardSlot.instanceIdOrEmpty()));
        }
        return active;
    }

    private static boolean isActiveWorldJournalQuest(
        @Nonnull WorldNpcPlayerProgress progress, @Nonnull String rowId
    ) {
        if (WorldQuestIds.isWorldQuestRow(rowId)) {
            String questId = WorldQuestIds.parseWorldQuestId(rowId);
            return questId != null && progress.hasQuestActive(questId);
        }
        if (WorldQuestIds.isWorldBoardRow(rowId)) {
            String instanceId = WorldQuestIds.parseWorldBoardInstanceId(rowId);
            return instanceId != null && WorldQuestBoardService.findAcceptedSlot(progress, instanceId) != null;
        }
        return false;
    }

    private static boolean isActivePlayerJournalQuest(
        @Nullable PlayerQuestProgress progress, @Nonnull String rowId
    ) {
        if (progress == null || !PlayerQuestIds.isPlayerQuestRow(rowId)) {
            return false;
        }
        String questId = PlayerQuestIds.parsePlayerQuestId(rowId);
        return questId != null && progress.hasQuestActive(questId);
    }

    @Nonnull
    private static Message journalQuestTitle(
        @Nonnull String rowId,
        @Nullable TownRecord town,
        @Nonnull WorldNpcPlayerProgress worldProgress,
        @Nullable PlayerQuestProgress playerProgress,
        @Nonnull QuestCatalog quests,
        @Nonnull QuestBoardCatalog boardCatalog,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (PlayerQuestIds.isPlayerQuestRow(rowId)) {
            String questId = PlayerQuestIds.parsePlayerQuestId(rowId);
            return questId != null ? quests.titleMessage(questId) : Message.raw("");
        }
        if (WorldQuestIds.isWorldQuestRow(rowId)) {
            String questId = WorldQuestIds.parseWorldQuestId(rowId);
            return questId != null ? quests.titleMessage(questId) : Message.raw("");
        }
        if (WorldQuestIds.isWorldBoardRow(rowId)) {
            String instanceId = WorldQuestIds.parseWorldBoardInstanceId(rowId);
            QuestBoardSlotRecord slot =
                instanceId != null ? WorldQuestBoardService.findAcceptedSlot(worldProgress, instanceId) : null;
            if (slot == null) {
                return Message.raw("");
            }
            return slot.getTitleLangKey() != null && !slot.getTitleLangKey().isBlank()
                ? Message.translation(slot.getTitleLangKey())
                : Message.raw(rowId);
        }
        if (QuestBoardService.isBoardJournalRow(rowId) && town != null) {
            String instanceId = QuestBoardService.parseJournalInstanceId(rowId);
            QuestBoardSlotRecord boardSlot = instanceId != null ? town.findBoardSlotByInstanceId(instanceId) : null;
            return boardSlot != null
                ? QuestBoardService.displayTitle(boardSlot, town, entityStore, boardCatalog)
                : Message.raw("");
        }
        if (town != null) {
            return quests.journalTitle(rowId, town, entityStore, plugin);
        }
        return quests.titleMessage(rowId);
    }

    private static void applyJournalQuestDetail(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String sel,
        @Nullable TownRecord town,
        @Nonnull WorldNpcPlayerProgress worldProgress,
        @Nullable PlayerQuestProgress playerProgress,
        @Nonnull QuestCatalog quests,
        @Nonnull QuestBoardCatalog boardCatalog,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> viewerRef
    ) {
        if (PlayerQuestIds.isPlayerQuestRow(sel)) {
            String questId = PlayerQuestIds.parsePlayerQuestId(sel);
            if (questId == null) {
                clearQuestDetailPane(commandBuilder);
                return;
            }
            commandBuilder.set("#QuestDetailTitle.TextSpans", quests.titleMessage(questId));
            commandBuilder.set("#QuestDetailDescription.TextSpans", quests.descriptionMessage(questId));
            applyQuestStepsHeading(commandBuilder, quests.hasObjectives(questId));
            if (quests.hasObjectives(questId)) {
                QuestJournalObjectivesUi.applyStoryQuest(
                    commandBuilder,
                    quests,
                    questId,
                    null,
                    null,
                    playerProgress,
                    entityStore,
                    plugin
                );
            } else {
                QuestJournalObjectivesUi.clear(commandBuilder);
            }
            applyQuestRewardPreview(commandBuilder, quests, questId);
            return;
        }
        if (WorldQuestIds.isWorldQuestRow(sel)) {
            String questId = WorldQuestIds.parseWorldQuestId(sel);
            if (questId == null) {
                clearQuestDetailPane(commandBuilder);
                return;
            }
            commandBuilder.set("#QuestDetailTitle.TextSpans", quests.titleMessage(questId));
            commandBuilder.set("#QuestDetailDescription.TextSpans", quests.descriptionMessage(questId));
            applyQuestStepsHeading(commandBuilder, quests.hasObjectives(questId));
            if (quests.hasObjectives(questId)) {
                QuestJournalObjectivesUi.applyStoryQuest(
                    commandBuilder,
                    quests,
                    questId,
                    null,
                    worldProgress,
                    null,
                    entityStore,
                    plugin
                );
            } else {
                QuestJournalObjectivesUi.clear(commandBuilder);
            }
            applyQuestRewardPreview(commandBuilder, quests, questId);
            return;
        }
        if (WorldQuestIds.isWorldBoardRow(sel)) {
            String instanceId = WorldQuestIds.parseWorldBoardInstanceId(sel);
            QuestBoardSlotRecord boardSlot =
                instanceId != null ? WorldQuestBoardService.findAcceptedSlot(worldProgress, instanceId) : null;
            if (boardSlot == null) {
                clearQuestDetailPane(commandBuilder);
                return;
            }
            String questRank = boardSlot.getQuestRank() != null ? boardSlot.getQuestRank() : "E";
            Message title =
                boardSlot.getTitleLangKey() != null && !boardSlot.getTitleLangKey().isBlank()
                    ? Message.translation(boardSlot.getTitleLangKey())
                    : Message.raw(sel);
            Message desc =
                boardSlot.getDescriptionLangKey() != null && !boardSlot.getDescriptionLangKey().isBlank()
                    ? Message.translation(boardSlot.getDescriptionLangKey())
                    : Message.raw("");
            commandBuilder.set(
                "#QuestDetailTitle.TextSpans",
                title.insert(Message.raw("  ")).insert(Message.raw("[" + questRank + "]"))
            );
            commandBuilder.set(
                "#QuestDetailDescription.TextSpans",
                desc.insert(Message.raw("\n\n"))
                    .insert(
                        Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.daysLeft")
                            .param("days", String.valueOf(QuestBoardService.daysRemaining(boardSlot)))
                    )
            );
            applyQuestStepsHeading(commandBuilder, false);
            QuestJournalObjectivesUi.clear(commandBuilder);
            if (town != null) {
                applyBoardQuestRewardPreview(commandBuilder, boardSlot, entityStore, town);
            } else {
                commandBuilder.set("#RewardRow.Visible", false);
                commandBuilder.set("#RewardReputationLine.Visible", false);
                commandBuilder.set("#RewardFallback.Visible", false);
            }
            return;
        }
        if (QuestBoardService.isBoardJournalRow(sel) && town != null) {
            String instanceId = QuestBoardService.parseJournalInstanceId(sel);
            QuestBoardSlotRecord boardSlot = instanceId != null ? town.findBoardSlotByInstanceId(instanceId) : null;
            if (boardSlot != null) {
                String questRank = boardSlot.getQuestRank() != null ? boardSlot.getQuestRank() : "E";
                commandBuilder.set(
                    "#QuestDetailTitle.TextSpans",
                    QuestBoardService.displayTitle(boardSlot, town, entityStore, boardCatalog)
                        .insert(Message.raw("  "))
                        .insert(Message.raw("[" + questRank + "]"))
                );
                commandBuilder.set(
                    "#QuestDetailDescription.TextSpans",
                    QuestBoardService.displayDescription(boardSlot, town, entityStore, boardCatalog)
                        .insert(Message.raw("\n\n"))
                        .insert(
                            Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.daysLeft")
                                .param("days", String.valueOf(QuestBoardService.daysRemaining(boardSlot)))
                        )
                );
                applyQuestStepsHeading(commandBuilder, true);
                QuestJournalObjectivesUi.applyBoardQuest(
                    commandBuilder,
                    boardSlot,
                    town,
                    entityStore,
                    boardCatalog,
                    viewerRef
                );
                applyBoardQuestRewardPreview(commandBuilder, boardSlot, entityStore, town);
            }
            return;
        }
        if (town == null) {
            clearQuestDetailPane(commandBuilder);
            return;
        }
        commandBuilder.set("#QuestDetailTitle.TextSpans", quests.journalTitle(sel, town, entityStore, plugin));
        commandBuilder.set("#QuestDetailDescription.TextSpans", quests.journalDescription(sel, town, entityStore, plugin));
        applyQuestStepsHeading(commandBuilder, quests.hasObjectives(sel));
        if (quests.hasObjectives(sel)) {
            QuestJournalObjectivesUi.applyStoryQuest(
                commandBuilder,
                quests,
                sel,
                town,
                worldProgress,
                null,
                entityStore,
                plugin
            );
        } else {
            QuestJournalObjectivesUi.clear(commandBuilder);
        }
        applyQuestRewardPreview(commandBuilder, quests, sel);
    }

    private static void applyQuestStepsHeading(@Nonnull UICommandBuilder commandBuilder, boolean visible) {
        commandBuilder.set("#QuestStepsHeading.Visible", visible);
        if (visible) {
            commandBuilder.set(
                "#QuestStepsHeading.TextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.stepsHeading")
            );
        } else {
            commandBuilder.set("#QuestStepsHeading.TextSpans", Message.raw(""));
        }
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
        applyQuestStepsHeading(commandBuilder, false);
        QuestJournalObjectivesUi.clear(commandBuilder);
        commandBuilder.set("#RewardRow.Visible", false);
        commandBuilder.set("#RewardReputationLine.Visible", false);
        commandBuilder.set("#RewardReputationLine.TextSpans", Message.raw(""));
        commandBuilder.set("#RewardFallback.Visible", false);
        commandBuilder.set("#RewardSlot.Slots", new ItemGridSlot[]{new ItemGridSlot()});
        commandBuilder.set("#RewardQuantity.TextSpans", Message.raw(""));
        commandBuilder.set("#RewardTitle.TextSpans", Message.raw(""));
    }

    private static void applyQuestRewardPreview(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull QuestCatalog quests,
        @Nonnull String questId
    ) {
        QuestCatalog.FirstItemReward itemRw = quests.firstItemReward(questId);
        QuestCatalog.QuestReputationGrant repRw = quests.findQuestBeneficiaryReputation(questId);
        boolean hasItem = itemRw != null;
        boolean hasRep = repRw != null;

        if (hasItem) {
            commandBuilder.set("#RewardRow.Visible", true);
            ItemGridSlot rewardSlot = AetherhavenUiItemGrids.slotForKnownItem(itemRw.itemId(), itemRw.count());
            commandBuilder.set(
                "#RewardSlot.Slots",
                new ItemGridSlot[]{rewardSlot != null ? rewardSlot : new ItemGridSlot()}
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
            commandBuilder.set("#RewardSlot.Slots", new ItemGridSlot[]{new ItemGridSlot()});
            commandBuilder.set("#RewardQuantity.TextSpans", Message.raw(""));
            commandBuilder.set("#RewardTitle.TextSpans", Message.raw(""));
        }

        if (hasRep) {
            commandBuilder.set("#RewardReputationLine.Visible", true);
            commandBuilder.set(
                "#RewardReputationLine.TextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.rewardReputationLine")
                    .param("amount", String.valueOf(repRw.amount()))
                    .param(
                        "villager",
                        Message.translation(
                            "aetherhaven_ui_journal_items_tail.npcRoles." + repRw.beneficiaryRoleId() + ".name"
                        )
                    )
            );
        } else {
            commandBuilder.set("#RewardReputationLine.Visible", false);
            commandBuilder.set("#RewardReputationLine.TextSpans", Message.raw(""));
        }

        if (!hasItem && !hasRep) {
            commandBuilder.set("#RewardFallback.Visible", true);
            commandBuilder.set(
                "#RewardFallback.TextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.rewardFallback")
            );
        } else {
            commandBuilder.set("#RewardFallback.Visible", false);
            commandBuilder.set("#RewardFallback.TextSpans", Message.raw(""));
        }
    }

    private static void applyBoardQuestRewardPreview(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town
    ) {
        List<com.hexvane.aetherhaven.quest.data.QuestReward> itemRewards = QuestBoardService.itemRewards(slot);
        QuestRewardService.ReputationRewardPreview repRw = QuestBoardService.firstReputationReward(slot);
        boolean hasItems = !itemRewards.isEmpty();
        boolean hasRep = repRw != null;

        if (hasItems) {
            commandBuilder.set("#RewardRow.Visible", true);
            if (itemRewards.size() == 1) {
                com.hexvane.aetherhaven.quest.data.QuestReward itemRw = itemRewards.get(0);
                commandBuilder.set("#RewardTextCluster.Visible", true);
                commandBuilder.set(
                    "#RewardSlot.Slots",
                    new ItemGridSlot[]{new ItemGridSlot(new ItemStack(itemRw.itemId().trim(), Math.max(1, itemRw.count())))}
                );
                commandBuilder.set("#RewardQuantity.TextSpans", Message.raw(String.valueOf(Math.max(1, itemRw.count()))));
                Item assetItem = Item.getAssetMap().getAsset(itemRw.itemId().trim());
                if (assetItem != null
                    && assetItem.getTranslationKey() != null
                    && !assetItem.getTranslationKey().isBlank()) {
                    commandBuilder.set("#RewardTitle.TextSpans", Message.translation(assetItem.getTranslationKey()));
                } else {
                    commandBuilder.set("#RewardTitle.TextSpans", Message.raw(itemRw.itemId().trim()));
                }
            } else {
                ItemGridSlot[] gridSlots = new ItemGridSlot[itemRewards.size()];
                for (int i = 0; i < itemRewards.size(); i++) {
                    com.hexvane.aetherhaven.quest.data.QuestReward itemRw = itemRewards.get(i);
                    gridSlots[i] = new ItemGridSlot(new ItemStack(itemRw.itemId().trim(), Math.max(1, itemRw.count())));
                }
                commandBuilder.set("#RewardSlot.Slots", gridSlots);
                commandBuilder.set("#RewardTextCluster.Visible", false);
                commandBuilder.set("#RewardQuantity.TextSpans", Message.raw(""));
                commandBuilder.set("#RewardTitle.TextSpans", Message.raw(""));
            }
        } else {
            commandBuilder.set("#RewardRow.Visible", false);
            commandBuilder.set("#RewardTextCluster.Visible", false);
            commandBuilder.set("#RewardSlot.Slots", new ItemGridSlot[]{new ItemGridSlot()});
            commandBuilder.set("#RewardQuantity.TextSpans", Message.raw(""));
            commandBuilder.set("#RewardTitle.TextSpans", Message.raw(""));
        }

        if (hasRep) {
            commandBuilder.set("#RewardReputationLine.Visible", true);
            String roleId = repRw.npcRoleId();
            if (roleId == null || roleId.isBlank()) {
                roleId = slot.getGiverRoleId();
            }
            Message villagerName =
                roleId != null && !roleId.isBlank()
                    ? Message.translation("aetherhaven_ui_journal_items_tail.npcRoles." + roleId.trim() + ".name")
                    : Message.raw(com.hexvane.aetherhaven.questboard.QuestBoardGiverDisplay.giverName(slot, store, town));
            commandBuilder.set(
                "#RewardReputationLine.TextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.rewardReputationLine")
                    .param("amount", String.valueOf(repRw.amount()))
                    .param("villager", villagerName)
            );
        } else {
            commandBuilder.set("#RewardReputationLine.Visible", false);
            commandBuilder.set("#RewardReputationLine.TextSpans", Message.raw(""));
        }

        if (!hasItems && !hasRep) {
            commandBuilder.set("#RewardFallback.Visible", true);
            commandBuilder.set(
                "#RewardFallback.TextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.rewardFallback")
            );
        } else {
            commandBuilder.set("#RewardFallback.Visible", false);
            commandBuilder.set("#RewardFallback.TextSpans", Message.raw(""));
        }
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
        if (action.equalsIgnoreCase("SelectActiveTown")) {
            if (data.activeTownId == null || data.activeTownId.isBlank()) {
                return;
            }
            UUID townId;
            try {
                townId = UUID.fromString(data.activeTownId.trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (plugin == null || uc == null) {
                return;
            }
            World world = store.getExternalData().getWorld();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.getTown(townId);
            if (town == null || !town.hasMemberOrOwner(uc.getUuid())) {
                return;
            }
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
            }
            st.setActiveTownId(townId);
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            TownBorderMapOverlayService.refreshPlayer(world, uc.getUuid());
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
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
        if (action.equalsIgnoreCase("DialogueSpeechToggle")) {
            if (data.checked == null) {
                return;
            }
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
            }
            st.setDialogueSpeechEnabled(data.checked);
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            return;
        }
        if (action.equalsIgnoreCase("DialogueSpeechVolumePreview")) {
            int volumePercent = data.speechVolumePercent != null
                ? Math.max(0, Math.min(100, data.speechVolumePercent))
                : 70;
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
            }
            st.setDialogueSpeechVolumePercent(volumePercent);
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#SettingsSpeechVolumeValue.TextSpans", Message.raw(volumePercent + "%"));
            sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }
        if (action.equalsIgnoreCase("Tab")) {
            String tabId = data.tabId;
            PlayerTownJournalState.JournalTab tab = parseTab(tabId);
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
                store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            }
            st.setLastTab(tab);
            if (tab == PlayerTownJournalState.JournalTab.QUESTS) {
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
                if (plugin != null && uuid != null) {
                    World w = store.getExternalData().getWorld();
                    TownRecord town = journalTown(w, store, ref, plugin, uuid.getUuid());
                    WorldNpcPlayerProgress worldProgress =
                        AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(w, plugin)
                            .getOrCreatePlayerProgress(uuid.getUuid());
                    PlayerQuestProgress playerProgress = store.getComponent(ref, PlayerQuestProgress.getComponentType());
                    Set<String> activeIds =
                        new HashSet<>(collectActiveJournalQuestIds(town, uuid.getUuid(), worldProgress, playerProgress));
                    st.retainPinnedQuests(activeIds);
                }
            }
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            abandonConfirmOpen = false;
            pendingAbandonQuestId = null;
            plotRemoveConfirmOpen = false;
            pendingRemovePlotId = null;
            journalSettingsPlotModalOpen = false;
            journalSettingsVillagerModalOpen = false;
            journalSettingsResetConfirmOpen = false;
            journalSettingsFormSnapshot = null;
            journalSettingsPersonalStatus = null;
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
        if (action.equalsIgnoreCase("ToggleQuestPin")) {
            String qid = data.questId;
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (qid == null || qid.isBlank() || plugin == null || uc == null) {
                return;
            }
            World world = store.getExternalData().getWorld();
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            WorldNpcPlayerProgress worldProgress =
                AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin)
                    .getOrCreatePlayerProgress(uc.getUuid());
            PlayerQuestProgress playerProgress = store.getComponent(ref, PlayerQuestProgress.getComponentType());
            String id = qid.trim();
            List<String> active = collectActiveJournalQuestIds(town, uc.getUuid(), worldProgress, playerProgress);
            if (!active.contains(id)) {
                return;
            }
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
            }
            st.retainPinnedQuests(new HashSet<>(active));
            if (st.isQuestPinned(id)) {
                st.unpinQuest(id);
            } else {
                st.pinQuest(id);
            }
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
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
            String sel = selectedQuestId.trim();
            WorldNpcPlayerProgress worldProgress =
                AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin)
                    .getOrCreatePlayerProgress(uc.getUuid());
            PlayerQuestProgress playerProgress = store.getComponent(ref, PlayerQuestProgress.getComponentType());
            boolean worldOk = isActiveWorldJournalQuest(worldProgress, sel);
            boolean playerOk = isActivePlayerJournalQuest(playerProgress, sel);
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            boolean townOk =
                town != null
                    && town.playerCanAbandonQuests(uc.getUuid())
                    && QuestBoardService.isActiveJournalQuest(town, sel);
            if (!worldOk && !playerOk && !townOk) {
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
        if (action.equalsIgnoreCase("OpenVillagerNeeds")) {
            String villagerUuidRaw = data.villagerUuid;
            if (villagerUuidRaw == null || villagerUuidRaw.isBlank()) {
                return;
            }
            UUID villagerUuid;
            try {
                villagerUuid = UUID.fromString(villagerUuidRaw.trim());
            } catch (IllegalArgumentException e) {
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            if (town == null) {
                return;
            }
            VillagerNeedsOverviewPage.openForVillager(playerRef, ref, store, town.getTownId(), villagerUuid);
            return;
        }
        if (action.equalsIgnoreCase("OpenPlotMaterials")) {
            String plotIdRaw = data.plotId;
            if (plotIdRaw == null || plotIdRaw.isBlank()) {
                return;
            }
            UUID plotUuid;
            try {
                plotUuid = UUID.fromString(plotIdRaw.trim());
            } catch (IllegalArgumentException e) {
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            if (town == null) {
                return;
            }
            OpenResult openResult = PlotConstructionOpenHelper.tryOpenFromJournal(playerRef, ref, store, town, plotUuid);
            switch (openResult) {
                case CHUNK_NOT_LOADED -> playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.removePlotAreaNotLoaded")
                );
                case ASSEMBLING -> playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.assemblingHint")
                );
                case COMPLETE -> playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.plotMaterialsComplete")
                );
                default -> {}
            }
            return;
        }
        if (action.equalsIgnoreCase("ToggleLocateVillager")) {
            String villagerUuidRaw = data.villagerUuid;
            if (villagerUuidRaw == null || villagerUuidRaw.isBlank()) {
                return;
            }
            UUID villagerUuid;
            try {
                villagerUuid = UUID.fromString(villagerUuidRaw.trim());
            } catch (IllegalArgumentException e) {
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            if (town == null) {
                return;
            }
            VillagerLocatePlayerComponent session = VillagerLocatePlayerComponent.get(store, ref);
            if (session != null && session.isActiveFor(villagerUuid)) {
                VillagerLocatePlayerComponent.clear(store, ref);
                playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateStopped")
                );
            } else {
                PlotLocatePlayerComponent.clear(store, ref);
                boolean listed = false;
                String label = "";
                for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
                    if (villagerUuid.equals(row.entityUuid())) {
                        listed = true;
                        label = row.label();
                        break;
                    }
                }
                if (!listed) {
                    playerRef.sendMessage(
                        Message.translation(
                            "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateNotResident"
                        )
                    );
                    return;
                }
                ResidentLastKnownPositionService.LocateTarget target =
                    ResidentLastKnownPositionService.resolveLocateTarget(store, town, villagerUuid);
                if (!target.isValid()) {
                    playerRef.sendMessage(
                        Message.translation(
                            "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateNoPosition"
                        )
                    );
                    return;
                }
                VillagerLocatePlayerComponent.start(
                    store,
                    ref,
                    town.getTownId(),
                    villagerUuid,
                    label,
                    target.isLastKnown()
                );
                if (target.isLastKnown()) {
                    playerRef.sendMessage(
                        Message.translation(
                                "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateStartedLastKnown"
                            )
                            .param("name", label)
                    );
                } else {
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateStarted")
                            .param("name", label)
                    );
                }
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("ToggleLocatePlot")) {
            String plotIdRaw = data.plotId;
            if (plotIdRaw == null || plotIdRaw.isBlank()) {
                return;
            }
            UUID plotUuid;
            try {
                plotUuid = UUID.fromString(plotIdRaw.trim());
            } catch (IllegalArgumentException e) {
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            if (town == null) {
                return;
            }
            PlotInstance plot = town.findPlotById(plotUuid);
            if (plot == null) {
                return;
            }
            PlotLocatePlayerComponent plotSession = PlotLocatePlayerComponent.get(store, ref);
            if (plotSession != null && plotSession.isActiveFor(plotUuid)) {
                PlotLocatePlayerComponent.clear(store, ref);
                playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locatePlotStopped")
                );
            } else {
                VillagerLocatePlayerComponent.clear(store, ref);
                PlotLocateTargetResolver.PlotLocateTarget target = PlotLocateTargetResolver.resolve(plot);
                if (!target.valid()) {
                    return;
                }
                Message titleMsg = journalPlotConstructionTitle(plugin.getConstructionCatalog(), plot);
                String label = titleMsg.getAnsiMessage();
                PlotLocatePlayerComponent.start(store, ref, town.getTownId(), plotUuid, label);
                playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locatePlotStarted")
                        .param("name", label)
                );
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("RepairPlot")) {
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            UUID plotUuid = tryParseUuid(pid);
            PlotInstance plot = town != null && plotUuid != null ? town.findPlotById(plotUuid) : null;
            if (town == null || plot == null || !town.playerCanManageConstructions(uc.getUuid())) {
                return;
            }
            if (!PlotFootprintChunkUtil.isPlotRepairAreaLoaded(world, plot)) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.repairPlotNotLoaded")
                );
                return;
            }
            TownRecord townRun = town;
            PlotInstance plotRun = plot;
            world.execute(
                () -> {
                    if (isDismissed() || !ref.isValid()) {
                        return;
                    }
                    PlotLinkReconcileService.PlotRepairReport rep =
                        TownJournalAdminService.repairSinglePlot(world, plugin, townRun, plotRun.getPlotId(), store);
                    if (rep.getSkippedChunkUnloaded() > 0) {
                        playerRef.sendMessage(
                            Message.translation(
                                "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.repairPlotNotLoaded"
                            )
                        );
                        return;
                    }
                    if (rep.getFailed() > 0) {
                        playerRef.sendMessage(
                            Message.translation(
                                "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.repairPlotFailed"
                            )
                        );
                        return;
                    }
                    if (rep.isBlueprintingPlot()) {
                        String signMsgKey =
                            rep.hadFixes()
                                ? "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.repairPlotSignFixed"
                                : "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.repairPlotSignAt";
                        playerRef.sendMessage(
                            Message.translation(signMsgKey)
                                .param("x", String.valueOf(plotRun.getSignX()))
                                .param("y", String.valueOf(plotRun.getSignY()))
                                .param("z", String.valueOf(plotRun.getSignZ()))
                        );
                    } else if (rep.hadFixes()) {
                        int fixes = rep.getRelinked() + rep.getPlacedBlocks();
                        playerRef.sendMessage(
                            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.repairPlotOk")
                                .param("count", String.valueOf(fixes))
                        );
                    } else {
                        playerRef.sendMessage(
                            Message.translation(
                                "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.repairPlotNothing"
                            )
                        );
                    }
                    UICommandBuilder cmd = new UICommandBuilder();
                    UIEventBuilder ev = new UIEventBuilder();
                    build(ref, cmd, ev, store);
                    sendUpdate(cmd, ev, false);
                }
            );
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
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
            ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
            Player player = store.getComponent(ref, Player.getComponentType());
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            Vector3d dropPos =
                tc != null
                    ? new Vector3d(tc.getPosition())
                    : new Vector3d(plot.getSignX() + 0.5, plot.getSignY(), plot.getSignZ() + 0.5);
            PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            TownDissolutionService.clearPlotFromWorld(world, plugin, town, plot, store, reg, PlotBlockClearMode.FULL_FOOTPRINT, null, ref);
            FestivalService.onFestivalSquareRemoved(world, store, plugin, tm, town, plot);
            if (!town.removePlotInstance(plotUuid)) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.removePlotFailed")
                );
                plotRemoveConfirmOpen = false;
                pendingRemovePlotId = null;
                return;
            }
            RefundResult refundResult = RefundResult.NONE;
            if (def != null && player != null) {
                refundResult =
                    PlotJournalRemovalRefundService.applyRefunds(
                        town,
                        plot,
                        def,
                        world,
                        plugin,
                        player,
                        ref,
                        store,
                        dropPos
                    );
            }
            tm.updateTown(town);
            playerRef.sendMessage(Message.translation(PlotJournalRemovalRefundService.successLangKey(refundResult)));
            plotRemoveConfirmOpen = false;
            pendingRemovePlotId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("SettingsSubTab")) {
            PlayerTownJournalState.SettingsSubTab subTab = parseSettingsSubTab(data.subTabId);
            if (subTab == PlayerTownJournalState.SettingsSubTab.SERVER && !JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
                store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            }
            st.setLastSettingsSubTab(subTab);
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            journalSettingsPersonalStatus = null;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("HudOpacityPreview")) {
            int opacityPercent = data.hudOpacityPercent != null
                ? Math.max(0, Math.min(100, data.hudOpacityPercent))
                : 0;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#SettingsHudOpacityValue.TextSpans", Message.raw(opacityPercent + "%"));
            sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }
        if (action.equalsIgnoreCase("PersonalSettingsSave")) {
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
            }
            float fovDef = st.effectiveRtsPickVerticalFovDeg();
            float aspectDef = st.effectiveRtsPickAspectRatio();
            float fov = (float) parseDoubleSafe(data.rtsFov, 30.0, 120.0, fovDef);
            float aspect = (float) parseDoubleSafe(data.rtsAspect, 0.5, 3.0, aspectDef);
            st.setRtsPickOverrides(fov, aspect);
            st.setHudPreferences(
                Boolean.TRUE.equals(data.hudTime),
                Boolean.TRUE.equals(data.hudDate),
                Boolean.TRUE.equals(data.hudGold),
                Boolean.TRUE.equals(data.hudQuests),
                data.hudStatusPlacement,
                parseIntSafe(data.hudStatusX, 0, 4000, st.getHudStatusX()),
                parseIntSafe(data.hudStatusY, 0, 4000, st.getHudStatusY()),
                data.hudQuestPlacement,
                parseIntSafe(data.hudQuestX, 0, 4000, st.getHudQuestX()),
                parseIntSafe(data.hudQuestY, 0, 4000, st.getHudQuestY())
            );
            st.setHudBackgroundOpacity(
                data.hudOpacityPercent != null
                    ? data.hudOpacityPercent / 100f
                    : st.getHudBackgroundOpacity()
            );
            st.setDialogueSpeechPreferences(
                data.speechEnabled == null || Boolean.TRUE.equals(data.speechEnabled),
                data.speechVolumePercent != null ? data.speechVolumePercent : st.getDialogueSpeechVolumePercent()
            );
            Season birthdaySeason = PlayerBirthdayService.parseSeason(
                data.birthdaySeason,
                st.getBirthdaySeason() != null ? st.getBirthdaySeason() : settingsToday(store).season()
            );
            int birthdayDay = PlayerBirthdayService.parseDay(
                data.birthdayDay,
                st.getBirthdayDay() >= 1 ? st.getBirthdayDay() : settingsToday(store).dayOfSeason()
            );
            if (birthdaySeason != null) {
                st.setBirthday(birthdaySeason, birthdayDay);
            }
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            refreshActiveRtsPickTuning(ref, store);
            journalSettingsPersonalStatus =
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.personalSaveOk");
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("PersonalSettingsReset")) {
            PlayerTownJournalState st = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (st == null) {
                st = new PlayerTownJournalState();
            }
            st.clearRtsPickOverrides();
            st.resetHudPreferences();
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), st);
            refreshActiveRtsPickTuning(ref, store);
            journalSettingsPersonalStatus =
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.personalResetOk");
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("OpenDifficulty")) {
            if (!JournalTabVisibility.difficultyTab()) {
                return;
            }
            if (!JournalSettingsAccess.canOpen(store, ref)) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            // openCustomPage replaces the journal; do not require getCustomPage() == null.
            player.getPageManager().openCustomPage(ref, store, new DifficultyPage(playerRef));
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
            int shopMemberPct = parseIntSafe(
                data.shopMemberPct,
                1,
                100,
                parseSrc.getShopSpotPlayerListingPricePercent()
            );
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
                giftMaxDays,
                shopMemberPct
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            if (town == null) {
                return;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                return;
            }
            String err = TownJournalAdminService.resetTownVillagersNearPlayer(world, plugin, town, store, new Vector3d(tc.getPosition()));
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
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
        if (action.equalsIgnoreCase("JournalDedupeVillagers")) {
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            if (town == null) {
                return;
            }
            TownResidentReconcileService.ReconcileReport rep = TownJournalAdminService.dedupeVillagers(world, plugin, town);
            playerRef.sendMessage(
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.dedupeVillagersDone")
                    .param("removed", String.valueOf(rep.getRemovedDuplicateEntities()))
                    .param("synced", String.valueOf(rep.getSyncedRoles()))
            );
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (action.equalsIgnoreCase("JournalRepairPlots")) {
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
            if (town == null) {
                return;
            }
            PlotLinkReconcileService.TownRepairReport rep = TownJournalAdminService.repairPlots(world, plugin, town);
            playerRef.sendMessage(
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.plotsRepairDone")
                    .param("scanned", String.valueOf(rep.getScanned()))
                    .param("relinked", String.valueOf(rep.getRelinked()))
                    .param("skipped", String.valueOf(rep.getSkippedChunkUnloaded()))
                    .param("orphans", String.valueOf(rep.getOrphans()))
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
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
            TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
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
            String id = qid.trim();
            if (PlayerQuestIds.isPlayerQuestRow(id)) {
                String playerQuestId = PlayerQuestIds.parsePlayerQuestId(id);
                PlayerQuestProgress playerProgress = store.getComponent(ref, PlayerQuestProgress.getComponentType());
                if (playerQuestId != null && playerProgress != null) {
                    PlayerQuestProgressionService.abandonQuest(playerProgress, playerQuestId);
                    store.putComponent(ref, PlayerQuestProgress.getComponentType(), playerProgress);
                }
            } else if (WorldQuestIds.isWorldQuestRow(id)) {
                String worldQuestId = WorldQuestIds.parseWorldQuestId(id);
                if (worldQuestId != null) {
                    WorldQuestProgressionService.abandonQuest(plugin, world, uc.getUuid(), worldQuestId);
                }
            } else if (WorldQuestIds.isWorldBoardRow(id)) {
                String instanceId = WorldQuestIds.parseWorldBoardInstanceId(id);
                if (instanceId != null) {
                    WorldQuestBoardService.abandonByInstanceId(world, plugin, uc.getUuid(), instanceId);
                }
            } else {
                TownRecord town = journalTown(world, store, ref, plugin, uc.getUuid());
                if (town == null || !town.playerCanAbandonQuests(uc.getUuid())) {
                    abandonConfirmOpen = false;
                    pendingAbandonQuestId = null;
                    return;
                }
                JsonObject a = new JsonObject();
                if (QuestBoardService.isBoardJournalRow(id)) {
                    String instanceId = QuestBoardService.parseJournalInstanceId(id);
                    TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                    if (
                        instanceId != null
                            && QuestBoardService.abandonByInstanceId(
                                town,
                                uc.getUuid(),
                                instanceId,
                                plugin.getQuestBoardCatalog(),
                                world,
                                store
                            )
                    ) {
                        tm.updateTown(town);
                    }
                } else {
                    a.addProperty("type", "abandon_quest");
                    a.addProperty("id", id);
                    DialogueActionExecutor ex = new DialogueActionExecutor();
                    ex.runBatch(List.of(a), ref, store, new DialogueActionBatchResult());
                }
            }
            if (id.equals(selectedQuestId)) {
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

    private void scheduleCoerceSettingsSubTabFromServerIfStillIllegal(@Nonnull World world) {
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
            if (js.getLastSettingsSubTab() != PlayerTownJournalState.SettingsSubTab.SERVER) {
                return;
            }
            if (JournalSettingsAccess.canOpen(st, pref)) {
                return;
            }
            js.setLastSettingsSubTab(PlayerTownJournalState.SettingsSubTab.PERSONAL);
            st.putComponent(pref, PlayerTownJournalState.getComponentType(), js);
        });
    }

    private static void refreshActiveRtsPickTuning(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        RtsCommandPlayerComponent session = store.getComponent(ref, RtsCommandPlayerComponent.getComponentType());
        if (session == null || !session.isActive()) {
            return;
        }
        PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        session.setPickTuning(RtsPickTuning.fromJournal(journal));
        RtsScreenPickUtil.refreshPickViewHeight(session, store);
        session.clearOrthoCalibration();
        store.putComponent(ref, RtsCommandPlayerComponent.getComponentType(), session);
    }

    @Nonnull
    private static PlayerTownJournalState.SettingsSubTab parseSettingsSubTab(@Nullable String subTabId) {
        if (subTabId == null || subTabId.isBlank()) {
            return PlayerTownJournalState.SettingsSubTab.PERSONAL;
        }
        return PlayerTownJournalState.SettingsSubTab.fromPersisted(subTabId);
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
            .append(new KeyedCodec<>("VillagerUuid", Codec.STRING), (d, v) -> d.villagerUuid = v, d -> d.villagerUuid)
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
            .append(new KeyedCodec<>("@ShopMemberPct", Codec.STRING), (d, v) -> d.shopMemberPct = v, d -> d.shopMemberPct)
            .add()
            .append(new KeyedCodec<>("@PlotPick", Codec.STRING), (d, v) -> d.plotPick = v, d -> d.plotPick)
            .add()
            .append(new KeyedCodec<>("@VillagerPick", Codec.STRING), (d, v) -> d.villagerPick = v, d -> d.villagerPick)
            .add()
            .append(new KeyedCodec<>("SubTabId", Codec.STRING), (d, v) -> d.subTabId = v, d -> d.subTabId)
            .add()
            .append(new KeyedCodec<>("@RtsFov", Codec.STRING), (d, v) -> d.rtsFov = v, d -> d.rtsFov)
            .add()
            .append(new KeyedCodec<>("@RtsAspect", Codec.STRING), (d, v) -> d.rtsAspect = v, d -> d.rtsAspect)
            .add()
            .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (d, v) -> d.checked = v, d -> d.checked)
            .add()
            .append(new KeyedCodec<>("@HudTime", Codec.BOOLEAN), (d, v) -> d.hudTime = v, d -> d.hudTime)
            .add()
            .append(new KeyedCodec<>("@HudDate", Codec.BOOLEAN), (d, v) -> d.hudDate = v, d -> d.hudDate)
            .add()
            .append(new KeyedCodec<>("@HudGold", Codec.BOOLEAN), (d, v) -> d.hudGold = v, d -> d.hudGold)
            .add()
            .append(new KeyedCodec<>("@HudQuests", Codec.BOOLEAN), (d, v) -> d.hudQuests = v, d -> d.hudQuests)
            .add()
            .append(
                new KeyedCodec<>("@HudOpacity", Codec.INTEGER),
                (d, v) -> d.hudOpacityPercent = v,
                d -> d.hudOpacityPercent
            )
            .add()
            .append(new KeyedCodec<>("@SpeechEnabled", Codec.BOOLEAN), (d, v) -> d.speechEnabled = v, d -> d.speechEnabled)
            .add()
            .append(
                new KeyedCodec<>("@SpeechVolume", Codec.INTEGER),
                (d, v) -> d.speechVolumePercent = v,
                d -> d.speechVolumePercent
            )
            .add()
            .append(new KeyedCodec<>("@HudStatusPlacement", Codec.STRING), (d, v) -> d.hudStatusPlacement = v, d -> d.hudStatusPlacement)
            .add()
            .append(new KeyedCodec<>("@HudStatusX", Codec.STRING), (d, v) -> d.hudStatusX = v, d -> d.hudStatusX)
            .add()
            .append(new KeyedCodec<>("@HudStatusY", Codec.STRING), (d, v) -> d.hudStatusY = v, d -> d.hudStatusY)
            .add()
            .append(new KeyedCodec<>("@HudQuestPlacement", Codec.STRING), (d, v) -> d.hudQuestPlacement = v, d -> d.hudQuestPlacement)
            .add()
            .append(new KeyedCodec<>("@HudQuestX", Codec.STRING), (d, v) -> d.hudQuestX = v, d -> d.hudQuestX)
            .add()
            .append(new KeyedCodec<>("@HudQuestY", Codec.STRING), (d, v) -> d.hudQuestY = v, d -> d.hudQuestY)
            .add()
            .append(new KeyedCodec<>("@BirthdaySeason", Codec.STRING), (d, v) -> d.birthdaySeason = v, d -> d.birthdaySeason)
            .add()
            .append(new KeyedCodec<>("@BirthdayDay", Codec.STRING), (d, v) -> d.birthdayDay = v, d -> d.birthdayDay)
            .add()
            .append(new KeyedCodec<>("@ActiveTownId", Codec.STRING), (d, v) -> d.activeTownId = v, d -> d.activeTownId)
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
        private String villagerUuid;
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
        private String shopMemberPct;
        @Nullable
        private String plotPick;
        @Nullable
        private String villagerPick;
        @Nullable
        private String subTabId;
        @Nullable
        private String rtsFov;
        @Nullable
        private String rtsAspect;
        @Nullable
        private Boolean checked;
        @Nullable
        private Boolean hudTime;
        @Nullable
        private Boolean hudDate;
        @Nullable
        private Boolean hudGold;
        @Nullable
        private Boolean hudQuests;
        @Nullable
        private Integer hudOpacityPercent;
        @Nullable
        private Boolean speechEnabled;
        @Nullable
        private Integer speechVolumePercent;
        @Nullable
        private String hudStatusPlacement;
        @Nullable
        private String hudStatusX;
        @Nullable
        private String hudStatusY;
        @Nullable
        private String hudQuestPlacement;
        @Nullable
        private String hudQuestX;
        @Nullable
        private String hudQuestY;
        @Nullable
        private String birthdaySeason;
        @Nullable
        private String birthdayDay;
        @Nullable
        private String activeTownId;
    }
}
