package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.ui.PlotPlacementPage;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotPlacementOpenHelper {
    private PlotPlacementOpenHelper() {}

    @Nullable
    public static PlotPlacementPage tryOpen(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor,
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionContext context
    ) {
        BlockPosition tb = context.getTargetBlock();
        Store<EntityStore> store = ref.getStore();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        PlotPlacementSession existing = PlotPlacementSessions.get(uc.getUuid());
        if (existing != null && existing.getWorld().getName().equals(world.getName())) {
            // Active preview: do not move anchor on block right-click; only Cancel clears the session so a new
            // right-click on a block can start placement elsewhere.
            return new PlotPlacementPage(playerRef, existing);
        }
        if (tb == null) {
            playerRef.sendMessage(Message.raw("Look at a block to set the plot sign position and start a preview."));
            return null;
        }
        Vector3i anchor = pickAnchor(world, tb);
        String cons = PlotPlacementPage.defaultConstructionFromInventory(store, ref);
        if (cons == null) {
            playerRef.sendMessage(
                Message.raw("Carry a plot token for a building type listed in the tool (e.g. inn plot token) to start placement.")
            );
            return null;
        }
        existing = new PlotPlacementSession(world, anchor, 0, cons);
        PlotPlacementSessions.put(uc.getUuid(), existing);
        return new PlotPlacementPage(playerRef, existing);
    }

    @Nonnull
    private static Vector3i pickAnchor(@Nonnull World world, @Nonnull BlockPosition tb) {
        Vector3i above = new Vector3i(tb.x, tb.y + 1, tb.z);
        if (isReplaceable(world, above.x, above.y, above.z)) {
            return above;
        }
        Vector3i on = new Vector3i(tb.x, tb.y, tb.z);
        if (isReplaceable(world, on.x, on.y, on.z)) {
            return on;
        }
        return above;
    }

    private static boolean isReplaceable(@Nonnull World world, int x, int y, int z) {
        BlockType t = world.getBlockType(x, y, z);
        return t == null || t.getMaterial() == BlockMaterial.Empty;
    }
}
