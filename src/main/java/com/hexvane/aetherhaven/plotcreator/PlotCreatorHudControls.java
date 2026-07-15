package com.hexvane.aetherhaven.plotcreator;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Step-specific HUD control rows (key label + lang key for description). */
public final class PlotCreatorHudControls {
    public record Row(@Nonnull String keyLabel, @Nonnull String descriptionLangKey, boolean infoOnly) {}

    private PlotCreatorHudControls() {}

    @Nonnull
    public static List<Row> rowsFor(@Nonnull PlotCreatorStep step, @Nonnull PlotCreatorSession session) {
        List<Row> rows = new ArrayList<>(switch (step) {
            case WELCOME -> List.of(
                row("F", "hud.WELCOME.f"),
                row("E", "hud.WELCOME.e"),
                row("R", "hud.common.r")
            );
            case CORNER_FIRST -> List.of(
                row("Primary", "hud.CORNER_FIRST.primary"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r")
            );
            case CORNER_SECOND -> List.of(
                row("Primary", "hud.CORNER_SECOND.primary"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r")
            );
            case ANCHOR -> List.of(
                row("Primary", "hud.ANCHOR.primary"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r")
            );
            case PREFAB_SAVE -> List.of(
                row("F", "hud.PREFAB_SAVE.f"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r")
            );
            case KIND -> List.of(
                row("F", "hud.KIND.f"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r")
            );
            case IDENTITY -> List.of(
                row("F", "hud.IDENTITY.f"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r")
            );
            case TAGS -> List.of(
                row("F", "hud.TAGS.f"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r")
            );
            case VARIANT -> List.of(
                row("F", "hud.VARIANT.f"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r")
            );
            case IMPORTANT_SPOTS -> List.of(
                row("F", "hud.IMPORTANT_SPOTS.f"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r")
            );
            case SUBSTEP -> substepRows(session);
            case MATERIALS -> List.of(
                row("F", "hud.MATERIALS.f"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r"),
                info("step.MATERIALS.detail")
            );
            case CONFIGURE -> List.of(
                row("F", "hud.CONFIGURE.f"),
                row("Q", "hud.common.q"),
                row("E", "hud.common.e"),
                row("R", "hud.common.r"),
                info("step.CONFIGURE.detail")
            );
            case REVIEW -> List.of(
                row("F", "hud.REVIEW.f"),
                row("Q", "hud.common.q"),
                row("R", "hud.common.r")
            );
            case DONE -> List.of();
        });
        return rows;
    }

    @Nonnull
    private static List<Row> substepRows(@Nonnull PlotCreatorSession session) {
        List<Row> rows = new ArrayList<>();
        rows.add(row("Primary", "hud.SUBSTEP.primary"));
        PlotBuildingKindRequirements.SubstepRequirement sub = PlotCreatorService.currentSubstep(session.getDraft());
        if (sub != null && sub.type() == PlotCreatorSubstepType.ADVENTURER_SPAWN) {
            rows.add(row("Secondary", "hud.SUBSTEP.secondaryAdventurer"));
        } else if (sub != null && sub.type() == PlotCreatorSubstepType.VISITOR_SPAWN) {
            rows.add(row("Secondary", "hud.SUBSTEP.secondaryVisitor"));
        }
        rows.add(row("Q", "hud.SUBSTEP.q"));
        rows.add(row("E", "hud.common.e"));
        rows.add(row("R", "hud.common.r"));
        return rows;
    }

    @Nonnull
    private static Row row(@Nonnull String key, @Nonnull String langKey) {
        return new Row(key, langKey, false);
    }

    @Nonnull
    private static Row info(@Nonnull String langKey) {
        return new Row("", langKey, true);
    }
}
