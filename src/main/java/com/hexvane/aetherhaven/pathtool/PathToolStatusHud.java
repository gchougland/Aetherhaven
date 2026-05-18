package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.ui.AetherhavenUiLocalization;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * In-world HUD overlay (via {@link com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager#setCustomHud}) for
 * path width and mode; shown while the path tool is held. When registered under Buuz135 MHUD, {@link #mhudLayout} is true
 * so refresh selectors match {@code MultipleCustomUIHud} slot prefixes.
 */
public final class PathToolStatusHud extends CustomUIHud {
    private final boolean mhudLayout;

    public PathToolStatusHud(@Nonnull PlayerRef playerRef) {
        this(playerRef, false);
    }

    public PathToolStatusHud(@Nonnull PlayerRef playerRef, boolean mhudLayout) {
        super(playerRef);
        this.mhudLayout = mhudLayout;
    }

    @Nonnull
    private String scoped(@Nonnull String selector) {
        if (!mhudLayout) {
            return selector;
        }
        String id = AetherhavenConstants.PATH_TOOL_MHUD_SLOT.replaceAll("[^a-zA-Z0-9]", "");
        return "#MultipleHUD #" + id + " " + selector;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/PathToolStatusHud.ui");
    }

    public void refresh(@Nonnull PathToolPlayerComponent st, @Nonnull AetherhavenPluginConfig cfg) {
        UICommandBuilder b = new UICommandBuilder();
        // Initial tree comes from {@link #show()} (append + clear). Use partial CustomHud updates here: sending
        // clear=true and re-appending every tick was hammering the client and has been linked to black/broken HUD
        // overlays on some GPUs (e.g. AMD) when holding the path tool idle.
        AetherhavenUiLocalization.applyPathToolStatusHudTitle(b, this::scoped);
        b.set(
            scoped("#ModeName.TextSpans"),
            Message.translation(
                switch (st.getGizmoMode()) {
                    case Translate -> "aetherhaven_items.aetherhaven.pathTool.hudNameTranslate";
                    case Rotate -> "aetherhaven_items.aetherhaven.pathTool.hudNameRotate";
                    case Commit -> "aetherhaven_items.aetherhaven.pathTool.hudNameCommit";
                }
            )
        );
        b.set(
            scoped("#ModeHelp.TextSpans"),
            Message.translation(
                switch (st.getGizmoMode()) {
                    case Translate -> "aetherhaven_items.aetherhaven.pathTool.hudHelpTranslate";
                    case Rotate -> "aetherhaven_items.aetherhaven.pathTool.hudHelpRotate";
                    case Commit -> "aetherhaven_items.aetherhaven.pathTool.hudHelpCommit";
                }
            )
        );
        b.set(
            scoped("#StyleLine.TextSpans"),
            Message
                .translation("aetherhaven_items.aetherhaven.pathTool.hudStyle")
                .param("style", cfg.getPathToolStyleName(st.getPathStyleIndex()))
        );
        b.set(
            scoped("#WidthLine.TextSpans"),
            Message
                .translation("aetherhaven_items.aetherhaven.pathTool.hudWidth")
                .param("w", String.valueOf(st.getPathWidthBlocks()))
        );
        b.set(
            scoped("#NodesLine.TextSpans"),
            Message
                .translation("aetherhaven_items.aetherhaven.pathTool.hudNodes")
                .param("n", String.valueOf(st.getNodes().size()))
        );
        b.set(scoped("#HintLine.TextSpans"), Message.translation("aetherhaven_items.aetherhaven.pathTool.hudHint"));
        this.update(false, b);
    }
}
