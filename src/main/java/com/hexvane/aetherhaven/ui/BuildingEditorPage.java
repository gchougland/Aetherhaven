package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunityMySubmissionEntry;
import com.hexvane.aetherhaven.community.CommunityMySubmissionsService;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.plotcreator.BuildingEditorFestivalSessionStarter;
import com.hexvane.aetherhaven.plotcreator.BuildingEditorSessionStarter;
import com.hypixel.hytale.codec.Codec;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Searchable list of catalog buildings for the Creative building editor staff. */
public final class BuildingEditorPage extends AetherhavenInteractiveCustomUIPage<BuildingEditorPage.PageData> {
    private static final String MSG = "aetherhaven_building_editor.aetherhaven.buildingeditor";
    private static final String ROWS = "#BuildingRows";
    private static final String TAB_ALL = "All";
    private static final String TAB_MINE = "Mine";
    private static final String TAB_FESTIVALS = "Festivals";

    private enum Tab {
        ALL,
        MINE,
        FESTIVALS
    }

    @Nonnull
    private String searchQuery = "";
    @Nonnull
    private Tab activeTab = Tab.ALL;
    @Nonnull
    private List<CommunityMySubmissionEntry> mySubmissions = List.of();
    private boolean mySubmissionsLoaded;
    private final AtomicBoolean mySubmissionsFetchInFlight = new AtomicBoolean();
    private final AtomicBoolean mineEditInFlight = new AtomicBoolean();
    private boolean templateAppended;

    public BuildingEditorPage(@Nonnull PlayerRef playerRef) {
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
            commandBuilder.append("Aetherhaven/BuildingEditorPage.ui");
            templateAppended = true;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "Close"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.SelectedTabChanged,
                "#EditorTabs",
                new EventData().append("Action", "TabChange").append("@SelectedTab", "#EditorTabs.SelectedTab"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RefreshButton",
                EventData.of("Action", "RefreshMine"),
                false
            );
        }

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        boolean marketplaceEnabled =
            plugin != null && plugin.getConfig().get().getCommunityMarketplace().isEnabled();
        boolean mineTab = activeTab == Tab.MINE;
        boolean festivalsTab = activeTab == Tab.FESTIVALS;
        boolean loadingMine = mineTab && (mySubmissionsFetchInFlight.get() || mineEditInFlight.get());
        boolean showMineEmpty = mineTab && mySubmissionsLoaded && filteredMineSubmissions().isEmpty();
        boolean showFestivalsEmpty = festivalsTab && filteredFestivals().isEmpty();

        commandBuilder.set("#EditorTitleText.TextSpans", Message.translation(MSG + ".title"));
        String hintKey =
            mineTab ? MSG + ".hint.pickMine" : festivalsTab ? MSG + ".hint.pickFestival" : MSG + ".hint.pick";
        commandBuilder.set("#StepHint.TextSpans", Message.translation(hintKey));
        commandBuilder.set("#StepHint.Visible", !loadingMine && !showMineEmpty && !showFestivalsEmpty);
        commandBuilder.set("#LoadingLabel.Visible", loadingMine);
        commandBuilder.set("#LoadingLabel.TextSpans", Message.translation(MSG + ".loadingMine"));
        commandBuilder.set("#EmptyLabel.Visible", showMineEmpty || showFestivalsEmpty);
        commandBuilder.set(
            "#EmptyLabel.TextSpans",
            Message.translation(showFestivalsEmpty ? MSG + ".emptyFestivals" : MSG + ".emptyMine")
        );
        commandBuilder.set("#CloseButton.TextSpans", Message.translation(MSG + ".button.close"));
        commandBuilder.set("#SearchInput.Value", searchQuery);
        commandBuilder.set(
            "#SearchInput.PlaceholderText",
            Message.translation(festivalsTab ? MSG + ".searchFestivalPlaceholder" : MSG + ".searchPlaceholder")
        );
        commandBuilder.set("#EditorTabs.SelectedTab", tabId(activeTab));
        commandBuilder.set("#RefreshRow.Visible", mineTab && marketplaceEnabled);
        commandBuilder.set("#RefreshLabel.TextSpans", Message.translation(MSG + ".refreshMine"));
        commandBuilder.set("#RefreshButton.Disabled", loadingMine || mineEditInFlight.get());

