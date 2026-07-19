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
        out.add(
            new ChecklistItem(
                LANG + "checklist.corners",
                draft.getCornerFirst() != null && draft.getCornerSecond() != null,
                false,
                null
            )
        );
        out.add(
            new ChecklistItem(LANG + "checklist.anchor", draft.getPlotAnchor() != null, false, null)
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
        out.add(
            new ChecklistItem(
                LANG + "checklist.materials",
                !draft.getMaterials().isEmpty(),
                false,
                null
            )
        );

        if (draft.isDecorationOnly()) {
            return out;
        }

        for (PlotBuildingKindRequirements.SubstepRequirement req :
            PlotBuildingKindRequirements.forDraft(draft, AetherhavenPlugin.get())) {
            int have = PlotCreatorValidator.countForRequirement(draft, req);
            int min = req.minCount();
            boolean optional = min <= 0;
            boolean completed = optional ? have >= 1 : have >= min;
            String hint = optional ? null : have + "/" + min;
            out.add(new ChecklistItem(spotLangKey(req), completed, optional, hint));
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
        return LANG + "spot." + req.type().name();
    }

    private static boolean nonBlank(@Nullable String s) {
        return s != null && !s.isBlank();
    }
}
