package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.ui.ToolKeybindSlot;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Step-specific HUD control rows (key slot + lang key for description). */
public final class PlotCreatorHudControls {
    public record Row(
        @Nonnull ToolKeybindSlot slot,
        @Nonnull String descriptionLangKey,
        boolean infoOnly,
        @Nullable String modifierLabel
    ) {
        public Row(@Nonnull ToolKeybindSlot slot, @Nonnull String descriptionLangKey, boolean infoOnly) {
            this(slot, descriptionLangKey, infoOnly, null);
        }
    }

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
            case IDENTITY, TAGS, CONFIGURE -> List.of(
                row(ToolKeybindSlot.USE, "hud.CONFIGURE.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r"),
                info("step.CONFIGURE.detail")
            );
            case VARIANT -> List.of(
                row(ToolKeybindSlot.USE, "hud.VARIANT.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case FESTIVAL -> List.of(
                row(ToolKeybindSlot.USE, "hud.FESTIVAL.f"),
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
            case WALL_PIECES -> wallPieceRows(session);
            case MATERIALS -> List.of(
                row(ToolKeybindSlot.USE, "hud.MATERIALS.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r"),
                info("step.MATERIALS.detail")
            );
            case REVIEW -> List.of(
                row(ToolKeybindSlot.USE, "hud.REVIEW.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r")
            );
            case DONE -> List.of();
        });
        if (step != PlotCreatorStep.DONE) {
            rows.add(row(ToolKeybindSlot.PICK, "hud.STEP_JUMP.f"));
        }
        return rows;
    }

    @Nonnull
    private static List<Row> boundsRows(@Nonnull PlotCreatorSession session) {
        List<Row> rows = new ArrayList<>();
        rows.add(row(ToolKeybindSlot.PRIMARY, "hud.BOUNDS.primary"));
        rows.add(row(ToolKeybindSlot.SECONDARY, "hud.BOUNDS.secondary"));
        rows.add(row(ToolKeybindSlot.ABILITY2, "hud.BOUNDS.confirm"));
        rows.add(row(ToolKeybindSlot.ABILITY1, "hud.common.q"));
        rows.add(row(ToolKeybindSlot.ABILITY3, "hud.common.r"));
        return rows;
    }

    @Nonnull
    private static List<Row> wallPieceRows(@Nonnull PlotCreatorSession session) {
        PlotCreatorDraft draft = session.getDraft();
        if (PlotCreatorWallPieceAuthoring.isBoundsSubstep(draft)) {
            List<Row> rows = boundsRows(session);
            rows.add(info("step.WALL_PIECES.boundsDetail"));
            return rows;
        }
        if (PlotCreatorWallPieceAuthoring.isMaterialsSubstep(draft)) {
            return List.of(
                row(ToolKeybindSlot.USE, "hud.MATERIALS.f"),
                row(ToolKeybindSlot.ABILITY1, "hud.common.q"),
                row(ToolKeybindSlot.ABILITY2, "hud.common.e"),
                row(ToolKeybindSlot.ABILITY3, "hud.common.r"),
                info("step.MATERIALS.detail")
            );
        }
        List<Row> rows = new ArrayList<>();
        rows.add(row(ToolKeybindSlot.PRIMARY, "hud.WALL_PIECES.primary"));
        rows.add(row(ToolKeybindSlot.SECONDARY, "hud.WALL_PIECES.secondary"));
        rows.add(row(ToolKeybindSlot.ABILITY1, "hud.WALL_PIECES.q"));
        rows.add(row(ToolKeybindSlot.ABILITY2, "hud.common.e"));
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
        } else if (sub != null
            && (sub.type() == PlotCreatorSubstepType.FESTIVAL_NPC
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_CENTERPIECE
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_RACE_LANE
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_BALLOON_SPAWN
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_WHACK_SPAWN
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_WHEEL
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_TREE_CLIMB_START
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_TREE_CLIMB_FINISH
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_MAZE_START
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_MAZE_ORB_SPAWN
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_MARKET_STAND
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_MARKET_DISPLAY
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_SNOWBALL_PILE
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_SNOWBALL_TEAM_A
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_SNOWBALL_TEAM_B
                || sub.type() == PlotCreatorSubstepType.FESTIVAL_SNOWBALL_OUT)) {
            rows.add(row(ToolKeybindSlot.SECONDARY, "hud.SUBSTEP.secondaryFestivalSpot"));
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
    private static Row modifierRow(
        @Nonnull String modifierLabel,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull String langKey
    ) {
        return new Row(slot, langKey, false, modifierLabel);
    }

    @Nonnull
    private static Row info(@Nonnull String langKey) {
        return new Row(ToolKeybindSlot.PRIMARY, langKey, true);
    }
}
