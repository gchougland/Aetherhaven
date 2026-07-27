package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.ui.ToolKeybindSlot;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Mode-specific HUD control rows (key slot + lang key for description). */
public final class PathToolHudControls {
    public record Row(@Nonnull ToolKeybindSlot slot, @Nonnull String descriptionLangKey, boolean infoOnly) {}

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
                row(ToolKeybindSlot.USE, "aetherhaven.pathTool.hud.styleDesigner.fContinue"),
                row(ToolKeybindSlot.ABILITY2, "aetherhaven.pathTool.hud.styleDesigner.eSave"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.pathTool.hud.styleDesigner.q"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoColumns"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoRows"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoWeight")
            );
        }
        if (mode == PathToolGizmoMode.ReplaceFilter && replaceFilterEditingActive) {
            return List.of(
                row(ToolKeybindSlot.USE, "aetherhaven.pathTool.hud.replaceFilter.fContinue"),
                row(ToolKeybindSlot.ABILITY2, "aetherhaven.pathTool.hud.replaceFilter.eSave"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.pathTool.hud.replaceFilter.q"),
                info("aetherhaven.pathTool.hud.replaceFilter.info")
            );
        }
        return rowsFor(mode);
    }

    @Nonnull
    public static List<Row> rowsFor(@Nonnull PathToolGizmoMode mode) {
        return switch (mode) {
            case Translate -> List.of(
                row(ToolKeybindSlot.PRIMARY, "aetherhaven.pathTool.hud.move.primary"),
                row(ToolKeybindSlot.SECONDARY, "aetherhaven.pathTool.hud.move.secondary"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.pathTool.hud.move.q"),
                row(ToolKeybindSlot.ABILITY2, "aetherhaven.pathTool.hud.move.e"),
                row(ToolKeybindSlot.ABILITY3, "aetherhaven.pathTool.hud.move.r")
            );
            case Rotate -> List.of(
                row(ToolKeybindSlot.PRIMARY, "aetherhaven.pathTool.hud.rotate.primary"),
                row(ToolKeybindSlot.SECONDARY, "aetherhaven.pathTool.hud.rotate.secondary"),
                row(ToolKeybindSlot.USE, "aetherhaven.pathTool.hud.rotate.f"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.pathTool.hud.rotate.q"),
                row(ToolKeybindSlot.ABILITY2, "aetherhaven.pathTool.hud.rotate.e"),
                row(ToolKeybindSlot.ABILITY3, "aetherhaven.pathTool.hud.rotate.r")
            );
            case Commit -> List.of(
                row(ToolKeybindSlot.PRIMARY, "aetherhaven.pathTool.hud.place.primary"),
                row(ToolKeybindSlot.SECONDARY, "aetherhaven.pathTool.hud.place.secondary"),
                row(ToolKeybindSlot.USE, "aetherhaven.pathTool.hud.place.f"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.pathTool.hud.place.q"),
                row(ToolKeybindSlot.ABILITY2, "aetherhaven.pathTool.hud.place.e"),
                row(ToolKeybindSlot.ABILITY3, "aetherhaven.pathTool.hud.place.r")
            );
            case Remove -> List.of(
                row(ToolKeybindSlot.PRIMARY, "aetherhaven.pathTool.hud.remove.primary"),
                row(ToolKeybindSlot.USE, "aetherhaven.pathTool.hud.remove.f"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.pathTool.hud.remove.q")
            );
            case StyleDesigner -> List.of(
                row(ToolKeybindSlot.USE, "aetherhaven.pathTool.hud.styleDesigner.f"),
                row(ToolKeybindSlot.ABILITY2, "aetherhaven.pathTool.hud.styleDesigner.e"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.pathTool.hud.styleDesigner.q"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoColumns"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoRows"),
                info("aetherhaven.pathTool.hud.styleDesigner.infoWeight")
            );
            case ReplaceFilter -> List.of(
                row(ToolKeybindSlot.USE, "aetherhaven.pathTool.hud.replaceFilter.f"),
                row(ToolKeybindSlot.ABILITY2, "aetherhaven.pathTool.hud.replaceFilter.e"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.pathTool.hud.replaceFilter.q"),
                info("aetherhaven.pathTool.hud.replaceFilter.info")
            );
        };
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
