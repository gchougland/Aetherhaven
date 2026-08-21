package com.hexvane.aetherhaven.pathtool;

import java.util.List;
import javax.annotation.Nonnull;

/** Rows for the path tool middle-click mode jump menu. */
public final class PathToolModeJumpModel {
    public record JumpNode(
        @Nonnull PathToolGizmoMode mode,
        @Nonnull String shortLangKey,
        @Nonnull String iconAssetPath,
        boolean current
    ) {}

    private PathToolModeJumpModel() {}

    /** Display order matches the status HUD tabs / Q cycle starting from Place. */
    @Nonnull
    public static List<JumpNode> nodes(@Nonnull PathToolGizmoMode current) {
        return List.of(
            node(PathToolGizmoMode.Commit, "aetherhaven_items.aetherhaven.pathTool.hudTabPlace", "UI/Custom/location.png", current),
            node(
                PathToolGizmoMode.Translate,
                "aetherhaven_items.aetherhaven.pathTool.hudTabMove",
                "UI/Custom/move_icon_256.png",
                current
            ),
            node(
                PathToolGizmoMode.Rotate,
                "aetherhaven_items.aetherhaven.pathTool.hudTabRotate",
                "UI/Custom/rotate_clockwise_256.png",
                current
            ),
            node(
                PathToolGizmoMode.Remove,
                "aetherhaven_items.aetherhaven.pathTool.hudTabRemove",
                "UI/Custom/bulldozer_icon_256.png",
                current
            ),
            node(
                PathToolGizmoMode.Restyle,
                "aetherhaven_items.aetherhaven.pathTool.hudTabRestyle",
                "UI/Custom/paintbrush.png",
                current
            ),
            node(
                PathToolGizmoMode.ReplaceFilter,
                "aetherhaven_items.aetherhaven.pathTool.hudTabReplaceFilter",
                "UI/Custom/filter.png",
                current
            ),
            node(
                PathToolGizmoMode.StyleDesigner,
                "aetherhaven_items.aetherhaven.pathTool.hudTabStyleDesigner",
                "UI/Custom/idea.png",
                current
            )
        );
    }

    @Nonnull
    private static JumpNode node(
        @Nonnull PathToolGizmoMode mode,
        @Nonnull String shortLangKey,
        @Nonnull String iconAssetPath,
        @Nonnull PathToolGizmoMode current
    ) {
        return new JumpNode(mode, shortLangKey, iconAssetPath, mode == current);
    }
}
