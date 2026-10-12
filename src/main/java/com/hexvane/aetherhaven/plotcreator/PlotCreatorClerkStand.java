package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.shop.ShopClerkStand;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Authoring of the one Clerk Stand a shop building may mark. */
public final class PlotCreatorClerkStand {
    private static final String LANG = "aetherhaven_plot_creator.aetherhaven.plotcreator.";

    private PlotCreatorClerkStand() {}

    /**
     * Only a player shop can be staffed by a townsfolk clerk, so only a building that is one, or counts as one,
     * is offered the marker. It is always optional.
     */
    public static boolean offered(@Nonnull PlotCreatorDraft draft, @Nullable AetherhavenPlugin plugin) {
        if (draft.isFestivalMode() || draft.isWallMode() || draft.isPropMode() || draft.isDecorationOnly()) {
            return false;
        }
        return PlotBuildingKindRequirements.requiresShopSafe(draft, plugin);
    }

    /** The clicked block is the floor; the clerk stands in the cell above it. */
    static boolean add(@Nonnull PlotCreatorSession session, @Nonnull Vector3i target, @Nonnull PlayerRef player) {
        PlotCreatorDraft draft = session.getDraft();
        Vector3i stand = new Vector3i(target).add(0, 1, 0);
        if (!draft.isInsideBounds(stand)
            || !VillagerBlockUtil.isNpcStandColumn(session.getWorld(), stand.x, stand.y, stand.z)) {
            player.sendMessage(Message.translation(LANG + "error.clerkStandBlocked"));
            return true;
        }
        boolean moved = place(draft, PlotCreatorLocalCoords.toLocal(draft, stand));
        player.sendMessage(Message.translation(LANG + (moved ? "hint.clerkStandMoved" : "hint.clerkStandMarked")));
        return true;
    }

    /**
     * Records the stand at this prefab-local cell. A shop has one clerk, so an earlier stand is replaced.
     *
     * @return true when an earlier stand was replaced
     */
    static boolean place(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        boolean moved = draft.getPois().removeIf(p -> p.getTags().contains(ShopClerkStand.TAG));
        PlotCreatorPoiDraft stand = new PlotCreatorPoiDraft();
        stand.setLocal(local[0], local[1], local[2]);
        stand.setTags(List.of(ShopClerkStand.TAG));
        draft.getPois().add(stand);
        return moved;
    }
}
