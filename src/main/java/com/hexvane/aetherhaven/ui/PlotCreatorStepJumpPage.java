package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.plotcreator.PlotCreatorInteractions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSelectionBoundsService;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorService;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorStep;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorStepJumpModel;
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

/** Quick jump menu for plot creator wizard steps (middle mouse / Pick). */
public final class PlotCreatorStepJumpPage extends AetherhavenInteractiveCustomUIPage<PlotCreatorStepJumpPage.PageData> {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";
    private static final String STEP_ROW = "#StepJumpRow";
    private static final String TEX_EMPTY = "UI/Custom/NodeEmpty.png";
    private static final String TEX_FULL = "UI/Custom/NodeFull.png";

    @Nonnull
    private final PlotCreatorSession session;
    private boolean templateAppended;

    public PlotCreatorStepJumpPage(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
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
            commandBuilder.append("Aetherhaven/PlotCreatorStepJumpPage.ui");
            templateAppended = true;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CancelButton",
                EventData.of("Action", "Cancel"),
                false
            );
        }
        commandBuilder.set("#StepJumpTitleText.TextSpans", Message.translation(MSG + ".stepJump.title"));
        commandBuilder.set("#StepHint.TextSpans", Message.translation(MSG + ".stepJump.hint"));
        commandBuilder.set("#CancelButton.TextSpans", Message.translation(MSG + ".button.close"));
        applyStepRow(commandBuilder, eventBuilder);
    }

    private void applyStepRow(@Nonnull UICommandBuilder b, @Nonnull UIEventBuilder eventBuilder) {
        List<PlotCreatorStepJumpModel.JumpNode> nodes = PlotCreatorStepJumpModel.nodes(session.getDraft());
        b.clear(STEP_ROW);
        for (int i = 0; i < nodes.size(); i++) {
            PlotCreatorStepJumpModel.JumpNode node = nodes.get(i);
            b.append(STEP_ROW, "Aetherhaven/PlotCreatorStepJumpNode.ui");
            String row = STEP_ROW + "[" + i + "]";
            boolean current = node.current();
            boolean done = node.reachable() && !current;
            String tex = (done || current) ? TEX_FULL : TEX_EMPTY;
            b.set(row + " #NodeIconNormal.Visible", !current);
            b.set(row + " #NodeIconCurrent.Visible", current);
            b.set(row + " #NodeIconNormal.AssetPath", tex);
            b.set(row + " #NodeIconCurrent.AssetPath", tex);
            b.set(row + " #LockedTint.Visible", !node.reachable());
            b.set(row + " #StepLabel.TextSpans", Message.translation(node.shortLangKey()));
            b.set(row + " #StepLabel.Style.FontSize", current ? 13 : 11);
            b.set(row + " #StepLabel.Style.RenderBold", current);
            b.set(
                row + " #StepLabel.Style.TextColor",
                current ? "#f4e8c8" : (done ? "#b8e0c0" : "#6a7280")
            );
            b.set(row + " #StepButton.Disabled", !node.reachable());
            if (node.reachable()) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #StepButton",
                    EventData.of("Action", "Jump").append("Step", node.step().name()),
                    false
                );
            }
        }
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if ("Cancel".equals(data.action)) {
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if (!"Jump".equals(data.action) || data.step == null || data.step.isBlank()) {
            return;
        }
        PlotCreatorStep target;
        try {
            target = PlotCreatorStep.valueOf(data.step.trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!PlotCreatorService.jumpToStep(session, target, ref, store)) {
            return;
        }
        PlotCreatorSelectionBoundsService.syncForSession(session, playerRef, ref, store);
        // Let onStepEntered replace this page when the target opens a wizard panel.
        if (!PlotCreatorService.stepAutoOpensPanel(target)) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("Step", Codec.STRING), (d, v) -> d.step = v, d -> d.step)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String step;
    }
}
