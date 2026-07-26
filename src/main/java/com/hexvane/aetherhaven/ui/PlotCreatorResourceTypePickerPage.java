package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.construction.ResourceTypeCatalog;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorMaterialsActions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
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

/** Grid picker for adding resource types to plot creator build costs. */
public final class PlotCreatorResourceTypePickerPage
    extends AetherhavenInteractiveCustomUIPage<PlotCreatorResourceTypePickerPage.PageData> {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";
    private static final String ROWS = "#PickerRows";
    private static final int GRID_COLS = 6;

    @Nonnull
    private final PlotCreatorSession session;
    private boolean templateAppended;

    public PlotCreatorResourceTypePickerPage(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
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
            commandBuilder.append("Aetherhaven/PlotCreatorResourceTypePickerPage.ui");
            templateAppended = true;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "Close"),
                false
            );
        }

        commandBuilder.set("#PickerTitleText.TextSpans", Message.translation(MSG + ".resourceTypePicker.title"));
        commandBuilder.set("#PickerHint.TextSpans", Message.translation(MSG + ".resourceTypePicker.hint"));
        commandBuilder.set("#CloseButton.TextSpans", Message.translation(MSG + ".button.close"));

        List<ResourceTypeCatalog.Entry> entries = ResourceTypeCatalog.listForPicker();
        commandBuilder.clear(ROWS);
        int total = entries.size();
        int numRows = (total + GRID_COLS - 1) / GRID_COLS;
        for (int r = 0; r < numRows; r++) {
            commandBuilder.append(ROWS, "Aetherhaven/PlotCreatorResourceTypePickerRow.ui");
            String rowBase = ROWS + "[" + r + "]";
            for (int c = 0; c < GRID_COLS; c++) {
                int idx = r * GRID_COLS + c;
                if (idx >= total) {
                    break;
                }
                ResourceTypeCatalog.Entry entry = entries.get(idx);
                commandBuilder.append(rowBase + " #Strip", "Aetherhaven/PlotCreatorResourceTypePickerCell.ui");
                String cell = rowBase + " #Strip[" + c + "]";
                if (entry.iconPath() != null && !entry.iconPath().isBlank()) {
                    commandBuilder.set(cell + " #Select #IconFrame #Icon.AssetPath", entry.iconPath());
                }
                commandBuilder.set(cell + " #Select #Name.TextSpans", resourceTypeNameMessage(entry));
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    cell + " #Select",
                    EventData.of("Action", "SelectResourceType").append("ResourceTypeId", entry.id()),
                    false
                );
            }
        }
    }

    @Nonnull
    private static Message resourceTypeNameMessage(@Nonnull ResourceTypeCatalog.Entry entry) {
        return Message.translation("server.resourceType." + entry.id() + ".name");
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if ("Close".equals(data.action)) {
            openMaterials(ref, store);
            return;
        }
        if ("SelectResourceType".equals(data.action) && data.resourceTypeId != null && !data.resourceTypeId.isBlank()) {
            PlotCreatorMaterialsActions.addResourceTypeFromPicker(
                session,
                playerRef,
                ref,
                store,
                data.resourceTypeId.trim()
            );
        }
    }

    private void openMaterials(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PlotCreatorMaterialsPage(playerRef, session));
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(
                new KeyedCodec<>("ResourceTypeId", Codec.STRING),
                (d, v) -> d.resourceTypeId = v,
                d -> d.resourceTypeId
            )
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String resourceTypeId;
    }
}
