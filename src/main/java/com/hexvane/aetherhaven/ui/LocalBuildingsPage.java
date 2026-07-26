package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plotcreator.LocalBuildingRemovalService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Manage and remove local custom buildings saved from plot creator staff. */
public final class LocalBuildingsPage extends AetherhavenInteractiveCustomUIPage<LocalBuildingsPage.PageData> {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator.localBuildings";
    private static final String ROWS = "#BuildingRows";

    @Nonnull
    private String searchQuery = "";
    @Nullable
    private String pendingRemoveConstructionId;
    private boolean removeConfirmOpen;
    private int pendingRemovePlotCount;
    private boolean templateAppended;

    public LocalBuildingsPage(@Nonnull PlayerRef playerRef) {
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
            commandBuilder.append("Aetherhaven/LocalBuildingsPage.ui");
            templateAppended = true;
        }

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        List<ConstructionDefinition> buildings = filteredBuildings(plugin);
        boolean modalBlocking = removeConfirmOpen;
        boolean empty = buildings.isEmpty();

        commandBuilder.set("#PageTitleText.TextSpans", Message.translation(MSG + ".title"));
        commandBuilder.set("#StepHint.TextSpans", Message.translation(MSG + ".hint"));
        commandBuilder.set("#StepHint.Visible", !empty && !modalBlocking);
        commandBuilder.set("#EmptyLabel.Visible", empty && !modalBlocking);
        commandBuilder.set("#EmptyLabel.TextSpans", Message.translation(MSG + ".empty"));
        commandBuilder.set("#CloseButton.TextSpans", Message.translation(MSG + ".button.close"));
        commandBuilder.set("#SearchInput.Value", searchQuery);
        commandBuilder.set("#SearchInput.PlaceholderText", Message.translation(MSG + ".searchPlaceholder"));

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

        commandBuilder.set("#RemoveConfirmModal.Visible", modalBlocking);
        if (modalBlocking) {
            commandBuilder.set("#RemoveConfirmTitle.TextSpans", Message.translation(MSG + ".confirm.title"));
            if (pendingRemovePlotCount > 0) {
                commandBuilder.set(
                    "#RemoveConfirmText.TextSpans",
                    Message.translation(MSG + ".confirm.withPlots")
                        .param("count", pendingRemovePlotCount)
                        .param("name", pendingRemoveDisplayName(plugin))
                );
            } else {
                commandBuilder.set(
                    "#RemoveConfirmText.TextSpans",
                    Message.translation(MSG + ".confirm.noPlots").param("name", pendingRemoveDisplayName(plugin))
                );
            }
            commandBuilder.set("#RemoveConfirmButton.TextSpans", Message.translation(MSG + ".confirm.remove"));
            commandBuilder.set("#RemoveCancelButton.TextSpans", Message.translation(MSG + ".confirm.cancel"));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RemoveConfirmButton",
                EventData.of("Action", "RemoveConfirm"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RemoveCancelButton",
                EventData.of("Action", "RemoveCancel"),
                false
            );
            return;
        }

