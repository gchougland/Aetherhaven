package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Progress-bar nodes for the plot creator session (main steps + optional SUBSTEP sub-bar). */
public final class PlotCreatorProgressModel {
    private static final String LANG = "aetherhaven_plot_creator.aetherhaven.plotcreator.";

    public record ProgressNode(
        @Nonnull String shortLangKey,
        boolean completed,
        boolean current
    ) {}

    private PlotCreatorProgressModel() {}

    /** Macro wizard steps only (SUBSTEP stays a single "Place spots" node). */
    @Nonnull
    public static List<ProgressNode> nodes(@Nonnull PlotCreatorDraft draft) {
        List<PlotCreatorStep> steps = macroSteps(draft);
        int currentIndex = macroCurrentIndex(draft, steps);
        List<ProgressNode> out = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            PlotCreatorStep step = steps.get(i);
            out.add(
                new ProgressNode(
                    LANG + "step." + step.name() + ".short",
                    i < currentIndex,
                    i == currentIndex
                )
            );
        }
        return out;
    }

    /** Spot-placement substeps; empty when not on SUBSTEP. */
    @Nonnull
    public static List<ProgressNode> substepNodes(@Nonnull PlotCreatorDraft draft) {
        if (draft.getStep() != PlotCreatorStep.SUBSTEP) {
            return List.of();
        }
        List<PlotBuildingKindRequirements.SubstepRequirement> subs =
            PlotBuildingKindRequirements.forDraft(draft, AetherhavenPlugin.get());
        if (subs.isEmpty()) {
            return List.of();
        }
        int current = Math.min(Math.max(0, draft.getSubstepIndex()), subs.size() - 1);
        List<ProgressNode> out = new ArrayList<>(subs.size());
        for (int i = 0; i < subs.size(); i++) {
            out.add(new ProgressNode(spotLangKey(subs.get(i)), i < current, i == current));
        }
        return out;
    }

    /** Signature of progress + checklist state for HUD refresh detection. */
    public static long guideSignature(@Nonnull PlotCreatorDraft draft) {
        long h = 17L;
        h = 31 * h + draft.getStep().ordinal();
        h = 31 * h + draft.getSubstepIndex();
        h = 31 * h + (draft.isImportantSpotsConfirmed() ? 1 : 0);
        h = 31 * h + draft.getKinds().hashCode();
        h = 31 * h + draft.getSelectedSpots().hashCode();
        h = 31 * h + (draft.getCornerFirst() != null ? 1 : 0);
        h = 31 * h + (draft.getCornerSecond() != null ? 1 : 0);
        h = 31 * h + (draft.getPlotAnchor() != null ? 1 : 0);
        h = 31 * h + (nonBlank(draft.getDisplayName()) ? 1 : 0);
        h = 31 * h + (nonBlank(draft.getConstructionId()) ? 1 : 0);
        h = 31 * h + (nonBlank(draft.getPrefabPath()) ? 1 : 0);
        h = 31 * h + draft.getMaterials().size();
        for (PlotBuildingKindRequirements.SubstepRequirement req :
            PlotBuildingKindRequirements.forDraft(draft, AetherhavenPlugin.get())) {
            h = 31 * h + req.type().ordinal();
            h = 31 * h + req.minCount();
            String role = req.workResidentKind();
            h = 31 * h + (role == null ? 0 : role.hashCode());
            h = 31 * h + PlotCreatorValidator.countForRequirement(draft, req);
        }
        return h;
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

    private static int macroCurrentIndex(@Nonnull PlotCreatorDraft draft, @Nonnull List<PlotCreatorStep> steps) {
        PlotCreatorStep step = draft.getStep();
        if (step == PlotCreatorStep.DONE) {
            return steps.size();
        }
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i) == step) {
                return i;
            }
        }
        return 0;
    }

    @Nonnull
    private static String spotLangKey(@Nonnull PlotBuildingKindRequirements.SubstepRequirement req) {
        if (req.type() == PlotCreatorSubstepType.WORK_POI
            && req.workResidentKind() != null
            && !req.workResidentKind().isBlank()) {
            return LANG + "spot.workRole." + req.workResidentKind().toLowerCase(Locale.ROOT);
        }
        return LANG + "spot." + req.type().name();
    }

    private static boolean nonBlank(@Nullable String s) {
        return s != null && !s.isBlank();
    }
}
