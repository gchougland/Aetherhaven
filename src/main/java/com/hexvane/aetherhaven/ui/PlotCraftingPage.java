package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.plot.PlotBuildingStyles;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog.GroupEntry;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog.Tab;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog.VariantEntry;
import com.hexvane.aetherhaven.plot.PlotCraftingPrefabPreview;
import com.hexvane.aetherhaven.plot.PlotCraftingPrefabPreviewClientMode;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockService;
import com.hexvane.aetherhaven.community.CommunityCatalogService;
import com.hexvane.aetherhaven.community.CommunityCatalogSort;
import com.hexvane.aetherhaven.community.CommunityDownloadService;
import com.hexvane.aetherhaven.community.CommunityManifestEntry;
import com.hexvane.aetherhaven.community.CommunityModerationPreviewCache;
import com.hexvane.aetherhaven.community.CommunityModerationService;
import com.hexvane.aetherhaven.community.CommunityPendingEntry;
import com.hexvane.aetherhaven.community.CommunityPrefabSafety;
import com.hexvane.aetherhaven.community.CommunityPaths;
import com.hexvane.aetherhaven.community.CommunityPreviewCache;
import com.hexvane.aetherhaven.community.CommunityRequiredMods;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotCraftingPage extends AetherhavenInteractiveCustomUIPage<PlotCraftingPage.PageData> {
    private static final String ROWS = "#BuildingListScroll #BuildingRows";
    private static final String STYLE_ROWS = "#StyleFilterScroll #StyleFilterRows";
    private static final String TAB_CORE = "Core";
    private static final String TAB_DECORATIONS = "Decorations";
    private static final String TAB_COMMUNITY = "Community";
    private static final String TAB_MODERATION = "Moderation";
    private static final long CRAFT_COST = AetherhavenConstants.PLOT_TOKEN_CRAFT_GOLD_COST;
    /** Matches {@code PlotCraftingPage.ui} list height for core/decoration tabs. */
    private static final int BUILDING_LIST_HEIGHT_NORMAL = 470;
    /** Shorter list on Community tab (refresh row + action buttons + craft). */
    private static final int BUILDING_LIST_HEIGHT_MARKETPLACE = 380;
    /** Shorter still on Moderation (refresh row + two action button rows). */
    private static final int BUILDING_LIST_HEIGHT_MODERATION = 340;
    private static final int BUILDING_LIST_FOOTER_GAP = 8;
    /** Visible rows per page on Community / Moderation (Custom UI has no list virtualization). */
    private static final int MARKETPLACE_PAGE_SIZE = 12;
    /** Delayed attempts so {@code #PrefabPreview} is mounted before {@link BuilderToolPrefabPreview} arrives. */
    private static final long[] PREFAB_PREVIEW_RETRY_DELAYS_MS = {50L, 100L, 150L};

    private Tab activeTab = Tab.CORE;
    private final Set<String> activeStyleFilters = new HashSet<>();
    private boolean openSoundPlayed;
    /** {@code append(ui)} must run only once per page instance; repeating it on every {@link #sendUpdate} duplicates the whole tree. */
    private boolean templateAppended;
    /** When true, {@link #refresh} will call {@link #sendUpdate} and must own prefab preview scheduling. */
    private boolean deferPreviewToSendUpdate;
    private int prefabPreviewSendSerial;
    private int marketplacePageIndex;
    @Nonnull
    private String searchQuery = "";
    @Nonnull
    private CommunityCatalogSort communitySort = CommunityCatalogSort.UPVOTES;
    private boolean showCommunityBuildsWithMissingMods;
    private final AtomicBoolean marketplaceRefreshInFlight = new AtomicBoolean(false);
    private boolean clientCreativeSpoofed;
    @Nullable
    private String lastSentPreviewPrefabKey;
    private int pageIconFetchSerial;
    @Nullable
    private String pendingPreviewPrefabKey;
    @Nullable
    private String communityPreviewConstructionId;
    @Nullable
    private String moderationPreviewSubmissionId;
    @Nullable
    private String selectedGroupKey;
    private int variantIndex;

    public PlotCraftingPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PlotCraftingPage.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyPlotCraftingPage(commandBuilder);

        if (!openSoundPlayed) {
            UiSoundEffects.play2dUi(ref, store, AetherhavenConstants.SFX_WORKBENCH_OPEN);
            openSoundPlayed = true;
        }

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        UUIDComponent playerUuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID playerUuid = playerUuidComponent != null ? playerUuidComponent.getUuid() : null;
        CommunityModerationService moderation = plugin.getCommunityModerationService();
        boolean knownModeratorAccess = playerUuid != null && moderation.hasModeratorAccessResult(playerUuid);
        boolean isModerator = playerUuid != null && moderation.isModerator(playerUuid);
        // Only kick when we know they are denied; unknown access stays on Moderation while probing async.
        if (activeTab == Tab.MODERATION && knownModeratorAccess && !isModerator) {
            activeTab = Tab.CORE;
        }
        boolean communityTab = activeTab == Tab.COMMUNITY;
        boolean moderationTab = activeTab == Tab.MODERATION;
        boolean marketplaceTab = communityTab || moderationTab;
        boolean marketplaceLoading = marketplaceTab && marketplaceRefreshInFlight.get();
        CommunityCatalogService communityCatalog = plugin.getCommunityCatalogService();

        List<GroupEntry> allGroups =
            moderationTab
                ? moderation.buildGroupEntries()
                : communityTab
                    ? communityCatalog.buildGroupEntries(
                        activeStyleFilters,
                        communitySort,
                        showCommunityBuildsWithMissingMods
                    )
                    : PlotCraftingCatalog.groupsForTab(catalog, activeTab, plugin.getClass().getClassLoader(), activeStyleFilters);
        allGroups = filterGroupsBySearch(allGroups, communityCatalog, moderation, communityTab, moderationTab);
        int marketplacePageCount = 1;
        List<GroupEntry> groups = allGroups;
        if (marketplaceTab) {
            marketplacePageCount = Math.max(1, (allGroups.size() + MARKETPLACE_PAGE_SIZE - 1) / MARKETPLACE_PAGE_SIZE);
            if (marketplacePageIndex >= marketplacePageCount) {
                marketplacePageIndex = marketplacePageCount - 1;
            }
            if (marketplacePageIndex < 0) {
                marketplacePageIndex = 0;
            }
            int from = marketplacePageIndex * MARKETPLACE_PAGE_SIZE;
            int to = Math.min(from + MARKETPLACE_PAGE_SIZE, allGroups.size());
            groups = from < to ? allGroups.subList(from, to) : List.of();
        }
        ensureSelection(allGroups);

        boolean showStyleFilters = !moderationTab;
        bindStyleFilters(commandBuilder, eventBuilder, catalog, communityTab ? communityCatalog : null, showStyleFilters);

        commandBuilder.set("#PlotCraftTabs.SelectedTab", tabId(activeTab));

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.SelectedTabChanged,
            "#PlotCraftTabs",
            new EventData()
                .append("Action", "TabChange")
                .append("@SelectedTab", "#PlotCraftTabs.SelectedTab"),
            false
        );
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#VariantPrev", EventData.of("Action", "VariantPrev"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#VariantNext", EventData.of("Action", "VariantNext"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CraftButton", EventData.of("Action", "Craft"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LoadPreviewButton", EventData.of("Action", "LoadPreview"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DownloadButton", EventData.of("Action", "Download"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveButton", EventData.of("Action", "Remove"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ModerationLoadPreviewButton", EventData.of("Action", "LoadModerationPreview"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ModerationCraftButton", EventData.of("Action", "CraftModerationTest"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ApproveButton", EventData.of("Action", "Approve"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DenyButton", EventData.of("Action", "Deny"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MarketplaceRefreshButton", EventData.of("Action", "RefreshMarketplace"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MarketplacePagePrev", EventData.of("Action", "MarketplacePagePrev"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MarketplacePageNext", EventData.of("Action", "MarketplacePageNext"), false);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SearchInput",
            EventData.of("@SearchQuery", "#SearchInput.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#CommunitySort",
            EventData.of("@SortMode", "#CommunitySort.Value"),
            false
        );

        commandBuilder.set("#SearchInput.Value", searchQuery);
        bindCommunitySortDropdown(commandBuilder, communityTab);

        commandBuilder.clear(ROWS);
        List<String> pageIconIds = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            GroupEntry group = groups.get(i);
            commandBuilder.append(ROWS, "Aetherhaven/PlotCraftingBuildingRow.ui");
            String row = ROWS + "[" + i + "]";
            boolean selected = group.groupKey().equals(selectedGroupKey);
            commandBuilder.set(row + " #SelectHilite.Visible", selected);
            commandBuilder.set(row + " #BuildingName.TextSpans", Message.raw(group.displayName()));
            if (communityTab) {
                CommunityManifestEntry listEntry = communityCatalog.findEntry(group.groupKey());
                String creator = listEntry != null ? listEntry.getCreatorName() : "";
                commandBuilder.set(row + " #BuildingCreator.Visible", true);
                commandBuilder.set(
                    row + " #BuildingCreator.TextSpans",
                    Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.communityListByCreator")
                        .param("creator", Message.raw(creator))
                );
            } else if (moderationTab) {
                CommunityPendingEntry listEntry = moderation.findEntry(group.groupKey());
                String creator = listEntry != null ? listEntry.getCreatorName() : "";
                commandBuilder.set(row + " #BuildingCreator.Visible", true);
                commandBuilder.set(
                    row + " #BuildingCreator.TextSpans",
                    Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.communityListByCreator")
                        .param("creator", Message.raw(creator))
                );
            } else {
                commandBuilder.set(row + " #BuildingCreator.Visible", false);
            }
            commandBuilder.set(row + " #IconBox #BuildingIcon.AssetPath", iconPathForGroup(catalog, group, plugin, communityTab, moderationTab));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                new EventData().append("Action", "SelectGroup").append("GroupKey", group.groupKey()),
                false
            );
            if (communityTab || moderationTab) {
                pageIconIds.add(group.groupKey());
            }
        }

        GroupEntry selectedGroup = findGroup(allGroups, selectedGroupKey);
        VariantEntry variant = selectedVariant(selectedGroup);
        int variantCount = selectedGroup != null ? selectedGroup.variants().size() : 0;
        int variantDisplayIndex = selectedVariantIndex(selectedGroup);
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean installed = variant != null && communityCatalog.isInstalled(variant.constructionId());
        boolean variantLocked = false;
        if (variant != null && !moderationTab) {
            variantLocked = !PlotTokenUnlockService.isUnlocked(ref, store, variant.constructionId());
        }
        boolean moderationPreviewReady =
            variant != null
                && moderationPreviewSubmissionId != null
                && moderationPreviewSubmissionId.equalsIgnoreCase(variant.constructionId());
        if (variant != null) {
            commandBuilder.set("#VariantName.TextSpans", Message.raw(variant.displayName()));
            if (communityTab) {
                CommunityManifestEntry entry = communityCatalog.findEntry(variant.constructionId());
                String creator = entry != null ? entry.getCreatorName() : "";
                long bytes = entry != null ? entry.getPrefabBytes() : 0L;
                commandBuilder.set(
                    "#VariantIndex.TextSpans",
                    Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.communityMeta")
                        .param("creator", Message.raw(creator))
                        .param("size", Message.raw(formatPrefabSize(bytes)))
                );
            } else if (moderationTab) {
                CommunityPendingEntry entry = moderation.findEntry(variant.constructionId());
                String creator = entry != null ? entry.getCreatorName() : "";
                String proposedId = entry != null ? entry.getProposedId() : "";
                commandBuilder.set(
                    "#VariantIndex.TextSpans",
                    Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationMeta")
                        .param("creator", Message.raw(creator))
                        .param("proposedId", Message.raw(proposedId))
                );
            } else {
                commandBuilder.set(
                    "#VariantIndex.TextSpans",
                    Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.variantIndex")
                        .param("current", Message.raw(String.valueOf(variantDisplayIndex)))
                        .param("total", Message.raw(String.valueOf(variantCount)))
                );
            }
            commandBuilder.set("#VariantPrev.Disabled", marketplaceTab || variantCount <= 1);
            commandBuilder.set("#VariantNext.Disabled", marketplaceTab || variantCount <= 1);
            commandBuilder.set("#PreviewLockOverlay.Visible", variantLocked);
            commandBuilder.set("#PreviewLockIconWrap.Visible", variantLocked);
            if (communityTab && !installed && (communityPreviewConstructionId == null || !communityPreviewConstructionId.equalsIgnoreCase(variant.constructionId()))) {
                pendingPreviewPrefabKey = null;
                commandBuilder.set("#PreviewPlaceholder.Visible", true);
                commandBuilder.set(
                    "#PreviewPlaceholder.TextSpans",
                    Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.previewPlaceholder")
                );
            } else if (moderationTab && !moderationPreviewReady) {
                pendingPreviewPrefabKey = null;
                commandBuilder.set("#PreviewPlaceholder.Visible", true);
                commandBuilder.set(
                    "#PreviewPlaceholder.TextSpans",
                    Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationPreviewPlaceholder")
                );
            } else {
                commandBuilder.set("#PreviewPlaceholder.Visible", false);
                pendingPreviewPrefabKey = variant.prefabPathKey();
            }
        } else {
            commandBuilder.set("#VariantName.TextSpans", Message.raw(""));
            commandBuilder.set("#VariantIndex.TextSpans", Message.raw(""));
            commandBuilder.set("#VariantPrev.Disabled", true);
            commandBuilder.set("#VariantNext.Disabled", true);
            commandBuilder.set("#PreviewLockOverlay.Visible", false);
            commandBuilder.set("#PreviewLockIconWrap.Visible", false);
            commandBuilder.set("#PreviewPlaceholder.Visible", false);
            pendingPreviewPrefabKey = null;
        }

        PlayerRef infoPlayerRef = store.getComponent(ref, PlayerRef.getComponentType());
        bindBuildingInfoPanel(
            commandBuilder,
            catalog,
            communityCatalog,
            moderation,
            variant,
            communityTab,
            moderationTab,
            infoPlayerRef
        );

        commandBuilder.set("#StyleFilterColumn.Visible", showStyleFilters);
        commandBuilder.set("#MarketplaceRefreshRow.Visible", marketplaceTab);
        commandBuilder.set("#CommunityActionRow.Visible", communityTab);
        commandBuilder.set("#ModerationActionRow.Visible", moderationTab);
        commandBuilder.set("#CostLine.Visible", false);
        commandBuilder.set("#FundsLine.Visible", false);
        commandBuilder.set("#CraftButton.Visible", !moderationTab);
        if (marketplaceTab) {
            if (marketplaceLoading) {
                commandBuilder.set(
                    "#MarketplacePageLabel.TextSpans",
                    Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.marketplaceLoading")
                );
                commandBuilder.set("#MarketplacePagePrev.Disabled", true);
                commandBuilder.set("#MarketplacePageNext.Disabled", true);
            } else {
                int pageDisplay = marketplacePageIndex + 1;
                commandBuilder.set(
                    "#MarketplacePageLabel.TextSpans",
                    Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.marketplacePageLabel")
                        .param("page", Message.raw(String.valueOf(pageDisplay)))
                        .param("pages", Message.raw(String.valueOf(marketplacePageCount)))
                );
                commandBuilder.set("#MarketplacePagePrev.Disabled", marketplacePageIndex <= 0);
                commandBuilder.set("#MarketplacePageNext.Disabled", marketplacePageIndex >= marketplacePageCount - 1);
            }
            commandBuilder.set("#MarketplaceRefreshButton.Disabled", marketplaceLoading);
        }
        // Shorter list when marketplace action rows are visible (community / moderation).
        applyBuildingListHeight(commandBuilder, communityTab, moderationTab);
        if (communityTab) {
            boolean hasSelection = variant != null;
            CommunityManifestEntry selectedCommunity =
                variant != null ? communityCatalog.findEntry(variant.constructionId()) : null;
            boolean dependenciesSatisfied =
                selectedCommunity != null && CommunityRequiredMods.isSatisfied(selectedCommunity.getRequiredMods());
            boolean previewReady =
                variant != null
                    && communityPreviewConstructionId != null
                    && communityPreviewConstructionId.equalsIgnoreCase(variant.constructionId());
            commandBuilder.set(
                "#LoadPreviewButton.Disabled",
                marketplaceLoading || !hasSelection || !dependenciesSatisfied || installed || previewReady
            );
            commandBuilder.set(
                "#DownloadButton.Disabled",
                marketplaceLoading || !hasSelection || !dependenciesSatisfied || installed
            );
            commandBuilder.set("#RemoveButton.Disabled", marketplaceLoading || !hasSelection || !installed);
        }
        if (moderationTab) {
            boolean hasSelection = variant != null;
            CommunityPendingEntry selectedPending =
                variant != null ? moderation.findEntry(variant.constructionId()) : null;
            boolean dependenciesSatisfied =
                selectedPending != null && CommunityRequiredMods.isSatisfied(selectedPending.getRequiredMods());
            commandBuilder.set(
                "#ModerationLoadPreviewButton.Disabled",
                marketplaceLoading || !hasSelection || moderationPreviewReady
            );
            commandBuilder.set(
                "#ModerationCraftButton.Disabled",
                marketplaceLoading || !hasSelection || !dependenciesSatisfied || !moderationPreviewReady
            );
            commandBuilder.set(
                "#ApproveButton.Disabled",
                marketplaceLoading || !hasSelection || !dependenciesSatisfied || !moderationPreviewReady
            );
            commandBuilder.set("#DenyButton.Disabled", marketplaceLoading || !hasSelection);
        }

        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        TownRecord town = uc != null ? tm.findTownForPlayerInWorld(uc.getUuid()) : null;
        boolean allowTreasury = uc != null && town != null && town.playerCanSpendTreasuryGold(uc.getUuid());

        if (!moderationTab) {
            long invCoins = inv != null ? GoldCoinPayment.totalAvailable(null, inv) : 0L;
            long treasuryCoins = town != null ? town.getTreasuryGoldCoinCount() : 0L;

            commandBuilder.set(
                "#CostLine.TextSpans",
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.costLine")
                    .param("cost", Message.raw(String.valueOf(CRAFT_COST)))
            );
            commandBuilder.set(
                "#FundsLine.TextSpans",
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.fundsLine")
                    .param("inv", Message.raw(String.valueOf(invCoins)))
                    .param("treasury", Message.raw(String.valueOf(treasuryCoins)))
            );
        }

        boolean canCraft =
            !moderationTab
                && !marketplaceLoading
                && variant != null
                && (!communityTab || installed)
                && !variantLocked
                && inv != null
                && GoldCoinPayment.canAfford(town, inv, CRAFT_COST, allowTreasury);
        commandBuilder.set("#CraftButton.Disabled", !canCraft);

        if (communityTab && !pageIconIds.isEmpty()) {
            schedulePageIconEnsure(ref, store, communityCatalog, pageIconIds);
        } else if (moderationTab && !pageIconIds.isEmpty() && playerUuid != null) {
            scheduleModerationPageIconEnsure(ref, store, moderation, pageIconIds, playerUuid);
        }

        if (!deferPreviewToSendUpdate) {
            schedulePrefabPreviewWithRetries(ref, store);
        }
    }

    @Override
    protected void sendUpdate(@Nullable UICommandBuilder commandBuilder, @Nullable UIEventBuilder eventBuilder, boolean clear) {
        if (isDismissed()) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (isDismissed() || !ref.isValid()) {
                    return;
                }
                Player playerComponent = store.getComponent(ref, Player.getComponentType());
                if (playerComponent == null) {
                    return;
                }
                if (playerComponent.getPageManager().getCustomPage() != this) {
                    return;
                }
                playerComponent.getPageManager()
                    .updateCustomPage(
                        new CustomPage(
                            this.getClass().getName(),
                            false,
                            clear,
                            this.lifetime,
                            commandBuilder != null ? commandBuilder.getCommands() : UICommandBuilder.EMPTY_COMMAND_ARRAY,
                            eventBuilder != null ? eventBuilder.getEvents() : UIEventBuilder.EMPTY_EVENT_BINDING_ARRAY
                        )
                    );
                schedulePrefabPreviewWithRetries(ref, store);
            }
        );
    }

    /**
     * Send prefab preview data after the client has mounted {@code #PrefabPreview}. Retries cover slow Adventure clients
     * and the initial {@code openCustomPage} path where UI is sent immediately after {@link #build}.
     */
    private void schedulePrefabPreviewWithRetries(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        String prefabKey = pendingPreviewPrefabKey;
        boolean hasPreview = prefabKey != null && !prefabKey.isBlank();
        if (!hasPreview) {
            if (clientCreativeSpoofed) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    if (PlotCraftingPrefabPreviewClientMode.restoreClientGameMode(
                        playerRef, player.getGameMode(), true
                    )) {
                        clientCreativeSpoofed = false;
                    }
                }
            }
            if (lastSentPreviewPrefabKey != null) {
                PlotCraftingPrefabPreview.clear(playerRef);
                lastSentPreviewPrefabKey = null;
            }
            prefabPreviewSendSerial++;
            return;
        }
        if (prefabKey.equals(lastSentPreviewPrefabKey) && clientCreativeSpoofed) {
            return;
        }

        World world = store.getExternalData().getWorld();
        final int serial = ++prefabPreviewSendSerial;
        final String keyForSend = prefabKey;
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Runnable attempt =
            () -> {
                if (!ref.isValid() || serial != prefabPreviewSendSerial) {
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null || player.getPageManager().getCustomPage() != this) {
                    return;
                }
                String currentKey = pendingPreviewPrefabKey;
                if (currentKey == null || currentKey.isBlank() || !currentKey.equals(keyForSend)) {
                    return;
                }
                if (PlotCraftingPrefabPreviewClientMode.ensureClientCreativeForPreview(
                    playerRef, player.getGameMode(), clientCreativeSpoofed
                )) {
                    clientCreativeSpoofed = true;
                }
                PlotCraftingPrefabPreview.send(playerRef, keyForSend);
                lastSentPreviewPrefabKey = keyForSend;
            };
        for (long delayMs : PREFAB_PREVIEW_RETRY_DELAYS_MS) {
            if (plugin != null) {
                plugin.scheduleOnWorld(world, attempt, delayMs);
            } else {
                world.execute(attempt);
            }
        }
    }

    private void schedulePageIconEnsure(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommunityCatalogService communityCatalog,
        @Nonnull List<String> pageIconIds
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || pageIconIds.isEmpty()) {
            return;
        }
        World world = store.getExternalData().getWorld();
        pageIconFetchSerial =
            communityCatalog.ensureIconsForIdsAsync(
                pageIconIds,
                () ->
                    plugin.scheduleOnWorld(
                        world,
                        () -> {
                            if (!ref.isValid() || isDismissed()) {
                                return;
                            }
                            Player player = store.getComponent(ref, Player.getComponentType());
                            if (player == null || player.getPageManager().getCustomPage() != this) {
                                return;
                            }
                            if (activeTab != Tab.COMMUNITY) {
                                return;
                            }
                            refresh(ref, store);
                        },
                        50L
                    )
            );
    }

    private void scheduleModerationPageIconEnsure(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommunityModerationService moderation,
        @Nonnull List<String> pageSubmissionIds,
        @Nonnull UUID moderatorUuid
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || pageSubmissionIds.isEmpty()) {
            return;
        }
        World world = store.getExternalData().getWorld();
        pageIconFetchSerial =
            moderation.ensureIconsForSubmissionIdsAsync(
                pageSubmissionIds,
                moderatorUuid,
                () ->
                    plugin.scheduleOnWorld(
                        world,
                        () -> {
                            if (!ref.isValid() || isDismissed()) {
                                return;
                            }
                            Player player = store.getComponent(ref, Player.getComponentType());
                            if (player == null || player.getPageManager().getCustomPage() != this) {
                                return;
                            }
                            if (activeTab != Tab.MODERATION) {
                                return;
                            }
                            refresh(ref, store);
                        },
                        50L
                    )
            );
    }

    @Override
    protected void sendUpdate(@Nullable UICommandBuilder commandBuilder, boolean clear) {
        sendUpdate(commandBuilder, null, clear);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.searchQuery != null) {
            searchQuery = data.searchQuery;
            marketplacePageIndex = 0;
            refresh(ref, store);
            return;
        }
        if (data.sortMode != null) {
            communitySort = CommunityCatalogSort.fromValue(data.sortMode);
            marketplacePageIndex = 0;
            refresh(ref, store);
            return;
        }
        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "TabChange" -> {
                if (TAB_MODERATION.equals(data.selectedTab)) {
                    activeTab = Tab.MODERATION;
                } else if (TAB_COMMUNITY.equals(data.selectedTab)) {
                    activeTab = Tab.COMMUNITY;
                } else if (TAB_DECORATIONS.equals(data.selectedTab)) {
                    activeTab = Tab.DECORATIONS;
                } else {
                    activeTab = Tab.CORE;
                }
                selectedGroupKey = null;
                variantIndex = 0;
                marketplacePageIndex = 0;
                communityPreviewConstructionId = null;
                moderationPreviewSubmissionId = null;
                if (activeTab == Tab.MODERATION) {
                    AetherhavenPlugin modPlugin = AetherhavenPlugin.get();
                    UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                    PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
                    if (modPlugin != null && uc != null && pr != null) {
                        startModerationTabLoad(ref, store, pr, uc.getUuid());
                        return;
                    }
                    activeTab = Tab.CORE;
                } else if (activeTab == Tab.COMMUNITY) {
                    maybeAutoRefreshCommunityCatalog(ref, store);
                }
            }
            case "SelectGroup" -> {
                if (data.groupKey != null && !data.groupKey.isBlank()) {
                    if (activeTab == Tab.COMMUNITY && selectedGroupKey != null && !selectedGroupKey.equals(data.groupKey.trim())) {
                        communityPreviewConstructionId = null;
                    }
                    if (activeTab == Tab.MODERATION && selectedGroupKey != null && !selectedGroupKey.equals(data.groupKey.trim())) {
                        moderationPreviewSubmissionId = null;
                    }
                    selectedGroupKey = data.groupKey.trim();
                    variantIndex = 0;
                    if (activeTab == Tab.MODERATION) {
                        refresh(ref, store);
                        return;
                    }
                }
            }
            case "VariantPrev" -> variantIndex--;
            case "VariantNext" -> variantIndex++;
            case "MarketplacePagePrev" -> {
                if (marketplacePageIndex > 0) {
                    marketplacePageIndex--;
                }
            }
            case "MarketplacePageNext" -> marketplacePageIndex++;
            case "StyleFilterToggle" -> {
                applyStyleFilterToggle(data);
                marketplacePageIndex = 0;
                selectedGroupKey = null;
                variantIndex = 0;
            }
            case "CommunityMissingModsToggle" -> {
                showCommunityBuildsWithMissingMods = Boolean.TRUE.equals(data.checked);
                marketplacePageIndex = 0;
                selectedGroupKey = null;
                variantIndex = 0;
                communityPreviewConstructionId = null;
            }
            case "Craft" -> {
                tryCraft(ref, store);
                return;
            }
            case "LoadPreview" -> {
                tryLoadPreview(ref, store);
                return;
            }
            case "Download" -> {
                tryDownload(ref, store);
                return;
            }
            case "Remove" -> {
                tryRemove(ref, store);
                return;
            }
            case "LoadModerationPreview" -> {
                tryLoadModerationPreview(ref, store);
                return;
            }
            case "CraftModerationTest" -> {
                tryCraftModerationTest(ref, store);
                return;
            }
            case "Approve" -> {
                tryApprove(ref, store);
                return;
            }
            case "Deny" -> {
                tryDeny(ref, store);
                return;
            }
            case "RefreshMarketplace" -> {
                tryRefreshMarketplace(ref, store);
                return;
            }
            default -> {
                return;
            }
        }
        refresh(ref, store);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        prefabPreviewSendSerial++;
        pageIconFetchSerial++;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            PlotCraftingPrefabPreviewClientMode.restoreClientGameMode(playerRef, player.getGameMode(), clientCreativeSpoofed);
            clientCreativeSpoofed = false;
            lastSentPreviewPrefabKey = null;
        }
        PlotCraftingPrefabPreview.clear(playerRef);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            CommunityPreviewCache.get().clearSession(plugin);
            CommunityModerationPreviewCache.get().clearSession(plugin);
        }
        communityPreviewConstructionId = null;
        moderationPreviewSubmissionId = null;
        super.onDismiss(ref, store);
    }

    private void tryCraft(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || player == null || pr == null) {
            return;
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        List<GroupEntry> groups =
            activeTab == Tab.MODERATION
                ? plugin.getCommunityModerationService().buildGroupEntries()
                : activeTab == Tab.COMMUNITY
                    ? plugin.getCommunityCatalogService().buildGroupEntries(
                        activeStyleFilters,
                        communitySort,
                        showCommunityBuildsWithMissingMods
                    )
                    : PlotCraftingCatalog.groupsForTab(catalog, activeTab, plugin.getClass().getClassLoader(), activeStyleFilters);
        GroupEntry group = findGroup(groups, selectedGroupKey);
        VariantEntry variant = selectedVariant(group);
        if (variant == null) {
            refresh(ref, store);
            return;
        }
        if (!PlotTokenUnlockService.isUnlocked(ref, store, variant.constructionId())) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.craftLocked"),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            return;
        }

        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        TownRecord town = uc != null ? tm.findTownForPlayerInWorld(uc.getUuid()) : null;
        boolean allowTreasury = uc != null && town != null && town.playerCanSpendTreasuryGold(uc.getUuid());

        SpendBreakdown paid = GoldCoinPayment.trySpendReturningBreakdown(town, inv, CRAFT_COST, allowTreasury);
        if (paid == null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.insufficientGold"),
                NotificationStyle.Danger
            );
            refresh(ref, store);
            return;
        }

        ConstructionDefinition def = catalog.get(variant.constructionId());
        String displayName = def != null && def.getDisplayName() != null ? def.getDisplayName() : variant.displayName();
        ItemStack token = PlotTokenInventory.createTokenStack(variant.constructionId(), 1, displayName, pr.getLanguage());
        if (!inv.canAddItemStack(token)) {
            GoldCoinPayment.refund(town, player, ref, store, paid);
            if (town != null) {
                tm.updateTown(town);
            }
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.noSpace"),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            return;
        }

        ItemStackTransaction giveTx = player.giveItem(token, ref, store);
        if (!giveTx.succeeded()) {
            GoldCoinPayment.refund(town, player, ref, store, paid);
            if (town != null) {
                tm.updateTown(town);
            }
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.noSpace"),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            return;
        }

        if (town != null && paid.fromTreasury() > 0L) {
            tm.updateTown(town);
        }

        UiSoundEffects.play2dUi(ref, store, AetherhavenConstants.SFX_WORKBENCH_CRAFT);

        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.crafted").param("name", Message.raw(displayName)),
            NotificationStyle.Success
        );
        refresh(ref, store);
    }

    private void ensureSelection(@Nonnull List<GroupEntry> groups) {
        if (groups.isEmpty()) {
            selectedGroupKey = null;
            variantIndex = 0;
            return;
        }
        if (selectedGroupKey == null || findGroup(groups, selectedGroupKey) == null) {
            selectedGroupKey = groups.get(0).groupKey();
            variantIndex = 0;
        }
        GroupEntry group = findGroup(groups, selectedGroupKey);
        if (group != null) {
            int n = group.variants().size();
            if (n <= 0) {
                variantIndex = 0;
            } else if (variantIndex < 0) {
                variantIndex = n - 1;
            } else if (variantIndex >= n) {
                variantIndex = 0;
            }
        }
    }

    @Nullable
    private static GroupEntry findGroup(@Nonnull List<GroupEntry> groups, @Nullable String groupKey) {
        if (groupKey == null) {
            return null;
        }
        for (GroupEntry g : groups) {
            if (groupKey.equals(g.groupKey())) {
                return g;
            }
        }
        return null;
    }

    @Nullable
    private VariantEntry selectedVariant(@Nullable GroupEntry group) {
        if (group == null || group.variants().isEmpty()) {
            return null;
        }
        int idx = variantIndex;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= group.variants().size()) {
            idx = group.variants().size() - 1;
        }
        return group.variants().get(idx);
    }

    /** 1-based index for UI, or 0 when there is no valid selection. */
    private int selectedVariantIndex(@Nullable GroupEntry group) {
        if (group == null || group.variants().isEmpty()) {
            return 0;
        }
        int idx = variantIndex;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= group.variants().size()) {
            idx = group.variants().size() - 1;
        }
        return idx + 1;
    }

    private static void applyBuildingListHeight(
        @Nonnull UICommandBuilder commandBuilder,
        boolean communityTab,
        boolean moderationTab
    ) {
        int height = BUILDING_LIST_HEIGHT_NORMAL;
        if (moderationTab) {
            height = BUILDING_LIST_HEIGHT_MODERATION;
        } else if (communityTab) {
            height = BUILDING_LIST_HEIGHT_MARKETPLACE;
        }
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(height));
        anchor.setBottom(Value.of(BUILDING_LIST_FOOTER_GAP));
        commandBuilder.setObject("#BuildingListScroll.Anchor", anchor);
    }

    @Nonnull
    private static String tabId(@Nonnull Tab tab) {
        return switch (tab) {
            case CORE -> TAB_CORE;
            case DECORATIONS -> TAB_DECORATIONS;
            case COMMUNITY -> TAB_COMMUNITY;
            case MODERATION -> TAB_MODERATION;
        };
    }

    @Nonnull
    private static String iconPathForGroup(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull GroupEntry group,
        @Nonnull AetherhavenPlugin plugin,
        boolean communityTab,
        boolean moderationTab
    ) {
        if (moderationTab) {
            String iconId = CommunityModerationService.iconConstructionId(group.groupKey());
            if (ConstructionTokenIconPath.isIconAvailable(iconId, plugin.getDataDirectory())) {
                return CommunityModerationService.iconAssetPath(group.groupKey());
            }
            return ConstructionTokenIconPath.unifiedPlotTokenFallbackIconPath();
        }
        if (communityTab) {
            String id = group.groupKey();
            Path iconFile = CommunityPaths.iconFile(plugin.getDataDirectory(), id);
            if (Files.isRegularFile(iconFile)) {
                return CommunityPaths.iconAssetPath(id);
            }
            if (ConstructionTokenIconPath.isIconAvailable(id, plugin.getDataDirectory())) {
                return CommunityPaths.iconAssetPath(id);
            }
            return ConstructionTokenIconPath.unifiedPlotTokenFallbackIconPath();
        }
        for (VariantEntry v : group.variants()) {
            ConstructionDefinition def = catalog.get(v.constructionId());
            if (def != null) {
                return ConstructionTokenIconPath.forConstruction(def, plugin.getDataDirectory());
            }
        }
        ConstructionDefinition canon = catalog.get(group.groupKey());
        if (canon != null) {
            return ConstructionTokenIconPath.forConstruction(canon, plugin.getDataDirectory());
        }
        return CustomBuildingsPaths.iconAssetPath(group.groupKey());
    }

    private void tryLoadPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null || activeTab != Tab.COMMUNITY) {
            return;
        }
        CommunityCatalogService community = plugin.getCommunityCatalogService();
        GroupEntry group = findGroup(
            community.buildGroupEntries(activeStyleFilters, communitySort, showCommunityBuildsWithMissingMods),
            selectedGroupKey
        );
        VariantEntry variant = selectedVariant(group);
        if (variant == null) {
            refresh(ref, store);
            return;
        }
        CommunityManifestEntry entry = community.findEntry(variant.constructionId());
        if (entry == null) {
            refresh(ref, store);
            return;
        }
        if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
            notifyMissingRequiredMods(pr, entry.getRequiredMods());
            return;
        }
        if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        refresh(ref, store);
        World world = store.getExternalData().getWorld();
        String selectedId = variant.constructionId();
        CompletableFuture.runAsync(
            () -> {
                String loadedPrefabKey;
                try {
                    loadedPrefabKey = CommunityPreviewCache.get().loadPreview(plugin, entry);
                } catch (RuntimeException e) {
                    loadedPrefabKey = null;
                }
                String prefabKey = loadedPrefabKey;
                plugin.scheduleOnWorld(
                    world,
                    () -> {
                        marketplaceRefreshInFlight.set(false);
                        if (!ref.isValid() || isDismissed() || activeTab != Tab.COMMUNITY) {
                            return;
                        }
                        if (prefabKey == null) {
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.previewFailed"),
                                NotificationStyle.Danger
                            );
                        } else if (selectedId.equalsIgnoreCase(selectedGroupKey)) {
                            communityPreviewConstructionId = selectedId;
                            pendingPreviewPrefabKey = prefabKey;
                        }
                        refresh(ref, store);
                    },
                    1L
                );
            }
        );
    }

    private void tryDownload(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null || activeTab != Tab.COMMUNITY) {
            return;
        }
        CommunityCatalogService community = plugin.getCommunityCatalogService();
        GroupEntry group = findGroup(
            community.buildGroupEntries(activeStyleFilters, communitySort, showCommunityBuildsWithMissingMods),
            selectedGroupKey
        );
        VariantEntry variant = selectedVariant(group);
        if (variant == null) {
            refresh(ref, store);
            return;
        }
        CommunityManifestEntry entry = community.findEntry(variant.constructionId());
        if (entry == null) {
            refresh(ref, store);
            return;
        }
        if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        refresh(ref, store);
        World world = store.getExternalData().getWorld();
        CompletableFuture.runAsync(
            () -> {
                CommunityDownloadService.InstallResult installResult;
                try {
                    installResult = CommunityDownloadService.install(plugin, entry);
                } catch (RuntimeException e) {
                    installResult = CommunityDownloadService.InstallResult.IO_ERROR;
                }
                CommunityDownloadService.InstallResult result = installResult;
                plugin.scheduleOnWorld(
                    world,
                    () -> {
                        marketplaceRefreshInFlight.set(false);
                        if (!ref.isValid() || isDismissed()) {
                            return;
                        }
                        if (result == CommunityDownloadService.InstallResult.MISSING_MODS) {
                            notifyMissingRequiredMods(pr, entry.getRequiredMods());
                        } else if (result != CommunityDownloadService.InstallResult.SUCCESS) {
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.downloadFailed"),
                                NotificationStyle.Danger
                            );
                        } else {
                            communityPreviewConstructionId = null;
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.downloaded")
                                    .param("name", Message.raw(entry.getDisplayName())),
                                NotificationStyle.Success
                            );
                        }
                        refresh(ref, store);
                    },
                    1L
                );
            }
        );
    }

    private void tryRemove(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null || activeTab != Tab.COMMUNITY) {
            return;
        }
        VariantEntry variant =
            selectedVariant(
                findGroup(
                    plugin.getCommunityCatalogService().buildGroupEntries(
                        activeStyleFilters,
                        communitySort,
                        showCommunityBuildsWithMissingMods
                    ),
                    selectedGroupKey
                )
            );
        if (variant == null) {
            refresh(ref, store);
            return;
        }
        CommunityDownloadService.remove(plugin, variant.constructionId());
        communityPreviewConstructionId = null;
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.removed")
                .param("name", Message.raw(variant.displayName())),
            NotificationStyle.Success
        );
        refresh(ref, store);
    }

    private void tryLoadModerationPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || pr == null || uc == null || activeTab != Tab.MODERATION) {
            return;
        }
        CommunityModerationService moderation = plugin.getCommunityModerationService();
        GroupEntry group = findGroup(moderation.buildGroupEntries(), selectedGroupKey);
        VariantEntry variant = selectedVariant(group);
        if (variant == null) {
            refresh(ref, store);
            return;
        }
        CommunityPendingEntry entry = moderation.findEntry(variant.constructionId());
        if (entry == null) {
            refresh(ref, store);
            return;
        }
        if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
            notifyMissingRequiredMods(pr, entry.getRequiredMods());
            return;
        }
        if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        refresh(ref, store);
        World world = store.getExternalData().getWorld();
        String selectedId = variant.constructionId();
        UUID moderatorUuid = uc.getUuid();
        CompletableFuture.runAsync(
            () -> {
                String loadedPrefabKey;
                try {
                    loadedPrefabKey = moderation.loadPreview(entry, moderatorUuid);
                } catch (RuntimeException e) {
                    loadedPrefabKey = null;
                }
                String prefabKey = loadedPrefabKey;
                plugin.scheduleOnWorld(
                    world,
                    () -> {
                        marketplaceRefreshInFlight.set(false);
                        if (!ref.isValid() || isDismissed() || activeTab != Tab.MODERATION) {
                            return;
                        }
                        if (prefabKey == null) {
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.previewFailed"),
                                NotificationStyle.Danger
                            );
                        } else if (selectedId.equalsIgnoreCase(selectedGroupKey)) {
                            moderationPreviewSubmissionId = selectedId;
                            pendingPreviewPrefabKey = prefabKey;
                        }
                        refresh(ref, store);
                    },
                    1L
                );
            }
        );
    }

    private void tryCraftModerationTest(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || player == null || pr == null || uc == null || activeTab != Tab.MODERATION) {
            return;
        }
        CommunityModerationService moderation = plugin.getCommunityModerationService();
        if (!moderation.isModerator(uc.getUuid())) {
            notifyNotModerator(pr, uc.getUuid());
            refresh(ref, store);
            return;
        }
        GroupEntry group = findGroup(moderation.buildGroupEntries(), selectedGroupKey);
        VariantEntry variant = selectedVariant(group);
        if (variant == null) {
            refresh(ref, store);
            return;
        }
        CommunityPendingEntry entry = moderation.findEntry(variant.constructionId());
        if (entry == null) {
            refresh(ref, store);
            return;
        }
        if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
            notifyMissingRequiredMods(pr, entry.getRequiredMods());
            return;
        }
        if (moderationPreviewSubmissionId == null
            || !moderationPreviewSubmissionId.equalsIgnoreCase(entry.getSubmissionId())) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.previewRequired"),
                NotificationStyle.Warning
            );
            return;
        }
        String proposedId = entry.getProposedId().trim();
        String displayName = entry.getDisplayName();
        World world = store.getExternalData().getWorld();
        UUID moderatorUuid = uc.getUuid();

        if (!isSafeInstalledCommunityPrefab(plugin, proposedId)) {
            if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
                return;
            }
            refresh(ref, store);
            CompletableFuture.runAsync(
                () -> {
                    CommunityDownloadService.InstallResult result = moderation.installForReview(entry, moderatorUuid);
                    plugin.scheduleOnWorld(
                        world,
                        () -> {
                            marketplaceRefreshInFlight.set(false);
                            if (!ref.isValid() || isDismissed()) {
                                return;
                            }
                            if (result != CommunityDownloadService.InstallResult.SUCCESS) {
                                NotificationUtil.sendNotification(
                                    pr.getPacketHandler(),
                                    Message.translation(
                                        "aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationCraftFailed"
                                    ),
                                    NotificationStyle.Danger
                                );
                                refresh(ref, store);
                                return;
                            }
                            plugin.reloadConfigsAndAssetCatalogs();
                            giveModerationTestToken(ref, store, player, pr, proposedId, displayName);
                        },
                        1L
                    );
                }
            );
            return;
        }
        giveModerationTestToken(ref, store, player, pr, proposedId, displayName);
    }

    private boolean isSafeInstalledCommunityPrefab(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        if (!CommunityPaths.isInstalled(plugin.getDataDirectory(), constructionId)) {
            return false;
        }
        try {
            Path prefab = CommunityPaths.installedPrefabFile(plugin.getDataDirectory(), constructionId);
            return CommunityPrefabSafety.validate(Files.readAllBytes(prefab)).isSafe();
        } catch (Exception e) {
            return false;
        }
    }

    private void giveModerationTestToken(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef pr,
        @Nonnull String constructionId,
        @Nonnull String displayName
    ) {
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        ConstructionDefinition def = AetherhavenPlugin.get() != null
            ? AetherhavenPlugin.get().getConstructionCatalog().get(constructionId)
            : null;
        String name = def != null && def.getDisplayName() != null ? def.getDisplayName() : displayName;
        ItemStack token = PlotTokenInventory.createTokenStack(constructionId, 1, name, pr.getLanguage());
        if (!inv.canAddItemStack(token)) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.noSpace"),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            return;
        }
        ItemStackTransaction giveTx = player.giveItem(token, ref, store);
        if (!giveTx.succeeded()) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.noSpace"),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            return;
        }
        UiSoundEffects.play2dUi(ref, store, AetherhavenConstants.SFX_WORKBENCH_CRAFT);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationCrafted")
                .param("name", Message.raw(name)),
            NotificationStyle.Success
        );
        refresh(ref, store);
    }

    private void tryApprove(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || pr == null || uc == null || activeTab != Tab.MODERATION) {
            return;
        }
        CommunityModerationService moderation = plugin.getCommunityModerationService();
        VariantEntry variant =
            selectedVariant(findGroup(moderation.buildGroupEntries(), selectedGroupKey));
        if (variant == null) {
            refresh(ref, store);
            return;
        }
        CommunityPendingEntry entry = moderation.findEntry(variant.constructionId());
        if (entry == null) {
            refresh(ref, store);
            return;
        }
        if (!CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) {
            notifyMissingRequiredMods(pr, entry.getRequiredMods());
            return;
        }
        if (moderationPreviewSubmissionId == null
            || !moderationPreviewSubmissionId.equalsIgnoreCase(entry.getSubmissionId())) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.previewRequired"),
                NotificationStyle.Warning
            );
            return;
        }
        if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        String displayName = entry.getDisplayName();
        UUID moderatorUuid = uc.getUuid();
        World world = store.getExternalData().getWorld();
        refresh(ref, store);
        CompletableFuture.runAsync(
            () -> {
                boolean approvalResult;
                try {
                    approvalResult = moderation.approve(moderatorUuid, entry.getSubmissionId());
                } catch (RuntimeException e) {
                    approvalResult = false;
                }
                boolean approved = approvalResult;
                plugin.scheduleOnWorld(
                    world,
                    () -> {
                        marketplaceRefreshInFlight.set(false);
                        if (!ref.isValid() || isDismissed()) {
                            return;
                        }
                        if (!approved) {
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationFailed"),
                                NotificationStyle.Danger
                            );
                        } else {
                            moderationPreviewSubmissionId = null;
                            selectedGroupKey = null;
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationApproved")
                                    .param("name", Message.raw(displayName)),
                                NotificationStyle.Success
                            );
                        }
                        refresh(ref, store);
                    },
                    1L
                );
            }
        );
    }

    private void tryDeny(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || pr == null || uc == null || activeTab != Tab.MODERATION) {
            return;
        }
        CommunityModerationService moderation = plugin.getCommunityModerationService();
        VariantEntry variant =
            selectedVariant(findGroup(moderation.buildGroupEntries(), selectedGroupKey));
        if (variant == null) {
            refresh(ref, store);
            return;
        }
        CommunityPendingEntry entry = moderation.findEntry(variant.constructionId());
        if (entry == null) {
            refresh(ref, store);
            return;
        }
        if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        String displayName = entry.getDisplayName();
        UUID moderatorUuid = uc.getUuid();
        World world = store.getExternalData().getWorld();
        refresh(ref, store);
        CompletableFuture.runAsync(
            () -> {
                boolean rejectionResult;
                try {
                    rejectionResult = moderation.reject(moderatorUuid, entry.getSubmissionId());
                } catch (RuntimeException e) {
                    rejectionResult = false;
                }
                boolean rejected = rejectionResult;
                plugin.scheduleOnWorld(
                    world,
                    () -> {
                        marketplaceRefreshInFlight.set(false);
                        if (!ref.isValid() || isDismissed()) {
                            return;
                        }
                        if (!rejected) {
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationFailed"),
                                NotificationStyle.Danger
                            );
                        } else {
                            moderationPreviewSubmissionId = null;
                            selectedGroupKey = null;
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationDenied")
                                    .param("name", Message.raw(displayName)),
                                NotificationStyle.Success
                            );
                        }
                        refresh(ref, store);
                    },
                    1L
                );
            }
        );
    }

    private void tryRefreshMarketplace(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || pr == null) {
            return;
        }
        if (activeTab == Tab.COMMUNITY) {
            startCommunityCatalogRefresh(ref, store, pr, true);
            return;
        }
        if (activeTab == Tab.MODERATION && uc != null) {
            CommunityModerationService moderation = plugin.getCommunityModerationService();
            if (moderation.hasModeratorAccessResult(uc.getUuid()) && !moderation.isModerator(uc.getUuid())) {
                notifyNotModerator(pr, uc.getUuid());
                refresh(ref, store);
                return;
            }
            World world = store.getExternalData().getWorld();
            UUID playerUuid = uc.getUuid();
            if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
                return;
            }
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.refreshMarketplaceInProgress"),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            CompletableFuture.runAsync(
                () -> {
                    try {
                        if (!moderation.hasModeratorAccessResult(playerUuid)) {
                            moderation.refreshModeratorAccess(playerUuid);
                        }
                        if (!moderation.isModerator(playerUuid)) {
                            plugin.scheduleOnWorld(
                                world,
                                () -> {
                                    marketplaceRefreshInFlight.set(false);
                                    if (!ref.isValid() || isDismissed()) {
                                        return;
                                    }
                                    activeTab = Tab.CORE;
                                    notifyNotModerator(pr, playerUuid);
                                    refresh(ref, store);
                                },
                                1L
                            );
                            return;
                        }
                        moderation.refreshPending(playerUuid);
                        plugin.scheduleOnWorld(
                            world,
                            () -> {
                                marketplaceRefreshInFlight.set(false);
                                if (!ref.isValid() || isDismissed()) {
                                    return;
                                }
                                selectedGroupKey = null;
                                variantIndex = 0;
                                marketplacePageIndex = 0;
                                moderationPreviewSubmissionId = null;
                                NotificationUtil.sendNotification(
                                    pr.getPacketHandler(),
                                    Message.translation(
                                        "aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.refreshMarketplaceDone"
                                    ),
                                    NotificationStyle.Success
                                );
                                refresh(ref, store);
                            },
                            1L
                        );
                    } catch (Exception e) {
                        plugin.scheduleOnWorld(
                            world,
                            () -> {
                                marketplaceRefreshInFlight.set(false);
                                if (!ref.isValid() || isDismissed()) {
                                    return;
                                }
                                NotificationUtil.sendNotification(
                                    pr.getPacketHandler(),
                                    Message.translation(
                                        "aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.refreshMarketplaceFailed"
                                    ),
                                    NotificationStyle.Danger
                                );
                                refresh(ref, store);
                            },
                            1L
                        );
                    }
                }
            );
        }
    }

    private void maybeAutoRefreshCommunityCatalog(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null) {
            return;
        }
        CommunityCatalogService catalog = plugin.getCommunityCatalogService();
        if (!catalog.isEnabled() || !catalog.isCacheStale()) {
            return;
        }
        startCommunityCatalogRefresh(ref, store, pr, false);
    }

    /**
     * Switches to the Moderation tab immediately, then probes moderator access / queue off-thread.
     * Avoids freezing the client on the synchronous moderation API check.
     */
    private void startModerationTabLoad(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef pr,
        @Nonnull UUID moderatorUuid
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            activeTab = Tab.CORE;
            refresh(ref, store);
            return;
        }
        CommunityModerationService moderation = plugin.getCommunityModerationService();
        if (moderation.hasModeratorAccessResult(moderatorUuid)) {
            if (!moderation.isModerator(moderatorUuid)) {
                activeTab = Tab.CORE;
                notifyNotModerator(pr, moderatorUuid);
                refresh(ref, store);
                return;
            }
            if (moderation.isQueueEmpty()) {
                maybeAutoRefreshModerationQueue(ref, store, pr, moderatorUuid);
            } else {
                refresh(ref, store);
            }
            return;
        }
        if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
            refresh(ref, store);
            return;
        }
        refresh(ref, store);
        World world = store.getExternalData().getWorld();
        CompletableFuture.runAsync(
            () -> {
                try {
                    moderation.refreshModeratorAccess(moderatorUuid);
                    if (moderation.isModerator(moderatorUuid)) {
                        moderation.refreshPending(moderatorUuid);
                    }
                    plugin.scheduleOnWorld(
                        world,
                        () -> {
                            marketplaceRefreshInFlight.set(false);
                            if (!ref.isValid() || isDismissed()) {
                                return;
                            }
                            if (!moderation.isModerator(moderatorUuid)) {
                                activeTab = Tab.CORE;
                                notifyNotModerator(pr, moderatorUuid);
                            } else {
                                marketplacePageIndex = 0;
                            }
                            refresh(ref, store);
                        },
                        1L
                    );
                } catch (Exception e) {
                    plugin.scheduleOnWorld(
                        world,
                        () -> {
                            marketplaceRefreshInFlight.set(false);
                            if (!ref.isValid() || isDismissed()) {
                                return;
                            }
                            activeTab = Tab.CORE;
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation(
                                    "aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.refreshMarketplaceFailed"
                                ),
                                NotificationStyle.Danger
                            );
                            refresh(ref, store);
                        },
                        1L
                    );
                }
            }
        );
    }

    private void maybeAutoRefreshModerationQueue(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef pr,
        @Nonnull UUID moderatorUuid
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
            refresh(ref, store);
            return;
        }
        refresh(ref, store);
        World world = store.getExternalData().getWorld();
        CommunityModerationService moderation = plugin.getCommunityModerationService();
        CompletableFuture.runAsync(
            () -> {
                try {
                    moderation.refreshPending(moderatorUuid);
                    plugin.scheduleOnWorld(
                        world,
                        () -> {
                            marketplaceRefreshInFlight.set(false);
                            if (!ref.isValid() || isDismissed() || activeTab != Tab.MODERATION) {
                                return;
                            }
                            marketplacePageIndex = 0;
                            refresh(ref, store);
                        },
                        1L
                    );
                } catch (Exception e) {
                    plugin.scheduleOnWorld(
                        world,
                        () -> {
                            marketplaceRefreshInFlight.set(false);
                            if (!ref.isValid() || isDismissed()) {
                                return;
                            }
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation(
                                    "aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.refreshMarketplaceFailed"
                                ),
                                NotificationStyle.Danger
                            );
                            refresh(ref, store);
                        },
                        1L
                    );
                }
            }
        );
    }

    private void startCommunityCatalogRefresh(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef pr,
        boolean notifyStart
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        if (!marketplaceRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        if (notifyStart) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.refreshMarketplaceInProgress"),
                NotificationStyle.Warning
            );
        }
        refresh(ref, store);
        World world = store.getExternalData().getWorld();
        CommunityCatalogService catalog = plugin.getCommunityCatalogService();
        CompletableFuture.runAsync(
            () -> {
                boolean success = false;
                try {
                    success = catalog.refreshFromApi();
                } finally {
                    boolean ok = success;
                    plugin.scheduleOnWorld(
                        world,
                        () -> {
                            marketplaceRefreshInFlight.set(false);
                            if (!ref.isValid() || isDismissed()) {
                                return;
                            }
                            if (activeTab != Tab.COMMUNITY) {
                                return;
                            }
                            selectedGroupKey = null;
                            variantIndex = 0;
                            marketplacePageIndex = 0;
                            communityPreviewConstructionId = null;
                            NotificationUtil.sendNotification(
                                pr.getPacketHandler(),
                                Message.translation(
                                    ok
                                        ? "aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.refreshMarketplaceDone"
                                        : "aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.refreshMarketplaceFailed"
                                ),
                                ok ? NotificationStyle.Success : NotificationStyle.Danger
                            );
                            refresh(ref, store);
                        },
                        1L
                    );
                }
            }
        );
    }

    private void notifyNotModerator(@Nullable PlayerRef pr, @Nonnull UUID playerUuid) {
        if (pr == null) {
            return;
        }
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.notModerator")
                .param("uuid", Message.raw(playerUuid.toString())),
            NotificationStyle.Warning
        );
    }

    private void notifyMissingRequiredMods(
        @Nonnull PlayerRef playerRef,
        @Nullable List<CommunityRequiredMods.RequiredMod> requiredMods
    ) {
        List<String> missing = CommunityRequiredMods.missingPackNames(requiredMods);
        String modsLabel = missing.isEmpty() ? "unknown" : String.join(", ", missing);
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(),
            Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.missingRequiredMods")
                .param("mods", Message.raw(modsLabel)),
            NotificationStyle.Danger
        );
    }

    private void bindBuildingInfoPanel(
        @Nonnull UICommandBuilder b,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull CommunityCatalogService communityCatalog,
        @Nonnull CommunityModerationService moderation,
        @Nullable VariantEntry variant,
        boolean communityTab,
        boolean moderationTab,
        @Nullable PlayerRef playerRef
    ) {
        b.clear("#InfoRequiredModsRows");
        b.clear("#InfoMaterialRows");
        boolean hasSelection = variant != null;
        b.set("#BuildingInfoEmpty.Visible", !hasSelection);
        b.set("#BuildingInfoScroll.Visible", hasSelection);
        if (!hasSelection) {
            return;
        }

        String id = variant.constructionId();
        String name = variant.displayName();
        String description = "";
        String meta = id;
        long constructionGold = 0L;
        List<MaterialRequirement> materials = List.of();
        List<CommunityRequiredMods.RequiredMod> requiredMods = List.of();

        ConstructionDefinition definition = catalog.get(id);
        if (definition != null) {
            description = definition.getDescription() != null ? definition.getDescription().trim() : "";
            constructionGold = definition.getTreasuryGoldCoinCost();
            materials = definition.getMaterials();
        }
        if (communityTab) {
            CommunityManifestEntry entry = communityCatalog.findEntry(id);
            if (entry != null) {
                name = entry.getDisplayName();
                if (!entry.getDescription().isBlank() || definition == null) {
                    description = entry.getDescription().trim();
                }
                if (entry.getTreasuryGoldCoinCost() > 0L || definition == null) {
                    constructionGold = entry.getTreasuryGoldCoinCost();
                }
                if (!entry.getMaterials().isEmpty() || definition == null) {
                    materials = entry.getMaterials();
                }
                requiredMods = entry.getRequiredMods();
                meta = "by " + entry.getCreatorName() + "\n" + entry.getId() + " · " + formatPrefabSize(entry.getPrefabBytes());
            }
        } else if (moderationTab) {
            CommunityPendingEntry entry = moderation.findEntry(id);
            if (entry != null) {
                name = entry.getDisplayName();
                if (!entry.getDescription().isBlank() || definition == null) {
                    description = entry.getDescription().trim();
                }
                if (entry.getTreasuryGoldCoinCost() > 0L || definition == null) {
                    constructionGold = entry.getTreasuryGoldCoinCost();
                }
                if (!entry.getMaterials().isEmpty() || definition == null) {
                    materials = entry.getMaterials();
                }
                requiredMods = entry.getRequiredMods();
                meta = "by " + entry.getCreatorName() + "\n" + entry.getProposedId();
            }
        }

        b.set("#InfoBuildingName.TextSpans", Message.raw(name));
        b.set("#InfoBuildingMeta.TextSpans", Message.raw(meta));
        b.set("#InfoDescription.Visible", !description.isBlank());
        b.set("#InfoDescription.TextSpans", Message.raw(description));
        String buildCost = constructionGold + " gold to build from the town treasury";
        String goldDetails = moderationTab
            ? buildCost
            : CRAFT_COST + " gold to craft plot token\n" + buildCost;
        b.set("#InfoGoldValue.TextSpans", Message.raw(goldDetails));

        List<String> missingMods = CommunityRequiredMods.missingPackNames(requiredMods);
        if (requiredMods.isEmpty()) {
            b.append("#InfoRequiredModsRows", "Aetherhaven/PlotCraftingInfoModRow.ui");
            b.set("#InfoRequiredModsRows[0] #Name.TextSpans", Message.raw("No external mods required"));
            b.set("#InfoRequiredModsRows[0] #Id.Visible", false);
        } else {
            for (int i = 0; i < requiredMods.size(); i++) {
                CommunityRequiredMods.RequiredMod mod = requiredMods.get(i);
                String row = "#InfoRequiredModsRows[" + i + "]";
                b.append("#InfoRequiredModsRows", "Aetherhaven/PlotCraftingInfoModRow.ui");
                boolean missing = missingMods.contains(mod.getName());
                b.set(row + " #Name.TextSpans", Message.raw(mod.getName() + (missing ? " — NOT INSTALLED" : "")));
                b.set(row + " #Name.Style.TextColor", missing ? "#d66a6a" : "#e1d7ee");
                b.set(row + " #Id.TextSpans", Message.raw(mod.getId()));
            }
        }

        boolean missingRequiredMods =
            (communityTab || moderationTab) && !CommunityRequiredMods.isSatisfied(requiredMods);
        boolean downloadRequired =
            communityTab && !missingRequiredMods && !communityCatalog.isInstalled(id);
        boolean canInspectMaterials =
            !missingRequiredMods && !downloadRequired;
        List<MaterialRequirement> visibleMaterials = canInspectMaterials
            ? materials.stream().filter(m -> m != null && m.getCount() > 0).toList()
            : List.of();
        if (downloadRequired) {
            b.append("#InfoMaterialRows", "Aetherhaven/PlotCraftingInfoMaterialRow.ui");
            b.set("#InfoMaterialRows[0] #IconBox.Visible", false);
            b.set("#InfoMaterialRows[0] #Name.TextSpans", Message.raw("Download to view materials"));
            b.set("#InfoMaterialRows[0] #Name.Style.TextColor", "#96a9be");
            b.set("#InfoMaterialRows[0] #Count.TextSpans", Message.raw(""));
        } else if (missingRequiredMods) {
            b.append("#InfoMaterialRows", "Aetherhaven/PlotCraftingInfoMaterialRow.ui");
            b.set("#InfoMaterialRows[0] #IconBox.Visible", false);
            b.set(
                "#InfoMaterialRows[0] #Name.TextSpans",
                Message.raw("Install all required mods to inspect materials")
            );
            b.set("#InfoMaterialRows[0] #Name.Style.TextColor", "#d6a36a");
            b.set("#InfoMaterialRows[0] #Count.TextSpans", Message.raw(""));
        } else if (visibleMaterials.isEmpty()) {
            b.append("#InfoMaterialRows", "Aetherhaven/PlotCraftingInfoMaterialRow.ui");
            b.set("#InfoMaterialRows[0] #IconBox.Visible", false);
            b.set("#InfoMaterialRows[0] #Name.TextSpans", Message.raw("No materials required"));
            b.set("#InfoMaterialRows[0] #Count.TextSpans", Message.raw(""));
        } else {
            String language = playerRef != null ? playerRef.getLanguage() : "en-US";
            for (int i = 0; i < visibleMaterials.size(); i++) {
                MaterialRequirement material = visibleMaterials.get(i);
                String row = "#InfoMaterialRows[" + i + "]";
                b.append("#InfoMaterialRows", "Aetherhaven/PlotCraftingInfoMaterialRow.ui");
                String icon = UiMaterialIcons.assetPathFor(material);
                b.set(row + " #IconBox.Visible", icon != null && !icon.isBlank());
                if (icon != null && !icon.isBlank()) {
                    b.set(row + " #IconBox #Icon.AssetPath", icon);
                }
                b.set(
                    row + " #Name.TextSpans",
                    Message.raw(UiMaterialLabels.materialLabelForUi(language, material))
                );
                b.set(row + " #Count.TextSpans", Message.raw("×" + material.getCount()));
            }
        }
    }

    @Nonnull
    private static String formatPrefabSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void applyStyleFilterToggle(@Nonnull PageData data) {
        if (data.styleId == null || data.checked == null) {
            return;
        }
        String styleId = PlotBuildingStyles.normalize(data.styleId);
        if (styleId == null) {
            return;
        }
        if (data.checked) {
            activeStyleFilters.add(styleId);
        } else {
            activeStyleFilters.remove(styleId);
        }
    }

    private void bindCommunitySortDropdown(@Nonnull UICommandBuilder commandBuilder, boolean communityTab) {
        commandBuilder.set("#CommunitySort.Visible", communityTab);
        if (!communityTab) {
            return;
        }
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        entries.add(
            new DropdownEntryInfo(
                LocalizableString.fromMessageId("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.sortUpvotes"),
                CommunityCatalogSort.UPVOTES.value()
            )
        );
        entries.add(
            new DropdownEntryInfo(
                LocalizableString.fromMessageId("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.sortDownloads"),
                CommunityCatalogSort.DOWNLOADS.value()
            )
        );
        entries.add(
            new DropdownEntryInfo(
                LocalizableString.fromMessageId("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.sortLatest"),
                CommunityCatalogSort.LATEST.value()
            )
        );
        entries.add(
            new DropdownEntryInfo(
                LocalizableString.fromMessageId("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.sortName"),
                CommunityCatalogSort.NAME.value()
            )
        );
        commandBuilder.set("#CommunitySort.Entries", entries);
        commandBuilder.set("#CommunitySort.Value", communitySort.value());
    }

    @Nonnull
    private List<GroupEntry> filterGroupsBySearch(
        @Nonnull List<GroupEntry> groups,
        @Nonnull CommunityCatalogService communityCatalog,
        @Nonnull CommunityModerationService moderation,
        boolean communityTab,
        boolean moderationTab
    ) {
        if (searchQuery.isEmpty()) {
            return groups;
        }
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return groups;
        }
        List<GroupEntry> filtered = new ArrayList<>(groups.size());
        for (GroupEntry group : groups) {
            if (group.displayName().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(group);
                continue;
            }
            if (communityTab) {
                CommunityManifestEntry entry = communityCatalog.findEntry(group.groupKey());
                if (entry != null && entry.getCreatorName().toLowerCase(Locale.ROOT).contains(query)) {
                    filtered.add(group);
                }
            } else if (moderationTab) {
                CommunityPendingEntry entry = moderation.findEntry(group.groupKey());
                if (entry != null && entry.getCreatorName().toLowerCase(Locale.ROOT).contains(query)) {
                    filtered.add(group);
                }
            }
        }
        return filtered;
    }

    private void bindStyleFilters(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull ConstructionCatalog catalog,
        @Nullable CommunityCatalogService communityCatalog,
        boolean showStyleFilters
    ) {
        if (!showStyleFilters) {
            commandBuilder.clear(STYLE_ROWS);
            return;
        }
        List<String> styleIds =
            communityCatalog != null ? communityCatalog.listStyleIds() : PlotBuildingStyles.craftableStyleIds(catalog);
        activeStyleFilters.retainAll(styleIds);
        commandBuilder.clear(STYLE_ROWS);
        int rowOffset = 0;
        if (communityCatalog != null) {
            commandBuilder.append(STYLE_ROWS, "Aetherhaven/PlotCraftingStyleFilterRow.ui");
            String row = STYLE_ROWS + "[0]";
            commandBuilder.set(
                row + " #StyleLabel.TextSpans",
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.showMissingModsFilter")
            );
            commandBuilder.set(row + " #CheckBox.Value", showCommunityBuildsWithMissingMods);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                row + " #CheckBox",
                new EventData()
                    .append("Action", "CommunityMissingModsToggle")
                    .append("@Checked", row + " #CheckBox.Value"),
                false
            );
            rowOffset = 1;
        }
        for (int i = 0; i < styleIds.size(); i++) {
            String styleId = styleIds.get(i);
            commandBuilder.append(STYLE_ROWS, "Aetherhaven/PlotCraftingStyleFilterRow.ui");
            String row = STYLE_ROWS + "[" + (i + rowOffset) + "]";
            commandBuilder.set(row + " #StyleLabel.TextSpans", Message.raw(displayStyleLabel(styleId)));
            commandBuilder.set(row + " #CheckBox.Value", activeStyleFilters.contains(styleId));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                row + " #CheckBox",
                new EventData()
                    .append("Action", "StyleFilterToggle")
                    .append("StyleId", styleId)
                    .append("@Checked", row + " #CheckBox.Value"),
                false
            );
        }
    }

    @Nonnull
    private static String displayStyleLabel(@Nonnull String styleId) {
        if (styleId.isEmpty()) {
            return styleId;
        }
        String[] parts = styleId.replace('_', ' ').trim().split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                label.append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return label.length() > 0 ? label.toString() : styleId;
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        deferPreviewToSendUpdate = true;
        try {
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
        } finally {
            deferPreviewToSendUpdate = false;
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("GroupKey", Codec.STRING), (d, v) -> d.groupKey = v, d -> d.groupKey)
            .add()
            .append(new KeyedCodec<>("SelectedTab", Codec.STRING), (d, v) -> d.selectedTab = v, d -> d.selectedTab)
            .add()
            .append(new KeyedCodec<>("@SelectedTab", Codec.STRING), (d, v) -> d.selectedTab = v, d -> d.selectedTab)
            .add()
            .append(new KeyedCodec<>("StyleId", Codec.STRING), (d, v) -> d.styleId = v, d -> d.styleId)
            .add()
            .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (d, v) -> d.checked = v, d -> d.checked)
            .add()
            .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery)
            .add()
            .append(new KeyedCodec<>("@SortMode", Codec.STRING), (d, v) -> d.sortMode = v, d -> d.sortMode)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String groupKey;
        @Nullable
        private String selectedTab;
        @Nullable
        private String styleId;
        @Nullable
        private Boolean checked;
        @Nullable
        private String searchQuery;
        @Nullable
        private String sortMode;
    }
}