        commandBuilder.clear(ROWS);
        Map<String, LocalBuildingRemovalService.PlotCount> plotCounts = plotCounts(plugin, buildings);
        for (int i = 0; i < buildings.size(); i++) {
            ConstructionDefinition def = buildings.get(i);
            String id = def.getId();
            commandBuilder.append(ROWS, "Aetherhaven/LocalBuildingsRow.ui");
            String row = ROWS + "[" + i + "]";
            commandBuilder.set(row + " #BuildingName.TextSpans", Message.raw(displayName(def)));
            int plotCount = plotCounts.getOrDefault(id, new LocalBuildingRemovalService.PlotCount(0, 0)).total();
            commandBuilder.set(
                row + " #BuildingDetail.TextSpans",
                Message.translation(MSG + ".rowDetail").param("id", id).param("count", plotCount)
            );
            String iconPath =
                plugin != null
                    ? ConstructionTokenIconPath.forConstruction(def, plugin.getDataDirectory())
                    : ConstructionTokenIconPath.forConstruction(def);
            commandBuilder.set(row + " #IconBox #BuildingIcon.AssetPath", iconPath);
            commandBuilder.set(row + " #RemoveButton.TextSpans", Message.translation(MSG + ".button.remove"));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #RemoveButton",
                EventData.of("Action", "BeginRemoveConfirm").append("ConstructionId", id),
                false
            );
        }
    }

    @Nonnull
    private static Map<String, LocalBuildingRemovalService.PlotCount> plotCounts(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull List<ConstructionDefinition> buildings
    ) {
        Map<String, LocalBuildingRemovalService.PlotCount> out = new HashMap<>();
        if (plugin == null) {
            return out;
        }
        for (ConstructionDefinition def : buildings) {
            out.put(def.getId(), LocalBuildingRemovalService.countPlots(plugin, def.getId()));
        }
        return out;
    }

    @Nonnull
    private Message pendingRemoveDisplayName(@Nullable AetherhavenPlugin plugin) {
        if (plugin == null || pendingRemoveConstructionId == null) {
            return Message.raw("");
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(pendingRemoveConstructionId);
        return Message.raw(def != null ? displayName(def) : pendingRemoveConstructionId);
    }

    @Nonnull
    private List<ConstructionDefinition> filteredBuildings(@Nullable AetherhavenPlugin plugin) {
        if (plugin == null) {
            return List.of();
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        List<ConstructionDefinition> out = new ArrayList<>();
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        for (String id : catalog.customConstructionIds()) {
            ConstructionDefinition def = catalog.get(id);
            if (def == null) {
                continue;
            }
            if (!q.isEmpty()) {
                String name = displayName(def).toLowerCase(Locale.ROOT);
                if (!id.toLowerCase(Locale.ROOT).contains(q) && !name.contains(q)) {
                    continue;
                }
            }
            out.add(def);
        }
        out.sort(Comparator.comparing(d -> displayName(d).toLowerCase(Locale.ROOT)));
        return out;
    }

    @Nonnull
    private static String displayName(@Nonnull ConstructionDefinition def) {
        String name = def.getDisplayName();
        return name != null && !name.isBlank() ? name : def.getId();
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
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
        if ("Close".equals(data.action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }
        if ("BeginRemoveConfirm".equals(data.action) && data.constructionId != null && !data.constructionId.isBlank()) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            String id = data.constructionId.trim();
            pendingRemoveConstructionId = id;
            pendingRemovePlotCount = LocalBuildingRemovalService.countPlots(plugin, id).total();
            removeConfirmOpen = true;
            refresh(ref, store);
            return;
        }
        if ("RemoveCancel".equals(data.action)) {
            pendingRemoveConstructionId = null;
            pendingRemovePlotCount = 0;
            removeConfirmOpen = false;
            refresh(ref, store);
            return;
        }
        if ("RemoveConfirm".equals(data.action)) {
            World world = store.getExternalData().getWorld();
            world.execute(() -> confirmRemove(ref, store));
            return;
        }
    }

    private void confirmRemove(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        String id = pendingRemoveConstructionId;
        pendingRemoveConstructionId = null;
        pendingRemovePlotCount = 0;
        removeConfirmOpen = false;
        if (id == null || id.isBlank()) {
            refresh(ref, store);
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            refresh(ref, store);
            return;
        }
        LocalBuildingRemovalService.PlotCount before = LocalBuildingRemovalService.countPlots(plugin, id);
        LocalBuildingRemovalService.Result result = LocalBuildingRemovalService.remove(plugin, id);
        switch (result) {
            case SUCCESS -> {
                playerRef.sendMessage(Message.translation(MSG + ".removed").param("id", id));
                if (before.unloadedWorldPlots() > 0) {
                    playerRef.sendMessage(
                        Message.translation(MSG + ".removedUnloadedPlots").param("count", before.unloadedWorldPlots())
                    );
                }
            }
            case NOT_CUSTOM -> playerRef.sendMessage(Message.translation(MSG + ".error.notCustom").param("id", id));
            case SESSION_ACTIVE -> playerRef.sendMessage(Message.translation(MSG + ".error.sessionActive"));
            case CHUNKS_NOT_LOADED -> playerRef.sendMessage(Message.translation(MSG + ".error.chunksNotLoaded"));
            case IO_ERROR -> playerRef.sendMessage(Message.translation(MSG + ".error.failed").param("id", id));
        }
        refresh(ref, store);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(
                new KeyedCodec<>("ConstructionId", Codec.STRING),
                (d, v) -> d.constructionId = v,
                d -> d.constructionId
            )
            .add()
            .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String constructionId;
        @Nullable
        private String searchQuery;
    }
}
