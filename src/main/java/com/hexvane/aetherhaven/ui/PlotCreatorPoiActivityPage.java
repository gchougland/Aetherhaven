package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.plotcreator.PlotCreatorInteractions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorPendingPoiPlacement;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSubstepHandler;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorWorkActivityOptions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorWorkActivityTags;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Work/fun POI animation picker opened after clicking a block in the plot creator substep flow. */
public final class PlotCreatorPoiActivityPage extends AetherhavenInteractiveCustomUIPage<PlotCreatorPoiActivityPage.PageData> {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator.poiActivity";

    @Nonnull
    private final PlotCreatorSession session;
    private boolean templateAppended;
    @Nonnull
    private String selectedActivityId = "read";

    public PlotCreatorPoiActivityPage(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.session = session;
        this.selectedActivityId = defaultActivityId(session);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PlotCreatorPoiActivityPage.ui");
            templateAppended = true;
            wireEvents(eventBuilder);
        }
        applyLabels(commandBuilder);
        applyActivityDropdown(commandBuilder);
    }

    private void wireEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ActivityDropdown",
            EventData.of("@Activity", "#ActivityDropdown.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ConfirmButton",
            EventData.of("Action", "Confirm"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            EventData.of("Action", "Cancel"),
            false
        );
    }

    private void applyLabels(@Nonnull UICommandBuilder b) {
        b.set("#PoiActivityTitle.TextSpans", Message.translation(MSG + ".title"));
        b.set("#IntroHint.TextSpans", Message.translation(MSG + ".hint"));
        b.set("#ActivityLabel.TextSpans", Message.translation(MSG + ".activityLabel"));
        b.set("#ConfirmButton.TextSpans", Message.translation(MSG + ".confirm"));
        b.set("#CloseButton.TextSpans", Message.translation(MSG + ".close"));
    }

    private void applyActivityDropdown(@Nonnull UICommandBuilder b) {
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        for (String id : PlotCreatorWorkActivityOptions.allSelectable()) {
            entries.add(
                new DropdownEntryInfo(LocalizableString.fromMessageId(PlotCreatorWorkActivityOptions.langKeyFor(id)), id)
            );
        }
        b.set("#ActivityDropdown.Entries", entries);
        b.set("#ActivityDropdown.Value", selectedActivityId);
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (data.activity != null && !data.activity.isBlank()) {
            selectedActivityId = data.activity.trim();
        }
        if (data.action != null) {
            if ("Confirm".equals(data.action)) {
                confirm(ref, store);
                return;
            }
            if ("Cancel".equals(data.action)) {
                cancel(ref, store);
                return;
            }
        }
        UICommandBuilder b = new UICommandBuilder();
        applyActivityDropdown(b);
        sendUpdate(b, null, false);
    }

    private void confirm(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (session.getPendingPoiPlacement() == null) {
            close();
            return;
        }
        PlotCreatorSubstepHandler.finalizePendingPoi(session, selectedActivityId, playerRef, ref, store);
        close();
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
    }

    private void cancel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlotCreatorSubstepHandler.cancelPendingPoi(session);
        close();
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (session.getPendingPoiPlacement() != null) {
            PlotCreatorSubstepHandler.cancelPendingPoi(session);
        }
        super.onDismiss(ref, store);
    }

    @Nonnull
    private static String defaultActivityId(@Nonnull PlotCreatorSession session) {
        PlotCreatorPendingPoiPlacement pending = session.getPendingPoiPlacement();
        if (pending == null) {
            return PlotCreatorWorkActivityOptions.allSelectable().get(0);
        }
        String resolved =
            PlotCreatorWorkActivityTags.resolveActivityId(
                pending.req().type(),
                pending.req().workResidentKind(),
                List.of()
            );
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return PlotCreatorWorkActivityOptions.allSelectable().get(0);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("@Activity", Codec.STRING), (d, v) -> d.activity = v, d -> d.activity)
                .add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String activity;
    }
}
