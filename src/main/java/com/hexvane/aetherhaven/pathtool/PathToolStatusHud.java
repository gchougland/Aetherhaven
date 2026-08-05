package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.ui.AetherhavenUiLocalization;
import com.hexvane.aetherhaven.ui.ToolKeybindDisplay;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;

/** In-world HUD overlay for path width and mode; shown while the path tool is held. */
public final class PathToolStatusHud extends CustomUIHud {
    private static final String LANG_PREFIX = "aetherhaven_items.";
    private static final String[] HINT_GROUPS = {
        "#RowsTranslate",
        "#RowsRotate",
        "#RowsCommit",
        "#RowsRemove",
        "#RowsStyleDesigner",
        "#RowsStyleDesignerEdit",
        "#RowsReplaceFilter",
        "#RowsReplaceFilterEdit",
    };

    public PathToolStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.PATH_TOOL_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/PathToolStatusHud.ui");
        AetherhavenUiLocalization.applyPathToolStatusHudTitle(commandBuilder, selector -> selector);
    }

    public void refresh(@Nonnull PathToolPlayerComponent st, @Nonnull AetherhavenPluginConfig cfg, @Nonnull PlayerRef playerRef) {
        UICommandBuilder b = new UICommandBuilder();
        PathToolGizmoMode mode = st.getGizmoMode();
        b.set("#PathToolModeTabs.SelectedTab", st.modeTabId());
        b.set(
            "#ModeName.TextSpans",
            Message.translation(
                switch (mode) {
                    case Translate -> LANG_PREFIX + "aetherhaven.pathTool.hudNameTranslate";
                    case Rotate -> LANG_PREFIX + "aetherhaven.pathTool.hudNameRotate";
                    case Commit -> LANG_PREFIX + "aetherhaven.pathTool.hudNameCommit";
                    case Remove -> LANG_PREFIX + "aetherhaven.pathTool.hudNameRemove";
                    case StyleDesigner -> LANG_PREFIX + "aetherhaven.pathTool.hudNameStyleDesigner";
                    case ReplaceFilter -> LANG_PREFIX + "aetherhaven.pathTool.hudNameReplaceFilter";
                }
            )
        );
        b.set(
            "#ModeHelp.TextSpans",
            Message.translation(
                switch (mode) {
                    case Translate -> LANG_PREFIX + "aetherhaven.pathTool.hudDescTranslate";
                    case Rotate -> LANG_PREFIX + "aetherhaven.pathTool.hudDescRotate";
                    case Commit -> LANG_PREFIX + "aetherhaven.pathTool.hudDescCommit";
                    case Remove -> LANG_PREFIX + "aetherhaven.pathTool.hudDescRemove";
                    case StyleDesigner -> LANG_PREFIX + "aetherhaven.pathTool.hudDescStyleDesigner";
                    case ReplaceFilter -> LANG_PREFIX + "aetherhaven.pathTool.hudDescReplaceFilter";
                }
            )
        );
        boolean showEditStats =
            mode != PathToolGizmoMode.Remove
                && mode != PathToolGizmoMode.StyleDesigner
                && mode != PathToolGizmoMode.ReplaceFilter;
        b.set("#StyleLine.Visible", showEditStats);
        b.set("#WidthLine.Visible", showEditStats);
        b.set("#NodesLine.Visible", showEditStats);
        if (showEditStats) {
            b.set(
                "#StyleLine.TextSpans",
                Message
                    .translation(LANG_PREFIX + "aetherhaven.pathTool.hudStyle")
                    .param("style", cfg.getPathToolStyleName(st.getPathStyleIndex()))
            );
            b.set(
                "#WidthLine.TextSpans",
                Message
                    .translation(LANG_PREFIX + "aetherhaven.pathTool.hudWidth")
                    .param("w", String.valueOf(st.getPathWidthBlocks()))
            );
            b.set(
                "#NodesLine.TextSpans",
                Message
                    .translation(LANG_PREFIX + "aetherhaven.pathTool.hudNodes")
                    .param("n", String.valueOf(st.getNodes().size()))
            );
        }
        boolean showPlaceReminder = mode != PathToolGizmoMode.Commit;
        b.set("#PlaceModeReminder.Visible", showPlaceReminder);
        if (showPlaceReminder) {
            b.set(
                "#PlaceModeReminder.TextSpans",
                Message.translation(LANG_PREFIX + "aetherhaven.pathTool.hudPlaceReminder")
            );
        }
        boolean styleEditingActive = false;
        boolean replaceFilterEditingActive = false;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref != null) {
            Store<EntityStore> store = ref.getStore();
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc != null) {
                PathToolStyleSessions.Session session = PathToolStyleSessions.get(uc.getUuid());
                styleEditingActive = session != null && session.editingActive;
            }
            PathToolPlayerComponent pathSt = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
            if (pathSt != null) {
                replaceFilterEditingActive = PathToolReplaceFilterUi.isActivelyEditing(ref, store);
            }
        }
        String activeGroup = hintGroupSelector(mode, styleEditingActive, replaceFilterEditingActive);
        for (String group : HINT_GROUPS) {
            b.set(group + ".Visible", group.equals(activeGroup));
        }
        List<PathToolHudControls.Row> rows =
            PathToolHudControls.rowsFor(mode, styleEditingActive, replaceFilterEditingActive);
        String rowPrefix = activeGroup + " #";
        int keyIndex = 0;
        int infoIndex = 0;
        for (PathToolHudControls.Row row : rows) {
            if (row.infoOnly()) {
                b.set(
                    rowPrefix + "Info" + infoIndex + ".TextSpans",
                    Message.translation(LANG_PREFIX + row.descriptionLangKey())
                );
                infoIndex++;
            } else {
                b.set(
                    rowPrefix + "Key" + keyIndex + ".TextSpans",
                    Message.raw(ToolKeybindDisplay.labelFor(playerRef, row.slot()))
                );
                b.set(
                    rowPrefix + "Desc" + keyIndex + ".TextSpans",
                    Message.translation(LANG_PREFIX + row.descriptionLangKey())
                );
                keyIndex++;
            }
        }
        this.update(false, b);
    }

    @Nonnull
    private static String hintGroupSelector(
        @Nonnull PathToolGizmoMode mode,
        boolean styleEditingActive,
        boolean replaceFilterEditingActive
    ) {
        return switch (mode) {
            case Translate -> "#RowsTranslate";
            case Rotate -> "#RowsRotate";
            case Commit -> "#RowsCommit";
            case Remove -> "#RowsRemove";
            case StyleDesigner -> styleEditingActive ? "#RowsStyleDesignerEdit" : "#RowsStyleDesigner";
            case ReplaceFilter -> replaceFilterEditingActive ? "#RowsReplaceFilterEdit" : "#RowsReplaceFilter";
        };
    }
}
