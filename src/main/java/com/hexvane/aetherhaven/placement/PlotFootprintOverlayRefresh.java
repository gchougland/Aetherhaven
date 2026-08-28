package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.ui.CharterRelocationPage;
import com.hexvane.aetherhaven.ui.PlotPlacementPage;
import com.hexvane.aetherhaven.ui.PropPlacementPage;
import com.hexvane.aetherhaven.ui.WallPlacementPage;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * {@link com.hypixel.hytale.protocol.packets.player.ClearDebugShapes} clears every client debug overlay for the player.
 * After another system clears for its own preview, re-send plot/charter footprint cylinders if that UI is open.
 */
public final class PlotFootprintOverlayRefresh {
    private PlotFootprintOverlayRefresh() {}

    /**
     * From a ticking system: queue until the command buffer is consumed (after {@code processing} unlocks).
     * Placement pages may write hologram transforms; that is illegal while the store is processing.
     */
    public static void afterClearDebugShapes(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.run(store -> {
            if (!ref.isValid()) {
                return;
            }
            afterClearDebugShapes(ref, store);
        });
    }

    /** World-thread Store path. Do not call from a ticking system; use the {@link CommandBuffer} overload. */
    public static void afterClearDebugShapes(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        CustomUIPage page = player.getPageManager().getCustomPage();
        if (page instanceof PlotPlacementPage p) {
            p.refreshFootprintOverlayAfterDebugClear(ref, store);
        } else if (page instanceof PropPlacementPage propPage) {
            propPage.refreshFootprintOverlayAfterDebugClear(ref, store);
        } else if (page instanceof CharterRelocationPage c) {
            c.refreshFootprintOverlayAfterDebugClear(ref, store);
        } else if (page instanceof WallPlacementPage w) {
            w.refreshFootprintOverlayAfterDebugClear(ref, store);
        }
    }
}
