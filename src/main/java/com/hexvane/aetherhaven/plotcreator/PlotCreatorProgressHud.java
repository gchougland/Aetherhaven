package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import javax.annotation.Nonnull;

/** Top-of-screen transparent step progress strip for the plot creator. */
public final class PlotCreatorProgressHud extends CustomUIHud {
    private static final String NODES = "#ProgressNodes";
    private static final String SUB_NODES = "#SubProgressNodes";
    private static final String TEX_EMPTY = "UI/Custom/NodeEmpty.png";
    private static final String TEX_FULL = "UI/Custom/NodeFull.png";

    public PlotCreatorProgressHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.PLOT_CREATOR_PROGRESS_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/PlotCreatorProgressHud.ui");
    }

    public void refresh(@Nonnull PlotCreatorSession session) {
        UICommandBuilder b = new UICommandBuilder();
        fillNodes(b, NODES, PlotCreatorProgressModel.nodes(session.getDraft()));
        List<PlotCreatorProgressModel.ProgressNode> sub = PlotCreatorProgressModel.substepNodes(session.getDraft());
        boolean showSub = !sub.isEmpty();
        b.set("#SubProgressPanel.Visible", showSub);
        b.clear(SUB_NODES);
        if (showSub) {
            fillNodes(b, SUB_NODES, sub);
        }
        this.update(false, b);
    }

    private static void fillNodes(
        @Nonnull UICommandBuilder b,
        @Nonnull String container,
        @Nonnull List<PlotCreatorProgressModel.ProgressNode> nodes
    ) {
        b.clear(container);
        int n = nodes.size();
        for (int i = 0; i < n; i++) {
            PlotCreatorProgressModel.ProgressNode node = nodes.get(i);
            b.append(container, "Aetherhaven/PlotCreatorProgressNode.ui");
            String row = container + "[" + i + "]";
            boolean current = node.current();
            boolean done = node.completed();
            String tex = (done || current) ? TEX_FULL : TEX_EMPTY;
            b.set(row + " #NodeIconNormal.Visible", !current);
            b.set(row + " #NodeIconCurrent.Visible", current);
            b.set(row + " #NodeIconNormal.AssetPath", tex);
            b.set(row + " #NodeIconCurrent.AssetPath", tex);
            b.set(row + " #NodeLabel.TextSpans", Message.translation(node.shortLangKey()));
            b.set(row + " #NodeLabel.Style.FontSize", current ? 13 : 11);
            b.set(row + " #NodeLabel.Style.RenderBold", current);
            b.set(row + " #NodeLabel.Style.TextColor", current ? "#f4e8c8" : (done ? "#b8e0c0" : "#9aa4b0"));
            applyLineCaps(b, row, i, n);
        }
    }

    /**
     * Line runs from the center of the first node to the center of the last. End cells only draw the
     * half toward the middle; middle cells draw a full-width segment. Nodes stay equidistant via FlexWeight.
     * Gold fill is defined in the .ui file — do not set Background from Java (strings are treated as texture paths).
     */
    private static void applyLineCaps(@Nonnull UICommandBuilder b, @Nonnull String row, int index, int count) {
        if (count <= 1) {
            b.set(row + " #LineRow.Visible", false);
            return;
        }
        b.set(row + " #LineRow.Visible", true);
        boolean first = index == 0;
        boolean last = index == count - 1;
        boolean middle = !first && !last;
        b.set(row + " #LineLeadSpacer.Visible", first);
        b.set(row + " #LineLeadGold.Visible", middle);
        b.set(row + " #LineBody.Visible", true);
        b.set(row + " #LineTrailGold.Visible", middle);
        b.set(row + " #LineTrailSpacer.Visible", last);
        b.set(row + " #LineSeam.Visible", !last);
    }
}
