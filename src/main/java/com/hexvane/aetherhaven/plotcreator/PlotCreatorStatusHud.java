package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.ui.AetherhavenUiLocalization;
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
        b.set("#StepHelp.TextSpans", Message.translation(LANG_PREFIX + stepKey + ".hint"));
        if (step == PlotCreatorStep.SUBSTEP) {
            PlotBuildingKindRequirements.SubstepRequirement sub = PlotCreatorService.currentSubstep(session.getDraft());
            if (sub != null) {
                b.set("#DetailLine.TextSpans", Message.translation(LANG_PREFIX + "substep." + sub.type().name()));
                b.set("#DetailLine.Visible", true);
            } else {
                b.set("#DetailLine.Visible", false);
            }
        } else if (step == PlotCreatorStep.KIND && session.getDraft().getKind() != null) {
            b.set(
                "#DetailLine.TextSpans",
                Message.translation(LANG_PREFIX + "hud.kindSelected").param("kind", session.getDraft().getKind().name())
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
        } else {
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
            } else {
                b.append(CONTROL_ROWS, "Aetherhaven/PathToolHudControlRow.ui");
                b.set(base + " #KeyLabel.TextSpans", Message.raw(row.keyLabel()));
                b.set(
                    base + " #DescLabel.TextSpans",
                    Message.translation(LANG_PREFIX + row.descriptionLangKey())
                );
            }
        }
        this.update(false, b);
    }
}
