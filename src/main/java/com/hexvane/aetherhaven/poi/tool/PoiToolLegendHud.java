package com.hexvane.aetherhaven.poi.tool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.ui.AetherhavenUiLocalization;
import com.hexvane.aetherhaven.ui.ToolHudHotkeyRows;
import com.hexvane.aetherhaven.ui.ToolKeybindSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/** In-world legend overlay for the POI debug staff; shown while the tool is held. */
public final class PoiToolLegendHud extends CustomUIHud {
    private static final String HINT_ROWS = "#HintRows";
    private static final String HINT_DESC = "aetherhaven_items.aetherhaven.poiTool.hudHint.desc";

    public PoiToolLegendHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.POI_TOOL_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/PoiToolLegendHud.ui");
    }

    public void refresh(@Nonnull PoiToolPlayerComponent state) {
        UICommandBuilder b = new UICommandBuilder();
        AetherhavenUiLocalization.applyPoiToolLegendHudTitle(b, selector -> selector);
        PoiToolMode mode = state.getMode();
        String nameKey =
            switch (mode) {
                case PoiEdit -> "aetherhaven_items.aetherhaven.poiTool.hudNameEdit";
                case PoiPlacement -> "aetherhaven_items.aetherhaven.poiTool.hudNamePlacement";
                case PoiRemove -> "aetherhaven_items.aetherhaven.poiTool.hudNameRemove";
                case AdventurerSpawnMarker -> "aetherhaven_items.aetherhaven.poiTool.hudNameAdventurerSpots";
            };
        String helpKey =
            switch (mode) {
                case PoiEdit -> "aetherhaven_items.aetherhaven.poiTool.hudHelpEdit";
                case PoiPlacement -> "aetherhaven_items.aetherhaven.poiTool.hudHelpPlacement";
                case PoiRemove -> "aetherhaven_items.aetherhaven.poiTool.hudHelpRemove";
                case AdventurerSpawnMarker -> "aetherhaven_items.aetherhaven.poiTool.hudHelpAdventurerSpots";
            };
        b.set("#ModeName.TextSpans", Message.translation(nameKey));
        b.set("#ModeHelp.TextSpans", Message.translation(helpKey));
        b.clear(HINT_ROWS);
        ToolHudHotkeyRows.appendHotkeyRow(b, HINT_ROWS, 0, ToolKeybindSlot.ABILITY1, HINT_DESC, getPlayerRef());
        this.update(false, b);
    }
}
