package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorMaterialsActions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorMaterialsHelper;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
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
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Build materials menu: draft-only prefab list with count editing (no spawned items). */
public final class PlotCreatorMaterialsPage extends AetherhavenInteractiveCustomUIPage<PlotCreatorMaterialsPage.PageData> {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";
    private static final String MATERIAL_ROWS = "#MaterialRows";

    @Nonnull
    private final PlotCreatorSession session;
    private boolean templateAppended;

    public PlotCreatorMaterialsPage(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.session = session;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PlotCreatorMaterialsPage.ui");
            templateAppended = true;
            bindStaticButtons(eventBuilder);
        }
        applyContent(commandBuilder, eventBuilder);
    }

    private void bindStaticButtons(@Nonnull UIEventBuilder eventBuilder) {
        bind(eventBuilder, "#AddFromInventoryButton", "AddFromInventory");
        bind(eventBuilder, "#AddResourceTypeButton", "AddResourceType");
        bind(eventBuilder, "#FillFromBuildShapeButton", "FillFromBuildShape");
        bind(eventBuilder, "#SuggestResourcesButton", "SuggestResources");
        bind(eventBuilder, "#ClearPrefabMaterialsButton", "ClearPrefabMaterials");
        bind(eventBuilder, "#MaterialsPrevPageButton", "MaterialsPrevPage");
        bind(eventBuilder, "#MaterialsNextPageButton", "MaterialsNextPage");
        bind(eventBuilder, "#CloseButton", "Close");
    }

    private void bind(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            selector,
            EventData.of("Action", action),
            false
        );
    }

    private void applyContent(@Nonnull UICommandBuilder b, @Nonnull UIEventBuilder eventBuilder) {
        int pages = PlotCreatorMaterialsHelper.pageCount(session.getDraft());
        int page = PlotCreatorMaterialsHelper.clampPageIndex(session) + 1;
        boolean multiPage = pages > 1;
        boolean hasMaterials = !session.getDraft().getMaterials().isEmpty();

        b.set("#MaterialsTitleText.TextSpans", Message.translation(MSG + ".step.MATERIALS.title"));
        b.set("#StepHint.TextSpans", Message.translation(MSG + ".step.MATERIALS.hint"));
        b.set("#DetailHint.TextSpans", Message.translation(MSG + ".step.MATERIALS.detail"));
        b.set("#AddFromInventoryButton.TextSpans", Message.translation(MSG + ".button.addFromInventory"));
        b.set("#AddResourceTypeButton.TextSpans", Message.translation(MSG + ".button.addResourceType"));
        b.set("#FillFromBuildShapeButton.TextSpans", Message.translation(MSG + ".button.useBuildShape"));
        b.set("#SuggestResourcesButton.TextSpans", Message.translation(MSG + ".button.suggestResources"));
        b.set("#ClearPrefabMaterialsButton.TextSpans", Message.translation(MSG + ".button.clearPrefabMaterials"));
        b.set("#CloseButton.TextSpans", Message.translation(MSG + ".button.close"));

        b.set("#PageLabel.Visible", multiPage);
        b.set("#PageButtonRow.Visible", multiPage);
        if (multiPage) {
            b.set(
                "#PageLabel.TextSpans",
                Message.translation(MSG + ".materials.pageLabel").param("page", page).param("pages", pages)
            );
            b.set("#MaterialsPrevPageButton.TextSpans", Message.translation(MSG + ".button.materialsPrevPage"));
            b.set("#MaterialsNextPageButton.TextSpans", Message.translation(MSG + ".button.materialsNextPage"));
            b.set("#MaterialsPrevPageButton.Disabled", page <= 1);
            b.set("#MaterialsNextPageButton.Disabled", page >= pages);
        }

        b.set("#EmptyHint.Visible", !hasMaterials);
        if (!hasMaterials) {
            b.set("#EmptyHint.TextSpans", Message.translation(MSG + ".materials.emptyList"));
        }

        b.set("#ClearPrefabMaterialsButton.Visible", hasMaterials);
        b.clear(MATERIAL_ROWS);

        List<MaterialRequirement> pageItems = PlotCreatorMaterialsHelper.materialsPage(session);
        int pageStart = PlotCreatorMaterialsHelper.clampPageIndex(session) * PlotCreatorMaterialsHelper.UI_ROWS_PER_PAGE;
        for (int i = 0; i < pageItems.size(); i++) {
            MaterialRequirement line = pageItems.get(i);
            int materialIndex = pageStart + i;
            b.append(MATERIAL_ROWS, "Aetherhaven/PlotCreatorMaterialEditRow.ui");
            String row = MATERIAL_ROWS + "[" + i + "]";

            String iconPath = UiMaterialIcons.assetPathFor(line);
            if (iconPath != null && !iconPath.isBlank()) {
                b.set(row + " #Icon.AssetPath", iconPath);
            }
            b.set(row + " #Name.TextSpans", Message.raw(UiMaterialLabels.displayLabelFor(line)));
            b.set(row + " #CountField.Value", String.valueOf(line.getCount()));
            b.set(row + " #MinusButton.Text", "-");
            b.set(row + " #PlusButton.Text", "+");
            b.set(row + " #RemoveButton.Text", Message.translation(MSG + ".button.materialRemove"));

            bindRow(eventBuilder, row + " #MinusButton", "AdjustCount", materialIndex, -1);
            bindRow(eventBuilder, row + " #PlusButton", "AdjustCount", materialIndex, 1);
            bindRow(eventBuilder, row + " #RemoveButton", "RemoveMaterial", materialIndex, 0);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                row + " #CountField",
                EventData.of("Action", "SetCount")
                    .append("MaterialIndex", String.valueOf(materialIndex))
                    .append("@Count", row + " #CountField.Value"),
                false
            );
        }
    }

    private void bindRow(
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull String selector,
        @Nonnull String action,
        int materialIndex,
        int delta
    ) {
        EventData data = EventData.of("Action", action).append("MaterialIndex", String.valueOf(materialIndex));
        if ("AdjustCount".equals(action)) {
            data = data.append("Delta", String.valueOf(delta));
        }
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, data, false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if ("AddFromInventory".equals(data.action)) {
            PlotCreatorMaterialsActions.openManualDepositChest(session, playerRef, ref, store);
            return;
        }
        if ("AddResourceType".equals(data.action)) {
            PlotCreatorMaterialsActions.openResourceTypePicker(session, playerRef, ref, store);
            return;
        }
        if ("FillFromBuildShape".equals(data.action)) {
            PlotCreatorMaterialsActions.requestFillFromBuildShape(session, playerRef, () -> refreshIfOpen(ref, store));
            refreshIfOpen(ref, store);
            return;
        }
        if ("SuggestResources".equals(data.action)) {
            PlotCreatorMaterialsActions.requestSuggestResources(session, playerRef, () -> refreshIfOpen(ref, store));
            refreshIfOpen(ref, store);
            return;
        }
        if ("ClearPrefabMaterials".equals(data.action)) {
            PlotCreatorMaterialsActions.requestClearPrefabMaterials(session, playerRef);
            refreshIfOpen(ref, store);
            return;
        }
        if ("MaterialsPrevPage".equals(data.action)) {
            PlotCreatorMaterialsActions.changeMaterialsPage(
                session,
                playerRef,
                ref,
                store,
                -1,
                () -> refreshIfOpen(ref, store)
            );
            return;
        }
        if ("MaterialsNextPage".equals(data.action)) {
            PlotCreatorMaterialsActions.changeMaterialsPage(
                session,
                playerRef,
                ref,
                store,
                1,
                () -> refreshIfOpen(ref, store)
            );
            return;
        }
        if ("AdjustCount".equals(data.action) && data.materialIndex != null && data.delta != null) {
            int index = parseInt(data.materialIndex, -1);
            int delta = parseInt(data.delta, 0);
            if (index >= 0 && delta != 0) {
                PlotCreatorMaterialsActions.adjustMaterialCount(session, index, delta, () -> refreshIfOpen(ref, store));
            }
            return;
        }
        if ("SetCount".equals(data.action) && data.materialIndex != null && data.count != null) {
            String raw = data.count.trim();
            if (raw.isEmpty()) {
                return;
            }
            int index = parseInt(data.materialIndex, -1);
            Integer parsed = parsePositiveIntOrNull(raw);
            if (index < 0 || parsed == null) {
                return;
            }
            // ValueChanged only: keep draft in sync without re-pushing the field being typed.
            PlotCreatorMaterialsActions.setMaterialCount(session, index, parsed, () -> {
                if (parsed <= 0) {
                    refreshIfOpen(ref, store);
                }
            });
            return;
        }
        if ("RemoveMaterial".equals(data.action) && data.materialIndex != null) {
            int index = parseInt(data.materialIndex, -1);
            if (index >= 0) {
                PlotCreatorMaterialsActions.removeMaterial(session, index, () -> refreshIfOpen(ref, store));
            }
            return;
        }
        if ("Close".equals(data.action)) {
            close(ref, store);
        }
    }

    private static int parseInt(@Nonnull String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    private static Integer parsePositiveIntOrNull(@Nonnull String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void refreshIfOpen(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getPageManager().getCustomPage() != this) {
            return;
        }
        UICommandBuilder b = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        applyContent(b, events);
        sendUpdate(b, events, false);
    }

    private void close(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("MaterialIndex", Codec.STRING), (d, v) -> d.materialIndex = v, d -> d.materialIndex)
            .add()
            .append(new KeyedCodec<>("Delta", Codec.STRING), (d, v) -> d.delta = v, d -> d.delta)
            .add()
            .append(new KeyedCodec<>("@Count", Codec.STRING), (d, v) -> d.count = v, d -> d.count)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String materialIndex;
        @Nullable
        private String delta;
        @Nullable
        private String count;
    }
}
