package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared roster overlay listing everyone taking part in one festival activity. Every activity reuses this HUD and
 * only supplies rows, so a player never sees a roster for something they are not in.
 */
public final class FestivalActivityRosterHud extends CustomUIHud {
    /** Pixel height of one roster row, matching {@code FestivalActivityRosterRow.ui}. */
    private static final int ROW_HEIGHT = 24;
    private static final int MAX_ROWS = 12;
    /** Snowflake slots authored in {@code FestivalActivityRosterRow.ui}. */
    public static final int MAX_LIFE_ICONS = 3;

    private static final String SHELL = "#FestivalRosterShell";
    private static final String PANEL = SHELL + " #FestivalRosterPanel";
    private static final String TITLE = PANEL + " #FestivalRosterTitle";
    private static final String ROWS = PANEL + " #FestivalRosterRows";
    private static final String EMPTY = PANEL + " #FestivalRosterEmpty";

    private static final String NAME_COLOR = "#f0e6d2";
    private static final String NAME_COLOR_DIM = "#8a8072";
    private static final String STATUS_COLOR = "#c8d4a8";
    private static final String STATUS_COLOR_DIM = "#7a7266";
    private static final String DOT_COLOR = "#f0e6d2";

    private int boundRowCount = -1;

    /**
     * One participant line. {@code dotColor} carries a team colour when the activity has teams, and {@code dimmed}
     * marks somebody who is out or already finished. A row shows either {@code status} text or, when
     * {@code lifeSlots} is positive, that many snowflakes with {@code livesLeft} of them lit.
     */
    public record Row(
        @Nonnull Message name,
        @Nullable Message status,
        @Nullable String dotColor,
        boolean dimmed,
        int livesLeft,
        int lifeSlots
    ) {
        public Row(
            @Nonnull Message name,
            @Nullable Message status,
            @Nullable String dotColor,
            boolean dimmed
        ) {
            this(name, status, dotColor, dimmed, 0, 0);
        }

        /** Row that counts down snowflakes instead of spelling the number out. */
        @Nonnull
        public static Row withLives(
            @Nonnull Message name,
            @Nullable String dotColor,
            boolean dimmed,
            int livesLeft,
            int lifeSlots
        ) {
            return new Row(name, null, dotColor, dimmed, livesLeft, lifeSlots);
        }
    }

    public FestivalActivityRosterHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.FESTIVAL_ACTIVITY_ROSTER_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/FestivalActivityRosterPanel.ui");
    }

    public void refresh(@Nonnull Message title, @Nonnull List<Row> rows, @Nonnull Message emptyText) {
        List<Row> shown = rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set(TITLE + ".TextSpans", title);
        if (shown.size() != boundRowCount) {
            cmd.clear(ROWS);
            for (int i = 0; i < shown.size(); i++) {
                cmd.append(ROWS, "Aetherhaven/FestivalActivityRosterRow.ui");
            }
            Anchor rowsAnchor = new Anchor();
            rowsAnchor.setHeight(Value.of(Math.max(1, shown.size() * ROW_HEIGHT)));
            cmd.setObject(ROWS + ".Anchor", rowsAnchor);
            boundRowCount = shown.size();
        }
        cmd.set(ROWS + ".Visible", !shown.isEmpty());
        cmd.set(EMPTY + ".Visible", shown.isEmpty());
        if (shown.isEmpty()) {
            cmd.set(EMPTY + ".TextSpans", emptyText);
        }
        applyRows(cmd, shown);
        this.update(false, cmd);
    }

    private static void applyRows(@Nonnull UICommandBuilder cmd, @Nonnull List<Row> rows) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            String base = ROWS + "[" + i + "]";
            cmd.set(base + " #RosterRowName.TextSpans", row.name());
            cmd.set(base + " #RosterRowName.Style.TextColor", row.dimmed() ? NAME_COLOR_DIM : NAME_COLOR);
            applyRowRight(cmd, base, row);
            String dot = row.dotColor();
            cmd.set(base + " #RosterRowDot.Visible", dot != null);
            cmd.set(base + " #RosterRowDot.Background", dot != null ? dot : DOT_COLOR);
        }
    }

    private static void applyRowRight(@Nonnull UICommandBuilder cmd, @Nonnull String base, @Nonnull Row row) {
        String lives = base + " #RosterRowRight #RosterRowLives";
        String status = base + " #RosterRowRight #RosterRowStatus";
        int slots = Math.min(MAX_LIFE_ICONS, Math.max(0, row.lifeSlots()));
        cmd.set(lives + ".Visible", slots > 0);
        cmd.set(status + ".Visible", slots <= 0 && row.status() != null);
        if (slots > 0) {
            int lit = Math.max(0, Math.min(slots, row.livesLeft()));
            for (int slot = 0; slot < MAX_LIFE_ICONS; slot++) {
                cmd.set(lives + " #RosterLife" + slot + ".Visible", slot < slots);
                cmd.set(lives + " #RosterLife" + slot + " #RosterLife" + slot + "Fg.Visible", slot < lit);
            }
            return;
        }
        Message text = row.status();
        if (text != null) {
            cmd.set(status + ".TextSpans", text);
            cmd.set(status + ".Style.TextColor", row.dimmed() ? STATUS_COLOR_DIM : STATUS_COLOR);
        }
    }
}
