package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.AetherhavenUiLocalization;
import com.hexvane.aetherhaven.ui.ToolHudHotkeyRows;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PatrolWandStatusHud extends CustomUIHud {
    private static final String CONTROL_ROWS = "#ControlRows";
    private static final String LANG_PREFIX = "aetherhaven_items.";

    public PatrolWandStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.PATROL_WAND_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/PatrolWandStatusHud.ui");
        AetherhavenUiLocalization.applyPatrolWandStatusHudTitle(commandBuilder, selector -> selector);
    }

    public void refresh(
        @Nonnull PatrolWandPlayerComponent st,
        @Nullable PatrolRouteRecord selectedRoute,
        @Nullable TownRecord town
    ) {
        UICommandBuilder b = new UICommandBuilder();
        PatrolWandMode mode = st.getMode();
        b.set("#PatrolWandModeTabs.SelectedTab", st.modeTabId());
        b.set(
            "#ModeHelp.TextSpans",
            Message.translation(
                mode == PatrolWandMode.Build
                    ? LANG_PREFIX + "aetherhaven.patrolWand.hudDescBuild"
                    : LANG_PREFIX + "aetherhaven.patrolWand.hudDescAssign"
            )
        );
        Message routeName;
        if (selectedRoute != null) {
            routeName = Message.raw(selectedRoute.safeDisplayName());
        } else if (st.getEditingRouteId() == null) {
            routeName = Message.translation(LANG_PREFIX + "aetherhaven.patrolWand.hudRouteNew");
        } else {
            routeName = Message.translation(LANG_PREFIX + "aetherhaven.patrolWand.hudRouteUnnamed");
        }
        b.set(
            "#RouteLine.TextSpans",
            Message.translation(LANG_PREFIX + "aetherhaven.patrolWand.hudRoute").param("name", routeName)
        );
        boolean buildMode = mode == PatrolWandMode.Build;
        b.set("#NodesLine.Visible", buildMode);
        b.set("#LoopLine.Visible", buildMode);
        b.set("#GuardLine.Visible", !buildMode);
        if (buildMode) {
            b.set(
                "#NodesLine.TextSpans",
                Message
                    .translation(LANG_PREFIX + "aetherhaven.patrolWand.hudNodes")
                    .param("n", String.valueOf(st.getDraftNodes().size()))
            );
            boolean closedLoop = st.isDraftClosedLoop();
            b.set(
                "#LoopLine.TextSpans",
                Message.translation(
                    closedLoop
                        ? LANG_PREFIX + "aetherhaven.patrolWand.hudLoopClosed"
                        : LANG_PREFIX + "aetherhaven.patrolWand.hudLoopOpen"
                )
            );
        } else {
            Message guardLine =
                selectedRoute != null && selectedRoute.getAssignedGuardUuidParsed() != null
                    ? Message.translation(LANG_PREFIX + "aetherhaven.patrolWand.hudGuardAssigned")
                    : Message.translation(LANG_PREFIX + "aetherhaven.patrolWand.hudGuardNone");
            b.set(
                "#GuardLine.TextSpans",
                Message.translation(LANG_PREFIX + "aetherhaven.patrolWand.hudGuard").param("guard", guardLine)
            );
        }
        b.clear(CONTROL_ROWS);
        List<PatrolWandHudControls.Row> rows = PatrolWandHudControls.rowsFor(mode);
        for (int i = 0; i < rows.size(); i++) {
            PatrolWandHudControls.Row row = rows.get(i);
            ToolHudHotkeyRows.appendRow(
                b,
                CONTROL_ROWS,
                i,
                row.slot(),
                LANG_PREFIX + row.descriptionLangKey(),
                getPlayerRef()
            );
        }
        this.update(false, b);
    }
}
