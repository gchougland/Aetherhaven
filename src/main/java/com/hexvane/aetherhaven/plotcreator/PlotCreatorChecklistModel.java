package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live requirements checklist rows for the plot creator session. */
public final class PlotCreatorChecklistModel {
    private static final String LANG = "aetherhaven_plot_creator.aetherhaven.plotcreator.";

    public record ChecklistItem(
        @Nonnull String labelLangKey,
        boolean completed,
        boolean optional,
        @Nullable String countHint
    ) {}

    private PlotCreatorChecklistModel() {}

    @Nonnull
    public static List<ChecklistItem> items(@Nonnull PlotCreatorDraft draft) {
        List<ChecklistItem> out = new ArrayList<>();
        if (draft.isWallMode()) {
            return wallItems(draft);
        }
        out.add(
            new ChecklistItem(
                LANG + "checklist.corners",
                draft.getCornerFirst() != null && draft.getCornerSecond() != null && draft.getPlotAnchor() != null,
                false,
                null
            )
        );
        out.add(new ChecklistItem(LANG + "checklist.kind", !draft.getKinds().isEmpty(), false, null));
        out.add(
            new ChecklistItem(
                LANG + "checklist.identity",
                nonBlank(draft.getDisplayName()) && nonBlank(draft.getConstructionId()),
                false,
                null
            )
        );
        out.add(
            new ChecklistItem(LANG + "checklist.prefab", nonBlank(draft.getPrefabPath()), false, null)
        );
        if (!draft.isFestivalMode()) {
            out.add(
                new ChecklistItem(
                    LANG + "checklist.materials",
                    !draft.getMaterials().isEmpty(),
                    false,
                    null
                )
            );
        }

        if (draft.isDecorationOnly()) {
            return out;
        }

        for (PlotBuildingKindRequirements.SubstepRequirement req :
            PlotBuildingKindRequirements.forDraft(draft, AetherhavenPlugin.get())) {
            int have = PlotCreatorValidator.countForRequirement(draft, req);
            int min = req.minCount();
            boolean optional = min <= 0;
            boolean exact = isExactCountRequirement(draft, req);
            boolean completed = optional ? have >= 1 : exact ? have == min : have >= min;
            String hint = optional ? null : have + "/" + min;
            out.add(new ChecklistItem(spotLangKey(req), completed, optional, hint));
        }
        return out;
    }

    /** A wall style is checked off one piece at a time: its box, then the connection points on it. */
    @Nonnull
    private static List<ChecklistItem> wallItems(@Nonnull PlotCreatorDraft draft) {
        draft.ensureWallPieces();
        List<ChecklistItem> out = new ArrayList<>();
        out.add(
            new ChecklistItem(
                LANG + "checklist.identity",
                nonBlank(draft.getDisplayName()) && nonBlank(draft.getConstructionId()),
                false,
                null
            )
        );
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            int need = piece.getRole().connectionCount();
            int have = Math.min(piece.getConnections().size(), need);
            out.add(
                new ChecklistItem(
                    LANG + "wallPiece." + PlotCreatorWallPieceAuthoring.roleLangSuffix(piece.getRole()),
                    piece.isComplete(),
                    false,
                    piece.hasBounds() ? have + "/" + need : null
                )
            );
        }
        return out;
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

    private static boolean isExactCountRequirement(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull PlotBuildingKindRequirements.SubstepRequirement req
    ) {
        if (!com.hexvane.aetherhaven.festival.market.MarketIds.MECHANIC_ID.equals(draft.getFestivalMechanicId())) {
            return false;
        }
        return req.type() == PlotCreatorSubstepType.FESTIVAL_MARKET_STAND
            || req.type() == PlotCreatorSubstepType.FESTIVAL_MARKET_DISPLAY;
    }

    private static boolean nonBlank(@Nullable String s) {
        return s != null && !s.isBlank();
    }
}
