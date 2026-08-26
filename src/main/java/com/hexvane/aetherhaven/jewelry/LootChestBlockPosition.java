package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** World block X/Z for a chunk-store block entity (loot chest). */
public final class LootChestBlockPosition {
    private LootChestBlockPosition() {}

    public record Coords(int blockX, int blockZ) {}

    @Nullable
    public static Coords resolve(@Nonnull Store<ChunkStore> store, @Nonnull BlockModule.BlockStateInfo bsi) {
        org.joml.Vector3i pos = new org.joml.Vector3i();
        if (!bsi.fillWorldPos(store, pos)) {
            return null;
        }
        return new Coords(pos.x, pos.z);
    }
}