        commandBuilder.clear(ROWS);
        if (mineTab) {
            buildMineRows(commandBuilder, eventBuilder, plugin);
        } else if (festivalsTab) {
            buildFestivalRows(commandBuilder, eventBuilder, plugin);
        } else {
            buildAllRows(commandBuilder, eventBuilder, plugin);
        }

        if (mineTab && marketplaceEnabled && !mySubmissionsLoaded && !mySubmissionsFetchInFlight.get()) {
            startMineSubmissionsFetch(ref, store);
        }
    }

    private void buildAllRows(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable AetherhavenPlugin plugin
    ) {
        List<ConstructionDefinition> buildings = filteredBuildings();
        for (int i = 0; i < buildings.size(); i++) {
            ConstructionDefinition def = buildings.get(i);
            commandBuilder.append(ROWS, "Aetherhaven/BuildingEditorRow.ui");
            String row = ROWS + "[" + i + "]";
            commandBuilder.set(row + " #SelectHilite.Visible", false);
            commandBuilder.set(row + " #BuildingName.TextSpans", Message.raw(displayName(def)));
            commandBuilder.set(row + " #BuildingId.TextSpans", Message.raw(def.getId()));
            String iconPath =
                plugin != null
                    ? ConstructionTokenIconPath.forConstruction(def, plugin.getDataDirectory())
                    : ConstructionTokenIconPath.forConstruction(def);
            commandBuilder.set(row + " #IconBox #BuildingIcon.AssetPath", iconPath);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                EventData.of("Action", "Select").append("ConstructionId", def.getId()),
                false
            );
        }
    }

    private void buildFestivalRows(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable AetherhavenPlugin plugin
    ) {
        List<FestivalChoice> choices = filteredFestivals();
        for (int i = 0; i < choices.size(); i++) {
            FestivalChoice choice = choices.get(i);
            commandBuilder.append(ROWS, "Aetherhaven/BuildingEditorRow.ui");
            String row = ROWS + "[" + i + "]";
            commandBuilder.set(row + " #SelectHilite.Visible", false);
            if (choice.labelLang() != null) {
                commandBuilder.set(row + " #BuildingName.TextSpans", Message.translation(choice.labelLang()));
            } else {
                commandBuilder.set(row + " #BuildingName.TextSpans", Message.raw(choice.fallbackLabel()));
            }
            commandBuilder.set(
                row + " #BuildingId.TextSpans",
                Message.raw(choice.festivalId() != null ? choice.festivalId() : "")
            );
            commandBuilder.set(row + " #IconBox #BuildingIcon.AssetPath", festivalIconPath(choice, plugin));
            EventData select =
                EventData.of("Action", "SelectFestival")
                    .append("FestivalId", choice.festivalId() != null ? choice.festivalId() : "");
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                select,
                false
            );
        }
    }

    @Nonnull
    private static String festivalIconPath(@Nonnull FestivalChoice choice, @Nullable AetherhavenPlugin plugin) {
        if (choice.festivalId() == null) {
            return "UI/Custom/flags.png";
        }
        if (plugin != null) {
            FestivalDefinition def = plugin.getFestivalCatalog().get(choice.festivalId());
            if (def != null) {
                return def.getCalendarIconPath();
            }
        }
        return "UI/Custom/flags.png";
    }

    private void buildMineRows(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable AetherhavenPlugin plugin
    ) {
        List<CommunityMySubmissionEntry> entries = filteredMineSubmissions();
        for (int i = 0; i < entries.size(); i++) {
            CommunityMySubmissionEntry entry = entries.get(i);
            String catalogId = entry.catalogId();
            commandBuilder.append(ROWS, "Aetherhaven/BuildingEditorRow.ui");
            String row = ROWS + "[" + i + "]";
            commandBuilder.set(row + " #SelectHilite.Visible", false);
            commandBuilder.set(row + " #BuildingName.TextSpans", Message.raw(entry.getDisplayName()));
            commandBuilder.set(row + " #BuildingId.TextSpans", mineRowDetailMessage(entry));
            String iconPath =
                plugin != null
                    ? ConstructionTokenIconPath.forConstructionId(catalogId, plugin.getDataDirectory())
                    : ConstructionTokenIconPath.forConstructionId(catalogId, null);
            commandBuilder.set(row + " #IconBox #BuildingIcon.AssetPath", iconPath);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                EventData.of("Action", "SelectMine").append("ConstructionId", catalogId),
                false
            );
        }
    }

    @Nonnull
    private Message mineRowDetailMessage(@Nonnull CommunityMySubmissionEntry entry) {
        String statusKey =
            entry.isUpdateWaiting()
                ? MSG + ".status.updateWaiting"
                : entry.isPending()
                    ? MSG + ".status.pending"
                    : entry.isRejected()
                        ? MSG + ".status.rejected"
                        : MSG + ".status.live";
        return Message.translation(MSG + ".rowMine")
            .param("id", entry.catalogId())
            .param("version", entry.getVersion())
            .param("status", Message.translation(statusKey));
    }

    @Nonnull
    private List<ConstructionDefinition> filteredBuildings() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return List.of();
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        List<ConstructionDefinition> out = new ArrayList<>();
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        for (ConstructionDefinition def : catalog.list()) {
            if (def == null || def.getId() == null || def.isWallSegment()) {
                continue;
            }
            if (!q.isEmpty()) {
                String id = def.getId().toLowerCase(Locale.ROOT);
                String name = displayName(def).toLowerCase(Locale.ROOT);
                if (!id.contains(q) && !name.contains(q)) {
                    continue;
                }
            }
            out.add(def);
        }
        out.sort(Comparator.comparing(d -> displayName(d).toLowerCase(Locale.ROOT)));
        return out;
    }

    @Nonnull
    private List<CommunityMySubmissionEntry> filteredMineSubmissions() {
        List<CommunityMySubmissionEntry> out = new ArrayList<>();
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        for (CommunityMySubmissionEntry entry : mySubmissions) {
            if (!q.isEmpty()) {
                String id = entry.catalogId().toLowerCase(Locale.ROOT);
                String name = entry.getDisplayName().toLowerCase(Locale.ROOT);
                if (!id.contains(q) && !name.contains(q)) {
                    continue;
                }
            }
            out.add(entry);
        }
        return out;
    }

    @Nonnull
    private List<FestivalChoice> filteredFestivals() {
        List<FestivalChoice> out = new ArrayList<>();
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            List<FestivalDefinition> defs = new ArrayList<>(plugin.getFestivalCatalog().list());
            defs.sort(Comparator.comparing(d -> d.getDisplayName().toLowerCase(Locale.ROOT)));
            for (FestivalDefinition def : defs) {
                out.add(new FestivalChoice(def.getId(), def.getDisplayNameLangKey(), def.getDisplayName()));
            }
        }
        if (q.isEmpty()) {
            return out;
        }
        List<FestivalChoice> filtered = new ArrayList<>();
        for (FestivalChoice choice : out) {
            String id = choice.festivalId() != null ? choice.festivalId().toLowerCase(Locale.ROOT) : "";
            String name = choice.fallbackLabel().toLowerCase(Locale.ROOT);
            if (id.contains(q) || name.contains(q)) {
                filtered.add(choice);
            }
        }
        return filtered;
    }

    private record FestivalChoice(
        @Nullable String festivalId,
        @Nullable String labelLang,
        @Nonnull String fallbackLabel
    ) {}

    @Nonnull
    private static String displayName(@Nonnull ConstructionDefinition def) {
        String name = def.getDisplayName();
        return name != null && !name.isBlank() ? name : def.getId();
    }

    private static String tabId(@Nonnull Tab tab) {
        return switch (tab) {
            case MINE -> TAB_MINE;
            case FESTIVALS -> TAB_FESTIVALS;
            case ALL -> TAB_ALL;
        };
    }

    private static Tab parseTab(@Nullable String tabId) {
        String id = tabId != null ? tabId.trim() : "";
        if (TAB_MINE.equalsIgnoreCase(id)) {
            return Tab.MINE;
        }
        if (TAB_FESTIVALS.equalsIgnoreCase(id)) {
            return Tab.FESTIVALS;
        }
        return Tab.ALL;
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    private void startMineSubmissionsFetch(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null || !mySubmissionsFetchInFlight.compareAndSet(false, true)) {
            return;
        }
        refresh(ref, store);
        World world = store.getExternalData().getWorld();
        String playerName = pr.getUsername() != null ? pr.getUsername() : "Unknown";
        CompletableFuture.runAsync(
            () -> {
                List<CommunityMySubmissionEntry> fetched =
                    CommunityMySubmissionsService.fetchOwnedSubmissions(plugin, pr.getUuid(), playerName);
                plugin.scheduleOnWorld(
                    world,
                    () -> {
                        mySubmissionsFetchInFlight.set(false);
                        if (!ref.isValid() || isDismissed() || activeTab != Tab.MINE) {
                            return;
                        }
                        mySubmissions = fetched;
                        mySubmissionsLoaded = true;
                        refresh(ref, store);
                    },
                    1L
                );
            }
        );
    }

    private void startMineEdit(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String catalogId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null) {
            return;
        }
        CommunityMySubmissionEntry entry = findMineEntry(catalogId);
        if (entry == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.unknownBuilding").param("id", catalogId));
            return;
        }
        if (!mineEditInFlight.compareAndSet(false, true)) {
            return;
        }
        refresh(ref, store);
        World world = store.getExternalData().getWorld();
        String playerName = pr.getUsername() != null ? pr.getUsername() : "Unknown";
        boolean liveOnMarketplace = entry.hasLiveVersion();
        CompletableFuture.runAsync(
            () -> {
                String err =
                    CommunityMySubmissionsService.ensureLocalFilesForEdit(plugin, entry, pr.getUuid(), playerName);
                plugin.scheduleOnWorld(
                    world,
                    () -> {
                        mineEditInFlight.set(false);
                        if (!ref.isValid() || isDismissed()) {
                            return;
                        }
                        if (err != null) {
                            playerRef.sendMessage(
                                Message.translation(MSG + ".error.prepareMine")
                                    .param("reason", Message.raw(err))
                            );
                            refresh(ref, store);
                            return;
                        }
                        plugin.reloadConfigsAndAssetCatalogs();
                        BuildingEditorSessionStarter.startFromConstructionId(
                            playerRef,
                            ref,
                            store,
                            catalogId,
                            true,
                            liveOnMarketplace
                        );
                    },
                    1L
                );
            }
        );
    }

    @Nullable
    private CommunityMySubmissionEntry findMineEntry(@Nonnull String catalogId) {
        for (CommunityMySubmissionEntry entry : mySubmissions) {
            if (entry.catalogId().equalsIgnoreCase(catalogId.trim())) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (data.searchQuery != null) {
            searchQuery = data.searchQuery;
            refresh(ref, store);
            return;
        }
        if ("TabChange".equals(data.action)) {
            Tab next = parseTab(data.selectedTab);
            if (next != activeTab) {
                activeTab = next;
                if (next == Tab.MINE) {
                    mySubmissionsLoaded = false;
                }
                refresh(ref, store);
            }
            return;
        }
        if ("RefreshMine".equals(data.action)) {
            mySubmissionsLoaded = false;
            refresh(ref, store);
            return;
        }
        if ("Close".equals(data.action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }
        if ("Select".equals(data.action) && data.constructionId != null && !data.constructionId.isBlank()) {
            BuildingEditorSessionStarter.startFromConstructionId(playerRef, ref, store, data.constructionId);
            return;
        }
        if ("SelectMine".equals(data.action) && data.constructionId != null && !data.constructionId.isBlank()) {
            startMineEdit(ref, store, data.constructionId);
            return;
        }
        if ("SelectFestival".equals(data.action)
            && data.festivalId != null
            && !data.festivalId.isBlank()) {
            BuildingEditorFestivalSessionStarter.start(playerRef, ref, store, data.festivalId.trim());
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("ConstructionId", Codec.STRING), (d, v) -> d.constructionId = v, d -> d.constructionId)
            .add()
            .append(new KeyedCodec<>("FestivalId", Codec.STRING), (d, v) -> d.festivalId = v, d -> d.festivalId)
            .add()
            .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery)
            .add()
            .append(new KeyedCodec<>("@SelectedTab", Codec.STRING), (d, v) -> d.selectedTab = v, d -> d.selectedTab)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String constructionId;
        @Nullable
        private String festivalId;
        @Nullable
        private String searchQuery;
        @Nullable
        private String selectedTab;
    }
}
