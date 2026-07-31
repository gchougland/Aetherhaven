package com.hexvane.aetherhaven.plotcreator;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Rows for the plot creator step jump menu. */
public final class PlotCreatorStepJumpModel {
    private static final String LANG = "aetherhaven_plot_creator.aetherhaven.plotcreator.";

    public record JumpNode(
        @Nonnull PlotCreatorStep step,
        @Nonnull String shortLangKey,
        boolean reachable,
        boolean current
    ) {}

    private PlotCreatorStepJumpModel() {}

    @Nonnull
    public static List<JumpNode> nodes(@Nonnull PlotCreatorDraft draft) {
        List<PlotCreatorStep> order = macroSteps(draft);
        int maxReached = draft.getMaxReachedStepIndex();
        PlotCreatorStep current = draft.getStep();
        List<JumpNode> out = new ArrayList<>(order.size());
        for (int i = 0; i < order.size(); i++) {
            PlotCreatorStep step = order.get(i);
            out.add(
                new JumpNode(
                    step,
                    LANG + "step." + step.name() + ".short",
                    i <= maxReached,
                    step == current
                )
            );
        }
        return out;
    }

    @Nonnull
    private static List<PlotCreatorStep> macroSteps(@Nonnull PlotCreatorDraft draft) {
        List<PlotCreatorStep> out = new ArrayList<>();
        for (PlotCreatorStep step : PlotCreatorService.stepOrder(draft)) {
            if (step != PlotCreatorStep.DONE) {
                out.add(step);
            }
        }
        return out;
    }
}
