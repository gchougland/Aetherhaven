package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.dialogue.DialogueActionExecutor;
import com.hexvane.aetherhaven.guide.GuideMarkdownUiAppender;
import com.hexvane.aetherhaven.guide.GuideScheduleWeekAppender;
import com.hexvane.aetherhaven.guide.GuideTopicFile;
import com.hexvane.aetherhaven.guide.GuideTopicRepository;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
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
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestJournalPage extends InteractiveCustomUIPage<QuestJournalPage.PageData> {
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

        PlayerTownJournalState journalState = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        if (journalState == null) {
            journalState = new PlayerTownJournalState();
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), journalState);
        }
        PlayerTownJournalState.JournalTab currentTab = journalState.getLastTab();

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
        World world = store.getExternalData().getWorld();
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

        commandBuilder.set("#JournalAbandonModal.Visible", abandonModalBlocking);
        commandBuilder.set("#JournalPlotRemoveModal.Visible", plotModalBlocking);
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

        if (currentTab == PlayerTownJournalState.JournalTab.GUIDE) {
            buildGuideTab(commandBuilder, eventBuilder, plugin, store);
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
        int np = Math.min(plots.size(), MAX_TOWN_PLOTS);
        for (int i = 0; i < np; i++) {
            PlotInstance p = plots.get(i);
            commandBuilder.append(TOWN_PLOT_ROWS, "Aetherhaven/TownJournalPlotRow.ui");
            String row = TOWN_PLOT_ROWS + "[" + i + "]";
            String gameplayCid = plugin.getConstructionCatalog().resolveGameplayConstructionId(p.getConstructionId());
            ConstructionDefinition cdef = plugin.getConstructionCatalog().get(gameplayCid);
            String title =
                cdef != null
                    ? cdef.getDisplayName()
                    : (p.getConstructionId() != null && !p.getConstructionId().isBlank()
                        ? p.getConstructionId().trim()
                        : "?");
            commandBuilder.set(row + " #PlotTitle.TextSpans", Message.raw(title));
            String coords = p.getSignX() + " " + p.getSignY() + " " + p.getSignZ();
            commandBuilder.set(row + " #PlotCoords.TextSpans", Message.raw(coords));
            PlotInstanceState pst = p.getState();
            commandBuilder.set(row + " #PlotStatus.TextSpans", Message.translation(plotStatusLangKey(pst)));
            String tokenId = cdef != null ? cdef.getPlotTokenItemId() : null;
            if (tokenId != null && !tokenId.isBlank()) {
                commandBuilder.set(
                    row + " #PlotTokenSlot.Slots",
                    new ItemGridSlot[]{new ItemGridSlot(new ItemStack(tokenId.trim(), 1))}
                );
            } else {
                commandBuilder.set(row + " #PlotTokenSlot.Slots", new ItemGridSlot[]{new ItemGridSlot()});
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
            commandBuilder.set(block + " #IconGrid.Slots", gridSlots);
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
        if (action.equalsIgnoreCase("Tab")) {
            String tabId = data.tabId;
            PlayerTownJournalState.JournalTab tab = parseTab(tabId);
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
    }
}
