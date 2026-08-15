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

    /** Macro wizard steps only (SUBSTEP stays a single "Place spots" node; WALL_PIECES becomes one node per piece). */
    @Nonnull
    public static List<ProgressNode> nodes(@Nonnull PlotCreatorDraft draft) {
        List<PlotCreatorStep> steps = macroSteps(draft);
        int currentIndex = macroCurrentIndex(draft, steps);
        List<ProgressNode> out = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            PlotCreatorStep step = steps.get(i);
            if (step == PlotCreatorStep.WALL_PIECES) {
                addWallPieceNodes(out, draft, i, currentIndex);
                continue;
            }
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

    private static void addWallPieceNodes(
        @Nonnull List<ProgressNode> out,
        @Nonnull PlotCreatorDraft draft,
        int stepIndex,
        int currentStepIndex
    ) {
        draft.ensureWallPieces();
        List<PlotCreatorWallPieceDraft> pieces = draft.getWallPieces();
        int currentPiece = draft.getWallPieceIndex();
        for (int p = 0; p < pieces.size(); p++) {
            boolean onThisStep = stepIndex == currentStepIndex;
            boolean completed = stepIndex < currentStepIndex || (onThisStep && p < currentPiece);
            boolean current = onThisStep && p == currentPiece;
            out.add(
                new ProgressNode(
                    LANG + "wallPiece." + PlotCreatorWallPieceAuthoring.roleLangSuffix(pieces.get(p).getRole()),
                    completed,
                    current
                )
            );
        }
    }

    /** Spot-placement substeps, or the build box and connection points of the current wall piece. */
    @Nonnull
    public static List<ProgressNode> substepNodes(@Nonnull PlotCreatorDraft draft) {
        if (draft.getStep() == PlotCreatorStep.WALL_PIECES) {
            return wallPieceSubstepNodes(draft);
        }
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

    @Nonnull
    private static List<ProgressNode> wallPieceSubstepNodes(@Nonnull PlotCreatorDraft draft) {
        draft.ensureWallPieces();
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        if (piece == null) {
            return List.of();
        }
        int count = PlotCreatorWallPieceAuthoring.substepCount(piece.getRole());
        int current = Math.min(Math.max(0, draft.getWallPieceSubstepIndex()), count - 1);
        List<ProgressNode> out = new ArrayList<>(count);
        out.add(new ProgressNode(LANG + "wallSubstep.bounds", current > 0, current == 0));
        for (int i = 1; i < count - 1; i++) {
            com.hexvane.aetherhaven.wall.WallCardinal face = piece.getRole().connectionFace(i - 1);
            String key =
                face == null
                    ? LANG + "wallSubstep.join"
                    : LANG + "wallSubstep.join." + face.name().toLowerCase(java.util.Locale.ROOT);
            out.add(new ProgressNode(key, i < current, i == current));
        }
        out.add(new ProgressNode(LANG + "wallSubstep.materials", false, current == count - 1));
        return out;
    }

    /** Signature of progress + checklist state for HUD refresh detection. */
    public static long guideSignature(@Nonnull PlotCreatorDraft draft) {
        long h = 17L;
        h = 31 * h + draft.getStep().ordinal();
        h = 31 * h + draft.getSubstepIndex();
        h = 31 * h + draft.getWallPieceIndex();
        h = 31 * h + draft.getWallPieceSubstepIndex();
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            h = 31 * h + (piece.hasBounds() ? piece.boundsMin().hashCode() + piece.boundsMax().hashCode() : 0);
            h = 31 * h + piece.getMaterials().size();
            for (PlotCreatorWallPieceDraft.Connection c : piece.getConnections()) {
                h = 31 * h + c.face().ordinal();
                h = 31 * h + c.worldCell().hashCode();
            }
        }
        h = 31 * h + (draft.isImportantSpotsConfirmed() ? 1 : 0);
        h = 31 * h + draft.getKinds().hashCode();
        h = 31 * h + draft.getSelectedSpots().hashCode();
        h = 31 * h + (draft.getCornerFirst() != null ? 1 : 0);
        h = 31 * h + (draft.getCornerSecond() != null ? 1 : 0);
        h = 31 * h + draft.getBoundsPhase().ordinal();
        h = 31 * h + (draft.getHoveredBoundsFace() != null ? draft.getHoveredBoundsFace().ordinal() : -1);
        h = 31 * h + (draft.getBoundsDragStart() != null ? draft.getBoundsDragStart().hashCode() : 0);
        h = 31 * h + (draft.getBoundsDragEnd() != null ? draft.getBoundsDragEnd().hashCode() : 0);
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
        if (req.type() == PlotCreatorSubstepType.FESTIVAL_NPC
            && req.workResidentKind() != null
            && !req.workResidentKind().isBlank()) {
            return LANG
                + "spot.festivalNpc."
                + PlotCreatorFestivalNpcRoles.labelLangSuffix(req.workResidentKind());
        }
        return LANG + "spot." + req.type().name();
    }

    private static boolean nonBlank(@Nullable String s) {
        return s != null && !s.isBlank();
    }
}
