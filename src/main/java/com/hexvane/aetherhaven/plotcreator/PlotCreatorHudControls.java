package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.ui.ToolKeybindSlot;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Step-specific HUD control rows (key slot + lang key for description). */
public final class PlotCreatorHudControls {
    public record Row(@Nonnull ToolKeybindSlot slot, @Nonnull String descriptionLangKey, boolean infoOnly) {}

    private PlotCreatorHudControls() {}

    @Nonnull
    public static List<Row> rowsFor(@Nonnull PlotCreatorStep step, @Nonnull PlotCreatorSession session) {
        List<Row> rows = new ArrayList<>(switch (step) {
            case WELCOME -> List.of(
                row(ToolKeybindSlot.USE, "hud.WELCOME.f"),
                row(ToolKeybindSlot.ABILITY2, "hud.WELCOME.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case BOUNDS -> boundsRows(session);
            case ANCHOR -> List.of();
            case PREFAB_SAVE -> List.of(
                row(ToolKeybindSlot.USE, "hud.PREFAB_SAVE.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case KIND -> List.of(
                row(ToolKeybindSlot.USE, "hud.KIND.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case IDENTITY -> List.of(
                row(ToolKeybindSlot.USE, "hud.IDENTITY.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case TAGS -> List.of(
                row(ToolKeybindSlot.USE, "hud.TAGS.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case VARIANT -> List.of(
                row(ToolKeybindSlot.USE, "hud.VARIANT.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case IMPORTANT_SPOTS -> List.of(
                row(ToolKeybindSlot.USE, "hud.IMPORTANT_SPOTS.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case SUBSTEP -> substepRows(session);
            case MATERIALS -> List.of(
                row(ToolKeybindSlot.USE, "hud.MATERIALS.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r"),
                info("step.MATERIALS.detail")
            );
            case CONFIGURE -> List.of(
                row(ToolKeybindSlot.USE, "hud.CONFIGURE.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r"),
                info("step.CONFIGURE.detail")
            );
            case REVIEW -> List.of(
                row(ToolKeybindSlot.USE, "hud.REVIEW.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case DONE -> List.of();
        });
        return rows;
    }

    @Nonnull
    private static List<Row> boundsRows(@Nonnull PlotCreatorSession session) {
        PlotCreatorDraft draft = session.getDraft();
        List<Row> rows = new ArrayList<>();
        if (draft.getBoundsPhase() == PlotCreatorBoundsPhase.FACE_ADJUST) {
            rows.add(row(ToolKeybindSlot.PRIMARY, "hud.BOUNDS.primaryFace"));
            rows.add(row(ToolKeybindSlot.SECONDARY, "hud.BOUNDS.secondary"));
            rows.add(info("hud.BOUNDS.secondaryShrink"));
            rows.add(row(ToolKeybindSlot.ABILITY1, "hud.BOUNDS.redo"));
        } else {
            rows.add(row(ToolKeybindSlot.PRIMARY, "hud.BOUNDS.primary"));
            rows.add(row(ToolKeybindSlot.ABILITY1, "hud.common.q"));
        }
        rows.add(row(ToolKeybindSlot.ABILITY2, "hud.BOUNDS.confirm"));
        rows.add(row(ToolKeybindSlot.ABILITY3, "hud.common.r"));
        return rows;
    }

    @Nonnull
    private static List<Row> substepRows(@Nonnull PlotCreatorSession session) {
        List<Row> rows = new ArrayList<>();
        rows.add(row(ToolKeybindSlot.PRIMARY, "hud.SUBSTEP.primary"));
        PlotBuildingKindRequirements.SubstepRequirement sub = PlotCreatorService.currentSubstep(session.getDraft());
        if (sub != null && sub.type() == PlotCreatorSubstepType.ADVENTURER_SPAWN) {
            rows.add(row(ToolKeybindSlot.SECONDARY, "hud.SUBSTEP.secondaryAdventurer"));
        } else if (sub != null && sub.type() == PlotCreatorSubstepType.VISITOR_SPAWN) {
            rows.add(row(ToolKeybindSlot.SECONDARY, "hud.SUBSTEP.secondaryVisitor"));
        }
        rows.add(row(ToolKeybindSlot.ABILITY1, "hud.SUBSTEP.q"));
        rows.add(row(ToolKeybindSlot.ABILITY2, "hud.common.e"));
        rows.add(row(ToolKeybindSlot.ABILITY3, "hud.common.r"));
        return rows;
    }

    @Nonnull
    private static Row row(@Nonnull ToolKeybindSlot slot, @Nonnull String langKey) {
        return new Row(slot, langKey, false);
    }

    @Nonnull
    private static Row info(@Nonnull String langKey) {
        return new Row(ToolKeybindSlot.PRIMARY, langKey, true);
    }
}
