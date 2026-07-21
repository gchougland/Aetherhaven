package com.hexvane.aetherhaven.pathtool;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Mode-specific HUD control rows (key label + lang key for description). */
public final class PathToolHudControls {
    public record Row(@Nonnull String keyLabel, @Nonnull String descriptionLangKey, boolean infoOnly) {}

    private PathToolHudControls() {}

    @Nonnull
    public static List<Row> rowsFor(@Nonnull PathToolGizmoMode mode, boolean styleEditingActive) {
        return rowsFor(mode, styleEditingActive, false);
    }

    @Nonnull
    public static List<Row> rowsFor(
        @Nonnull PathToolGizmoMode mode,
        boolean styleEditingActive,
        boolean replaceFilterEditingActive
    ) {
        if (mode == PathToolGizmoMode.StyleDesigner && styleEditingActive) {
            return List.of(
                row("F", "aetherhaven.pathTool.hud.styleDesigner.fContinue"),
                row("E", "aetherhaven.pathTool.hud.styleDesigner.eSave"),
                row("Q", "aetherhaven.pathTool.hud.styleDesigner.q"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoColumns"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoRows"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoWeight")
            );
        }
        if (mode == PathToolGizmoMode.ReplaceFilter && replaceFilterEditingActive) {
            return List.of(
                row("F", "aetherhaven.pathTool.hud.replaceFilter.fContinue"),
                row("E", "aetherhaven.pathTool.hud.replaceFilter.eSave"),
                row("Q", "aetherhaven.pathTool.hud.replaceFilter.q"),
                info("aetherhaven.pathTool.hud.replaceFilter.info")
            );
        }
        return rowsFor(mode);
    }

    @Nonnull
    public static List<Row> rowsFor(@Nonnull PathToolGizmoMode mode) {
        return switch (mode) {
            case Translate -> List.of(
                row("Primary", "aetherhaven.pathTool.hud.move.primary"),
                row("Secondary", "aetherhaven.pathTool.hud.move.secondary"),
                row("Q", "aetherhaven.pathTool.hud.move.q"),
                row("E", "aetherhaven.pathTool.hud.move.e"),
                row("R", "aetherhaven.pathTool.hud.move.r")
            );
            case Rotate -> List.of(
                row("Primary", "aetherhaven.pathTool.hud.rotate.primary"),
                row("Secondary", "aetherhaven.pathTool.hud.rotate.secondary"),
                row("F", "aetherhaven.pathTool.hud.rotate.f"),
                row("Q", "aetherhaven.pathTool.hud.rotate.q"),
                row("E", "aetherhaven.pathTool.hud.rotate.e"),
                row("R", "aetherhaven.pathTool.hud.rotate.r")
            );
            case Commit -> List.of(
                row("Primary", "aetherhaven.pathTool.hud.place.primary"),
                row("Secondary", "aetherhaven.pathTool.hud.place.secondary"),
                row("F", "aetherhaven.pathTool.hud.place.f"),
                row("Q", "aetherhaven.pathTool.hud.place.q"),
                row("E", "aetherhaven.pathTool.hud.place.e"),
                row("R", "aetherhaven.pathTool.hud.place.r")
            );
            case Remove -> List.of(
                row("Primary", "aetherhaven.pathTool.hud.remove.primary"),
                row("F", "aetherhaven.pathTool.hud.remove.f"),
                row("Q", "aetherhaven.pathTool.hud.remove.q")
            );
            case StyleDesigner -> List.of(
                row("F", "aetherhaven.pathTool.hud.styleDesigner.f"),
                row("E", "aetherhaven.pathTool.hud.styleDesigner.e"),
                row("Q", "aetherhaven.pathTool.hud.styleDesigner.q"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoColumns"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoRows"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoWeight")
            );
            case ReplaceFilter -> List.of(
                row("F", "aetherhaven.pathTool.hud.replaceFilter.f"),
                row("E", "aetherhaven.pathTool.hud.replaceFilter.e"),
                row("Q", "aetherhaven.pathTool.hud.replaceFilter.q"),
                info("aetherhaven.pathTool.hud.replaceFilter.info")
            );
        };
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
