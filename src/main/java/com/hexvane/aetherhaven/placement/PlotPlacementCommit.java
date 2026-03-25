package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PlotPlacementCommit {
    private static final int PLACE_SETTINGS = 10;

    private PlotPlacementCommit() {}

    /**
     * Places {@link AetherhavenConstants#PLOT_SIGN_ITEM_ID} at {@code anchor} with NESW rotation from {@code prefabYaw}
     * and {@link PlotSignBlock} construction id.
     */
    public static boolean placePlotSign(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull Rotation prefabYaw,
        @Nonnull String constructionId,
        @Nonnull UUID plotId,
        @Nonnull Store<EntityStore> entityStore
    ) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }
        RotationTuple rt = RotationTuple.of(prefabYaw, Rotation.None, Rotation.None);
        boolean ok = chunk.placeBlock(x, y, z, AetherhavenConstants.PLOT_SIGN_ITEM_ID, rt.yaw(), rt.pitch(), rt.roll(), PLACE_SETTINGS);
        if (!ok) {
            return false;
        }
        Ref<ChunkStore> signRef = chunk.getBlockComponentEntity(x, y, z);
        if (signRef == null) {
            return false;
        }
        Store<ChunkStore> cs = signRef.getStore();
        cs.putComponent(signRef, PlotSignBlock.getComponentType(), new PlotSignBlock(constructionId, plotId.toString()));
        return true;
    }
}
