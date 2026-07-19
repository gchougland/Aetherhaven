package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Searchable list of catalog buildings for the Creative building editor staff. */
public final class BuildingEditorPage extends AetherhavenInteractiveCustomUIPage<BuildingEditorPage.PageData> {
    private static final String MSG = "aetherhaven_building_editor.aetherhaven.buildingeditor";
    private static final String ROWS = "#BuildingRows";

    @Nonnull
    private String searchQuery = "";
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
        }
        commandBuilder.set("#EditorTitleText.TextSpans", Message.translation(MSG + ".title"));
        commandBuilder.set("#StepHint.TextSpans", Message.translation(MSG + ".hint.pick"));
        commandBuilder.set("#CloseButton.TextSpans", Message.translation(MSG + ".button.close"));
        commandBuilder.set("#SearchInput.Value", searchQuery);
        commandBuilder.set("#SearchInput.PlaceholderText", Message.translation(MSG + ".searchPlaceholder"));

        List<ConstructionDefinition> buildings = filteredBuildings();
        commandBuilder.clear(ROWS);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
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
        if ("Select".equals(data.action) && data.constructionId != null && !data.constructionId.isBlank()) {
            // Do not setPage(None) here: that increments PageManager's custom-page ack counter, and opening
            // Important Spots after paste increments again — Done/Close Data events stay ignored (Escape still works).
            // openCustomPage in finishSessionAfterPaste replaces this page without the double-ack race.
            BuildingEditorSessionStarter.startFromConstructionId(playerRef, ref, store, data.constructionId);
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("ConstructionId", Codec.STRING), (d, v) -> d.constructionId = v, d -> d.constructionId)
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
