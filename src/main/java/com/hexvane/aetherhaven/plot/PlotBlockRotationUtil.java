package com.hexvane.aetherhaven.plot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;

/** Reads placed plot/charter block yaw from world block rotation (NESW variant). */
public final class PlotBlockRotationUtil {
    private PlotBlockRotationUtil() {}

    @Nonnull
    public static Rotation readBlockYaw(@Nonnull World world, @Nonnull Vector3i blockWorldPos) {
        int y = blockWorldPos.y;
        if (y < ChunkUtil.MIN_Y || y > ChunkUtil.HEIGHT_MINUS_1) {
            return Rotation.None;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef =
                chunkStore.getChunkSectionReferenceAtBlock(blockWorldPos.x, blockWorldPos.y, blockWorldPos.z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return Rotation.None;
        }
        Store<ChunkStore> store = sectionRef.getStore();
        BlockSection section = store.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) {
            return Rotation.None;
        }
        return section.getRotation(blockWorldPos.x, blockWorldPos.y, blockWorldPos.z).yaw();
    }

    /**
     * Packed rotation index for {@link com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk#setBlock} /
     * prefab placement, derived from the block already in the world.
     */
    public static int readBlockRotationIndex(@Nonnull World world, @Nonnull Vector3i blockWorldPos) {
        int y = blockWorldPos.y;
        if (y < ChunkUtil.MIN_Y || y > ChunkUtil.HEIGHT_MINUS_1) {
            return 0;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef =
            chunkStore.getChunkSectionReferenceAtBlock(blockWorldPos.x, blockWorldPos.y, blockWorldPos.z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return 0;
        }
        Store<ChunkStore> store = sectionRef.getStore();
        BlockSection section = store.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) {
            return 0;
        }
        RotationTuple current = section.getRotation(blockWorldPos.x, blockWorldPos.y, blockWorldPos.z);
        for (int i = 0; i < 256; i++) {
            RotationTuple t = RotationTuple.get(i);
            if (t.yaw() == current.yaw() && t.pitch() == current.pitch() && t.roll() == current.roll()) {
                return i;
            }
        }
        return 0;
    }
}
