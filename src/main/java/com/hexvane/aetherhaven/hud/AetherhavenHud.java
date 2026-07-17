package com.hexvane.aetherhaven.hud;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Persistent Aetherhaven status and quest overlay. It owns one full-screen, non-hit-testable UI document and sends
 * only fields that changed since the previous snapshot.
 */
public final class AetherhavenHud extends CustomUIHud {
    public static final String HUD_KEY = "Aetherhaven.CoreHud";
    public static final int STATUS_WIDTH = 286;
    public static final int QUEST_WIDTH = 370;

    @Nonnull
    private HudPanelPlacement statusPlacement;
    @Nonnull
    private HudPanelPlacement questPlacement;
    private int statusHeight = statusHeight(true, true, true);
    private int questHeight = questHeight(0);
    @Nullable
    private AetherhavenHudSnapshot previous;

    public AetherhavenHud(@Nonnull PlayerRef playerRef) {
        this(playerRef, HudPanelPlacement.topRight(), HudPanelPlacement.topRight());
    }

    public AetherhavenHud(
        @Nonnull PlayerRef playerRef,
        @Nonnull HudPanelPlacement statusPlacement,
        @Nonnull HudPanelPlacement questPlacement
    ) {
        super(playerRef, HUD_KEY, 0);
        this.statusPlacement = statusPlacement;
        this.questPlacement = questPlacement;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/AetherhavenHud.ui");
        commandBuilder.set(
            "#QuestHeading.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.hud.quests")
        );
        commandBuilder.set("#GoldIconLeft.ItemId", AetherhavenConstants.ITEM_GOLD_COIN);
        commandBuilder.set("#GoldIconRight.ItemId", AetherhavenConstants.ITEM_GOLD_COIN);
        applyAnchor(commandBuilder, "#StatusPanel", statusPlacement, STATUS_WIDTH, statusHeight);
        applyAnchor(commandBuilder, "#QuestPanel", questPlacement, QUEST_WIDTH, questHeight);
        applyStatusAlignment(commandBuilder, statusPlacement);
        applyQuestAlignment(commandBuilder, questPlacement);
    }

    /** Changes only the status panel placement. */
    public void setStatusPlacement(@Nonnull HudPanelPlacement placement) {
        if (statusPlacement.equals(placement)) {
            return;
        }
        statusPlacement = placement;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        applyAnchor(commandBuilder, "#StatusPanel", placement, STATUS_WIDTH, statusHeight);
        applyStatusAlignment(commandBuilder, placement);
        update(false, commandBuilder);
    }

    /** Changes only the quest panel placement. */
    public void setQuestPlacement(@Nonnull HudPanelPlacement placement) {
        if (questPlacement.equals(placement)) {
            return;
        }
        questPlacement = placement;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        applyAnchor(commandBuilder, "#QuestPanel", placement, QUEST_WIDTH, questHeight);
        applyQuestAlignment(commandBuilder, placement);
        update(false, commandBuilder);
    }

    @Nonnull
    public HudPanelPlacement getStatusPlacement() {
        return statusPlacement;
    }

    @Nonnull
    public HudPanelPlacement getQuestPlacement() {
        return questPlacement;
    }

    public void refresh(@Nonnull AetherhavenHudSnapshot snapshot) {
        UICommandBuilder commands = new UICommandBuilder();
        boolean changed = false;
        AetherhavenHudSnapshot old = previous;

        if (old == null || Float.compare(old.backgroundOpacity(), snapshot.backgroundOpacity()) != 0) {
            applyBackground(commands, "#StatusBackgroundHost", snapshot.backgroundOpacity());
            applyBackground(commands, "#QuestBackgroundHost", snapshot.backgroundOpacity());
            changed = true;
        }
        if (
            old == null
                || old.showTime() != snapshot.showTime()
                || old.showDate() != snapshot.showDate()
                || old.showGold() != snapshot.showGold()
        ) {
            commands.set("#StatusPanel.Visible", snapshot.showTime() || snapshot.showDate() || snapshot.showGold());
            commands.set("#TimeRow.Visible", snapshot.showTime());
            commands.set("#DateRow.Visible", snapshot.showDate());
            commands.set("#GoldRow.Visible", snapshot.showGold());
            commands.set("#DividerTime.Visible", snapshot.showTime() && (snapshot.showDate() || snapshot.showGold()));
            commands.set("#DividerDate.Visible", snapshot.showDate() && snapshot.showGold());
            int nextStatusHeight = statusHeight(snapshot.showTime(), snapshot.showDate(), snapshot.showGold());
            if (nextStatusHeight != statusHeight) {
                statusHeight = nextStatusHeight;
                applyAnchor(commands, "#StatusPanel", statusPlacement, STATUS_WIDTH, statusHeight);
            }
            changed = true;
        }
        if (old == null || !old.dateText().equals(snapshot.dateText())) {
            commands.set("#DateText.TextSpans", Message.raw(snapshot.dateText()));
            changed = true;
        }
        if (old == null || !old.clockText().equals(snapshot.clockText())) {
            commands.set("#ClockText.TextSpans", Message.raw(snapshot.clockText()));
            changed = true;
        }
        if (old == null
            || old.inventoryCoins() != snapshot.inventoryCoins()
            || old.treasuryCoins() != snapshot.treasuryCoins()
            || old.totalCoins() != snapshot.totalCoins()) {
            Message amount = Message.raw(String.format("%,d", snapshot.totalCoins()));
            commands.set("#CoinTextLeft.TextSpans", amount);
            commands.set("#CoinTextRight.TextSpans", amount);
            changed = true;
        }
        boolean questVisible = snapshot.showQuests() && !snapshot.quests().isEmpty();
        boolean oldQuestVisible = old != null && old.showQuests() && !old.quests().isEmpty();
        int nextQuestHeight = questHeight(snapshot.quests());
        if (old == null || questVisible != oldQuestVisible || nextQuestHeight != questHeight) {
            commands.set("#QuestPanel.Visible", questVisible);
            if (nextQuestHeight != questHeight) {
                questHeight = nextQuestHeight;
                applyAnchor(commands, "#QuestPanel", questPlacement, QUEST_WIDTH, questHeight);
            }
            changed = true;
        }
        if (old == null || !sameQuests(old.quests(), snapshot.quests())) {
            applyQuests(commands, snapshot.quests());
            changed = true;
        }

        previous = snapshot;
        if (changed) {
            update(false, commands);
        }
    }

    private static void applyQuests(@Nonnull UICommandBuilder commands, @Nonnull List<HudQuestEntry> quests) {
        for (int i = 0; i < AetherhavenHudSnapshotService.MAX_QUESTS; i++) {
            String row = "#QuestRow" + (i + 1);
            boolean visible = i < quests.size();
            commands.set(row + ".Visible", visible);
            if (visible) {
                HudQuestEntry quest = quests.get(i);
                Anchor rowAnchor = new Anchor();
                rowAnchor.setHeight(Value.of(questRowHeight(quest)));
                if (i < AetherhavenHudSnapshotService.MAX_QUESTS - 1) {
                    rowAnchor.setBottom(Value.of(7));
                }
                commands.setObject(row + ".Anchor", rowAnchor);
                commands.set(row + " #QuestTitle.TextSpans", quest.title());
                commands.set(row + " #QuestObjectives.TextSpans", quest.objectives());
            }
        }
    }

    static int statusHeight(boolean showTime, boolean showDate, boolean showGold) {
        int height = 10;
        if (showTime) {
            height += 30;
        }
        if (showDate) {
            height += 22;
        }
        if (showGold) {
            height += 41;
        }
        if (showTime && (showDate || showGold)) {
            height += 7;
        }
        if (showDate && showGold) {
            height += 7;
        }
        return Math.max(46, height);
    }

    static int questHeight(int questCount) {
        int count = Math.max(0, Math.min(AetherhavenHudSnapshotService.MAX_QUESTS, questCount));
        return 33 + (84 * count) + (7 * Math.min(count, 2));
    }

    private static int questHeight(@Nonnull List<HudQuestEntry> quests) {
        int count = Math.min(AetherhavenHudSnapshotService.MAX_QUESTS, quests.size());
        int height = 33 + (7 * Math.min(count, 2));
        for (int i = 0; i < count; i++) {
            height += questRowHeight(quests.get(i));
        }
        return height;
    }

    static int questRowHeight(@Nonnull HudQuestEntry quest) {
        int titleLines = estimatedWrappedLines(quest.title().getAnsiMessage(), 40);
        int objectiveLines = estimatedWrappedLines(quest.objectives().getAnsiMessage(), 48);
        return Math.max(84, 21 + (18 * titleLines) + (16 * objectiveLines));
    }

    private static int estimatedWrappedLines(@Nullable String text, int charactersPerLine) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int lines = 0;
        for (String rawLine : text.split("\\R", -1)) {
            int length = rawLine.strip().length();
            lines += Math.max(1, (length + charactersPerLine - 1) / charactersPerLine);
        }
        return lines;
    }

    private static void applyStatusAlignment(
        @Nonnull UICommandBuilder commands,
        @Nonnull HudPanelPlacement placement
    ) {
        boolean right = isRightSide(placement);
        String alignment = right ? "End" : "Start";
        commands.set("#ClockText.Style.HorizontalAlignment", alignment);
        commands.set("#DateText.Style.HorizontalAlignment", alignment);
        commands.set("#GoldLeftContent.Visible", !right);
        commands.set("#GoldRightContent.Visible", right);
    }

    private static void applyQuestAlignment(
        @Nonnull UICommandBuilder commands,
        @Nonnull HudPanelPlacement placement
    ) {
        boolean right = isRightSide(placement);
        String alignment = right ? "End" : "Start";
        commands.set("#QuestHeading.Style.HorizontalAlignment", alignment);
        commands.set("#QuestDividerLeft.Visible", !right);
        commands.set("#QuestDividerRight.Visible", right);
        for (int i = 1; i <= AetherhavenHudSnapshotService.MAX_QUESTS; i++) {
            String row = "#QuestRow" + i;
            commands.set(row + " #QuestTitle.Style.HorizontalAlignment", alignment);
            commands.set(row + " #QuestObjectives.Style.HorizontalAlignment", alignment);
        }
    }

    private static boolean isRightSide(@Nonnull HudPanelPlacement placement) {
        return placement.placement() == HudPlacement.TOP_RIGHT
            || placement.placement() == HudPlacement.BOTTOM_RIGHT;
    }

    @Nonnull
    private static void applyBackground(
        @Nonnull UICommandBuilder commands,
        @Nonnull String host,
        float opacity
    ) {
        float clamped = Math.max(0f, Math.min(1f, opacity));
        commands.clear(host);
        if (clamped <= 0f) {
            return;
        }
        commands.appendInline(
            host,
            String.format(
                Locale.ROOT,
                "Group { Anchor: (Full: 0); Background: #000000(%.3f); }",
                clamped
            )
        );
    }

    private static boolean sameQuests(@Nonnull List<HudQuestEntry> left, @Nonnull List<HudQuestEntry> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).fingerprint().equals(right.get(i).fingerprint())) {
                return false;
            }
        }
        return true;
    }

    private static void applyAnchor(
        @Nonnull UICommandBuilder commands,
        @Nonnull String selector,
        @Nonnull HudPanelPlacement placement,
        int width,
        int height
    ) {
        HudLayout.ResolvedAnchor resolved = HudLayout.resolve(placement, width, height);
        Anchor anchor = new Anchor();
        if (resolved.left() != null) {
            anchor.setLeft(Value.of(resolved.left()));
        }
        if (resolved.top() != null) {
            anchor.setTop(Value.of(resolved.top()));
        }
        if (resolved.right() != null) {
            anchor.setRight(Value.of(resolved.right()));
        }
        if (resolved.bottom() != null) {
            anchor.setBottom(Value.of(resolved.bottom()));
        }
        anchor.setWidth(Value.of(resolved.width()));
        anchor.setHeight(Value.of(resolved.height()));
        commands.setObject(selector + ".Anchor", anchor);
    }
}
