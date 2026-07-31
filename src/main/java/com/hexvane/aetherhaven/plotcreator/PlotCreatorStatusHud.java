package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.ui.AetherhavenUiLocalization;
import com.hexvane.aetherhaven.ui.ToolHudHotkeyRows;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import javax.annotation.Nonnull;

/** In-world hints while the plot creator wizard is dismissed but the session is still active. */
public final class PlotCreatorStatusHud extends CustomUIHud {
    private static final String CONTROL_ROWS = "#ControlRows";
    private static final String LANG_PREFIX = "aetherhaven_plot_creator.aetherhaven.plotcreator.";

    public PlotCreatorStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.PLOT_CREATOR_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/PlotCreatorStatusHud.ui");
    }

    public void refresh(@Nonnull PlotCreatorSession session) {
        UICommandBuilder b = new UICommandBuilder();
        AetherhavenUiLocalization.applyPlotCreatorStatusHudTitle(b, selector -> selector);
        PlotCreatorStep step = session.getDraft().getStep();
        String stepKey = "step." + step.name();
        b.set("#StepName.TextSpans", Message.translation(LANG_PREFIX + stepKey + ".title"));
        if (step == PlotCreatorStep.BOUNDS) {
            PlotCreatorDraft draft = session.getDraft();
            String hintKey =
                draft.getBoundsPhase() == PlotCreatorBoundsPhase.FACE_ADJUST
                    ? stepKey + ".hint.faces"
                    : stepKey + ".hint.initial";
            b.set("#StepHelp.TextSpans", Message.translation(LANG_PREFIX + hintKey));
            b.set("#DetailLine.Visible", false);
        } else {
            b.set("#StepHelp.TextSpans", Message.translation(LANG_PREFIX + stepKey + ".hint"));
        }
        if (step == PlotCreatorStep.SUBSTEP) {
            PlotBuildingKindRequirements.SubstepRequirement sub = PlotCreatorService.currentSubstep(session.getDraft());
            if (sub != null) {
                if (sub.type() == PlotCreatorSubstepType.WORK_POI
                    && sub.workResidentKind() != null
                    && !sub.workResidentKind().isBlank()) {
                    b.set(
                        "#DetailLine.TextSpans",
                        Message.translation(
                            LANG_PREFIX
                                + "spot.workRole."
                                + sub.workResidentKind().toLowerCase(java.util.Locale.ROOT)
                        )
                    );
                } else {
                    b.set("#DetailLine.TextSpans", Message.translation(LANG_PREFIX + "substep." + sub.type().name()));
                }
                b.set("#DetailLine.Visible", true);
            } else {
                b.set("#DetailLine.Visible", false);
            }
        } else if (step == PlotCreatorStep.KIND && !session.getDraft().getKinds().isEmpty()) {
            StringBuilder kinds = new StringBuilder();
            for (int i = 0; i < session.getDraft().getKinds().size(); i++) {
                if (i > 0) {
                    kinds.append(", ");
                }
                kinds.append(session.getDraft().getKinds().get(i).name());
            }
            b.set(
                "#DetailLine.TextSpans",
                Message.translation(LANG_PREFIX + "hud.kindSelected").param("kind", kinds.toString())
            );
            b.set("#DetailLine.Visible", true);
        } else if (step == PlotCreatorStep.MATERIALS && PlotCreatorMaterialsHelper.pageCount(session.getDraft()) > 1) {
            int page = PlotCreatorMaterialsHelper.clampPageIndex(session) + 1;
            int pages = PlotCreatorMaterialsHelper.pageCount(session.getDraft());
            b.set(
                "#DetailLine.TextSpans",
                Message.translation(LANG_PREFIX + "materials.pageLabel").param("page", page).param("pages", pages)
            );
            b.set("#DetailLine.Visible", true);
        } else if (step != PlotCreatorStep.BOUNDS) {
            b.set("#DetailLine.Visible", false);
        }
        b.clear(CONTROL_ROWS);
        List<PlotCreatorHudControls.Row> rows = PlotCreatorHudControls.rowsFor(step, session);
        for (int i = 0; i < rows.size(); i++) {
            PlotCreatorHudControls.Row row = rows.get(i);
            String base = CONTROL_ROWS + "[" + i + "]";
            if (row.infoOnly()) {
                b.append(CONTROL_ROWS, "Aetherhaven/PathToolHudInfoRow.ui");
                b.set(
                    base + " #InfoLabel.TextSpans",
                    Message.translation(LANG_PREFIX + row.descriptionLangKey())
                );
            } else if (row.modifierLabel() != null) {
                ToolHudHotkeyRows.appendModifierRow(
                    b,
                    CONTROL_ROWS,
                    i,
                    row.modifierLabel(),
                    row.slot(),
                    LANG_PREFIX + row.descriptionLangKey(),
                    getPlayerRef()
                );
            } else {
                ToolHudHotkeyRows.appendRow(
                    b,
                    CONTROL_ROWS,
                    i,
                    row.slot(),
                    LANG_PREFIX + row.descriptionLangKey(),
                    getPlayerRef()
                );
            }
        }
        this.update(false, b);
    }
}
