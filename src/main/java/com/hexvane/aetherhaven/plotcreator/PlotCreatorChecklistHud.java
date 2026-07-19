package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import javax.annotation.Nonnull;

/** Right-side transparent requirements checklist for the plot creator. */
public final class PlotCreatorChecklistHud extends CustomUIHud {
    private static final String ROWS = "#ChecklistRows";
    private static final String LANG = "aetherhaven_plot_creator.aetherhaven.plotcreator.";

    public PlotCreatorChecklistHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.PLOT_CREATOR_CHECKLIST_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/PlotCreatorChecklistHud.ui");
    }

    public void refresh(@Nonnull PlotCreatorSession session) {
        UICommandBuilder b = new UICommandBuilder();
        b.set("#ChecklistTitle.TextSpans", Message.translation(LANG + "checklist.title"));
        b.clear(ROWS);
        List<PlotCreatorChecklistModel.ChecklistItem> items = PlotCreatorChecklistModel.items(session.getDraft());
        for (int i = 0; i < items.size(); i++) {
            PlotCreatorChecklistModel.ChecklistItem item = items.get(i);
            b.append(ROWS, "Aetherhaven/PlotCreatorChecklistRow.ui");
            String row = ROWS + "[" + i + "]";
            b.set(row + " #CheckBox.Value", item.completed());
            b.set(row + " #CheckBox.Disabled", true);
            b.set(row + " #ItemLabel.TextSpans", formatLabel(item));
            b.set(row + " #ItemLabel.Style.TextColor", item.completed() ? "#a8d4b0" : "#dce4ec");
        }
        this.update(false, b);
    }

    @Nonnull
    private static Message formatLabel(@Nonnull PlotCreatorChecklistModel.ChecklistItem item) {
        Message name = Message.translation(item.labelLangKey());
        if (item.optional()) {
            return Message.translation(LANG + "checklist.labeledOptional").param("label", name);
        }
        if (item.countHint() != null) {
            String[] parts = item.countHint().split("/");
            if (parts.length == 2) {
                return Message.translation(LANG + "checklist.labeledCount")
                    .param("label", name)
                    .param("have", parts[0])
                    .param("need", parts[1]);
            }
        }
        return name;
    }
}
